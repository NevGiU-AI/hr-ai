package com.nevgiu.hrai.candidate.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local OCR adapter backed by the Tesseract command-line program.
 *
 * <p>The engine renders each PDF page to a temporary grayscale PNG, invokes Tesseract for that
 * page, and reads Tesseract's TSV output. The TSV format is used instead of plain text because it
 * includes word-level confidence values. CV bytes and rendered pages remain on the backend host,
 * and the temporary working directory is removed after both successful and failed attempts.</p>
 */
@Component
public class TesseractCvOcrEngine implements CvOcrEngine {
    // One immutable configuration object is shared by all calls to this singleton bean.
    // Request-specific files and results remain local variables, so concurrent calls do not share
    // mutable OCR state.
    private final CvOcrProperties properties;

    /** Constructor injection keeps deployment-specific OCR settings explicit and testable. */
    public TesseractCvOcrEngine(CvOcrProperties properties) {
        this.properties = properties;
    }

    /**
     * Extracts page-level and document-level OCR data from an in-memory PDF.
     *
     * @param pdf original PDF bytes that have already passed upload and malware validation
     * @return immutable OCR text, confidence, and page provenance
     * @throws CvOcrException when OCR is disabled or local processing cannot complete
     */
    @Override
    public CvOcrResult extract(byte[] pdf) {
        // Keep OCR opt-in so an environment without Tesseract cannot invoke an external process
        // accidentally. The extraction pipeline decides whether this adapter should be called.
        if (!properties.enabled()) {
            throw new CvOcrException("OCR is disabled");
        }
        // Declared outside the try block so finally can clean it when rendering, execution, or
        // parsing fails partway through a multi-page document.
        Path workDirectory = null;
        // try-with-resources closes native PDFBox resources even if one page fails. The byte array
        // is read directly; no copy of the original PDF is written to the temporary directory.
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // Limit CPU, memory, temporary-disk use, and the number of child processes that an
            // unusually large or hostile document can cause.
            int pageCount = Math.min(document.getNumberOfPages(), properties.maxPages());
            if (pageCount == 0) {
                // Represent an empty PDF as a valid empty result; the pipeline's quality rules will
                // decide that it requires review rather than manufacturing an OCR infrastructure error.
                return new CvOcrResult("", 0, List.of());
            }
            // A directory per request prevents concurrent imports from overwriting each other's
            // rendered pages or Tesseract output files.
            workDirectory = Files.createTempDirectory("cv-ocr-");
            PDFRenderer renderer = new PDFRenderer(document);
            List<CvOcrResult.Page> pages = new ArrayList<>();
            for (int index = 0; index < pageCount; index++) {
                // Human-facing page numbers start at one, while PDFBox indexes pages from zero.
                Path image = workDirectory.resolve("page-" + (index + 1) + ".png");
                Path outputBase = workDirectory.resolve("result-" + (index + 1));
                // Grayscale retains text contrast without the memory and disk cost of RGB. DPI
                // means dots per inch: a higher value gives Tesseract more pixels for small text,
                // but doubling DPI roughly quadruples the image pixels and processing cost. The
                // configured 300-DPI default is a practical accuracy/cost balance for typical CVs.
                ImageIO.write(renderer.renderImageWithDPI(index, properties.dpi(), ImageType.GRAY), "png", image.toFile());
                runTesseract(image, outputBase);
                // Tesseract appends .tsv to this base path. A separate result per page preserves
                // provenance for review and future evidence citations.
                pages.add(parseTsv(index + 1, Path.of(outputBase + ".tsv")));
            }
            // Keep page boundaries instead of joining the last word of one page directly to the
            // first word of the next page.
            String text = pages.stream().map(CvOcrResult.Page::text)
                    .filter(value -> !value.isBlank()).reduce((left, right) -> left + "\n\n" + right).orElse("");
            // Empty pages do not lower the mean. A document with no recognized text gets zero
            // confidence and is routed to human review by the calling service.
            double confidence = pages.stream().filter(page -> !page.text().isBlank())
                    .mapToDouble(CvOcrResult.Page::confidence).average().orElse(0);
            // Copying prevents callers from observing later mutation of the working ArrayList.
            return new CvOcrResult(text.trim(), confidence, List.copyOf(pages));
        } catch (IOException exception) {
            // Normalize PDF loading, image rendering, TSV reading, and temporary-file failures into
            // the OCR-specific exception understood by the extraction pipeline.
            throw new CvOcrException("OCR processing failed", exception);
        } finally {
            // Cleanup belongs in finally because a partially processed CV can leave both rendered
            // images and TSV output behind. Those files contain personal data.
            deleteWorkDirectory(workDirectory);
        }
    }

    /** Runs one bounded Tesseract child process and reports infrastructure failures consistently. */
    private void runTesseract(Path image, Path outputBase) {
        Process process;
        try {
            // Merge stderr into stdout to avoid managing two process pipes and to retain one useful
            // diagnostic stream when the external command fails.
            process = new ProcessBuilder(properties.command(), image.toString(), outputBase.toString(),
                    "-l", properties.languages(), "tsv").redirectErrorStream(true).start();
            // A per-page timeout prevents malformed or expensive input from occupying a request
            // thread and a Tesseract process indefinitely.
            boolean completed = process.waitFor(properties.pageTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                // Forced termination is appropriate here: a timed-out OCR result is unusable, and
                // leaving the native process alive would consume resources after the request ends.
                process.destroyForcibly();
                throw new CvOcrException("OCR page processing timed out");
            }
            // Recognition data is written to the TSV file; this stream contains diagnostics. It is
            // included in the exception only for a non-zero exit status.
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new CvOcrException("OCR process failed with exit code " + process.exitValue()
                        + (output.isBlank() ? "" : ": " + output.strip()));
            }
        } catch (IOException exception) {
            // This normally means the configured executable is missing or cannot be started.
            throw new CvOcrException("OCR executable is unavailable", exception);
        } catch (InterruptedException exception) {
            // Preserve the interruption so request or shutdown infrastructure can still observe it.
            Thread.currentThread().interrupt();
            throw new CvOcrException("OCR processing was interrupted", exception);
        }
    }

    /** Converts Tesseract word rows into page text and a mean confidence percentage. */
    private CvOcrResult.Page parseTsv(int pageNumber, Path tsv) throws IOException {
        // Column 11 contains the recognized token; column 10 contains its confidence in the
        // conventional Tesseract 0-100 range.
        StringBuilder text = new StringBuilder();
        double confidenceTotal = 0;
        int confidenceCount = 0;
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            // The TSV has twelve columns. Level 5 is an individual word; page, block, paragraph,
            // and line rows are ignored because including them would duplicate recognized text.
            String[] columns = line.split("\\t", 12);
            if (columns.length < 12 || !"5".equals(columns[0]) || columns[11].isBlank()) {
                continue;
            }
            // Positional metadata is available in the TSV, but this first OCR slice intentionally
            // keeps a simple reading-order transcript and retains page provenance separately.
            if (!text.isEmpty()) text.append(' ');
            text.append(columns[11].trim());
            try {
                double confidence = Double.parseDouble(columns[10]);
                // Tesseract uses negative values for rows without a usable confidence score.
                if (confidence >= 0) {
                    confidenceTotal += confidence;
                    confidenceCount++;
                }
            } catch (NumberFormatException ignored) {
                // A malformed confidence value does not discard otherwise usable OCR text.
            }
        }
        // Confidence is an unweighted mean of recognized words. It is a review signal, not a
        // statistical guarantee that every extracted field is correct.
        return new CvOcrResult.Page(pageNumber, text.toString().trim(),
                confidenceCount == 0 ? 0 : confidenceTotal / confidenceCount);
    }

    /** Best-effort removal of rendered pages and OCR output without hiding the primary outcome. */
    private void deleteWorkDirectory(Path directory) {
        // No directory exists when PDF loading fails or the document has zero pages.
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            // Children must be removed before their parent directory, hence reverse path order.
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary-file cleanup must not replace the primary OCR result or failure.
                }
            });
        } catch (IOException ignored) {
            // The operating system may finish cleaning its temporary directory later.
        }
    }
}
