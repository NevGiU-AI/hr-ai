package com.nevgiu.hrai.candidate;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cv-retention")
public record CvRetentionProperties(boolean enabled, int batchSize) {
    public CvRetentionProperties {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("CV retention batch size must be between 1 and 1000");
        }
    }
}
