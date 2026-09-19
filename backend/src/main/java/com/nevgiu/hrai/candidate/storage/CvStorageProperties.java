package com.nevgiu.hrai.candidate.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cv-storage")
public record CvStorageProperties(String root, Duration retention) {
}
