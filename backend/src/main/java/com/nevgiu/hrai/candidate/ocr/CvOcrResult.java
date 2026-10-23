package com.nevgiu.hrai.candidate.ocr;

import java.util.List;

/**
 * Provider-neutral OCR output. Confidence values use the OCR provider's percentage-like 0-100
 * scale and are quality-review signals rather than guarantees of correctness.
 */
public record CvOcrResult(String text, double confidence, List<Page> pages) {
    /** One-based page provenance used for review and future source citations. */
    public record Page(int pageNumber, String text, double confidence) { }
}
