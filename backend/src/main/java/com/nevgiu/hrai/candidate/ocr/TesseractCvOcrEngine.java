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

/** Local OCR adapter. CV bytes never leave the backend host. */
@Component
public class TesseractCvOcrEngine implements CvOcrEngine {
    private final CvOcrProperties properties;

    public TesseractCvOcrEngine(CvOcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public CvOcrResult extract(byte[] pdf) {
        if (!properties.enabled()) {
            throw new CvOcrException("OCR is disabled");
        }
        Path workDirectory = null;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageCount = Math.min(document.getNumberOfPages(), properties.maxPages());
            if (pageCount == 0) {
                return new CvOcrResult("", 0, List.of());
            }
            workDirectory = Files.createTempDirectory("cv-ocr-");
            PDFRenderer renderer = new PDFRenderer(document);
            List<CvOcrResult.Page> pages = new ArrayList<>();
            for (int index = 0; index < pageCount; index++) {
                Path image = workDirectory.resolve("page-" + (index + 1) + ".png");
                Path outputBase = workDirectory.resolve("result-" + (index + 1));
                ImageIO.write(renderer.renderImageWithDPI(index, properties.dpi(), ImageType.GRAY), "png", image.toFile());
                runTesseract(image, outputBase);
                pages.add(parseTsv(index + 1, Path.of(outputBase + ".tsv")));
            }
            String text = pages.stream().map(CvOcrResult.Page::text)
                    .filter(value -> !value.isBlank()).reduce((left, right) -> left + "\n\n" + right).orElse("");
            double confidence = pages.stream().filter(page -> !page.text().isBlank())
                    .mapToDouble(CvOcrResult.Page::confidence).average().orElse(0);
            return new CvOcrResult(text.trim(), confidence, List.copyOf(pages));
        } catch (IOException exception) {
            throw new CvOcrException("OCR processing failed", exception);
        } finally {
            deleteWorkDirectory(workDirectory);
        }
    }

    private void runTesseract(Path image, Path outputBase) {
        Process process;
        try {
            process = new ProcessBuilder(properties.command(), image.toString(), outputBase.toString(),
                    "-l", properties.languages(), "tsv").redirectErrorStream(true).start();
            boolean completed = process.waitFor(properties.pageTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new CvOcrException("OCR page processing timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new CvOcrException("OCR process failed with exit code " + process.exitValue()
                        + (output.isBlank() ? "" : ": " + output.strip()));
            }
        } catch (IOException exception) {
            throw new CvOcrException("OCR executable is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CvOcrException("OCR processing was interrupted", exception);
        }
    }

    private CvOcrResult.Page parseTsv(int pageNumber, Path tsv) throws IOException {
        StringBuilder text = new StringBuilder();
        double confidenceTotal = 0;
        int confidenceCount = 0;
        for (String line : Files.readAllLines(tsv, StandardCharsets.UTF_8)) {
            String[] columns = line.split("\\t", 12);
            if (columns.length < 12 || !"5".equals(columns[0]) || columns[11].isBlank()) {
                continue;
            }
            if (!text.isEmpty()) text.append(' ');
            text.append(columns[11].trim());
            try {
                double confidence = Double.parseDouble(columns[10]);
                if (confidence >= 0) {
                    confidenceTotal += confidence;
                    confidenceCount++;
                }
            } catch (NumberFormatException ignored) {
                // A malformed confidence value does not discard otherwise usable OCR text.
            }
        }
        return new CvOcrResult.Page(pageNumber, text.toString().trim(),
                confidenceCount == 0 ? 0 : confidenceTotal / confidenceCount);
    }

    private void deleteWorkDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
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
