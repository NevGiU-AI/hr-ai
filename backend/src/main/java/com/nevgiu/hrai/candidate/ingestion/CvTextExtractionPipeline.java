package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.ocr.CvOcrEngine;
import com.nevgiu.hrai.candidate.ocr.CvOcrException;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Selects the least expensive trustworthy extraction path.
 *
 * <p>Embedded PDF text is attempted first. OCR is a fallback only when that text is below the
 * configured minimum, preventing unnecessary image rendering and native-process work for normal
 * PDFs. OCR infrastructure failure is represented in the result so callers can retain the
 * governed original and route it to review.</p>
 */
@Service
public class CvTextExtractionPipeline {
    private final CvTextExtractor nativeExtractor;
    private final CvOcrEngine ocrEngine;
    private final CvIngestionProperties ingestionProperties;
    private final CvOcrProperties ocrProperties;

    public CvTextExtractionPipeline(CvTextExtractor nativeExtractor, CvOcrEngine ocrEngine,
                                    CvIngestionProperties ingestionProperties, CvOcrProperties ocrProperties) {
        this.nativeExtractor = nativeExtractor;
        this.ocrEngine = ocrEngine;
        this.ingestionProperties = ingestionProperties;
        this.ocrProperties = ocrProperties;
    }

    /**
     * Extracts text while keeping a malformed PDF distinct from unavailable OCR infrastructure.
     *
     * @throws IOException when native PDF parsing fails and no meaningful extraction can begin
     */
    public CvTextExtractionResult extract(byte[] content) throws IOException {
        // Native extraction is deterministic, local, and much cheaper than rendering every page.
        String nativeText = nativeExtractor.extract(content);
        // Disabling OCR never prevents an otherwise valid upload. Insufficient native text is
        // returned and the importing service marks the document for manual review.
        if (nativeText.length() >= ingestionProperties.minimumTextLength() || !ocrProperties.enabled()) {
            return CvTextExtractionResult.nativeText(nativeText);
        }
        try {
            // Once OCR is selected, its output—not the inadequate native text—becomes the current
            // extraction and carries page evidence plus a measurable confidence score.
            CvOcrResult ocr = ocrEngine.extract(content);
            return new CvTextExtractionResult(
                    ocr.text(), CvExtractionMethod.OCR, ocr.confidence(), ocr.pages(), false);
        } catch (CvOcrException exception) {
            // Preserve the governed original and route it to review instead of losing the import
            // when Tesseract is missing, times out, or returns unusable output.
            return new CvTextExtractionResult(nativeText, CvExtractionMethod.NATIVE, null, java.util.List.of(), true);
        }
    }
}
