package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;

import java.util.List;

/**
 * Internal result passed from extraction into transactional import or reprocessing.
 *
 * @param text document-level text selected by the pipeline
 * @param method mechanism that produced {@code text}
 * @param confidence OCR confidence, or {@code null} when native extraction was used
 * @param pages page-level OCR provenance; empty for native extraction and failed OCR attempts
 * @param ocrAttemptFailed whether OCR infrastructure failed after native text proved insufficient
 */
public record CvTextExtractionResult(
        String text,
        CvExtractionMethod method,
        Double confidence,
        List<CvOcrResult.Page> pages,
        boolean ocrAttemptFailed
) {
    /** Builds the deliberately confidence-free result used for embedded PDF text. */
    public static CvTextExtractionResult nativeText(String text) {
        return new CvTextExtractionResult(text, CvExtractionMethod.NATIVE, null, List.of(), false);
    }
}
