package com.nevgiu.hrai.candidate.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the local OCR process.
 *
 * @param enabled explicit deployment switch for OCR fallback
 * @param command executable name or absolute path used to start Tesseract
 * @param languages Tesseract language expression such as {@code eng+fra+por}; corresponding
 *                  trained-data packages must be installed in the runtime image
 * @param dpi image resolution used when rendering PDF pages; 300 DPI is a practical default for
 *            small CV text, while doubling DPI roughly quadruples the pixels, memory, and work
 * @param maxPages upper bound on pages rendered and submitted to external OCR for one document
 * @param pageTimeout maximum time allowed for Tesseract to process one rendered page
 * @param minimumConfidence document-level threshold below which OCR output requires human review
 */
@ConfigurationProperties(prefix = "app.cv-ocr")
public record CvOcrProperties(
        boolean enabled,
        String command,
        String languages,
        int dpi,
        int maxPages,
        Duration pageTimeout,
        double minimumConfidence
) { }
