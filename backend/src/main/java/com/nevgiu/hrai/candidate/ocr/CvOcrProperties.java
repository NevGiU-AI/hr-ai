package com.nevgiu.hrai.candidate.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the local OCR process.
 *
 * @param dpi image resolution used when rendering PDF pages; 300 DPI is a practical default for
 *            small CV text, while doubling DPI roughly quadruples the pixels, memory, and work
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
