package com.nevgiu.hrai.candidate.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
