package com.nevgiu.hrai.candidate.semantic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounds semantic indexing cost and the amount of personal information sent to the embedding provider. */
@Validated
@ConfigurationProperties("app.cv-semantic-index")
public record CvSemanticIndexProperties(
        // Feature flag permits Flyway/schema deployment before any CV text is sent to the embedding provider.
        boolean enabled,
        // Character bounds are deterministic approximations; the provider still performs its own token accounting.
        @Min(400) @Max(4000) int chunkCharacters,
        @Min(0) @Max(1000) int overlapCharacters,
        // Caps both personal-data disclosure and embedding cost for one document.
        @Min(1) @Max(200) int maxChunksPerDocument,
        // Shared with Spring AI PgVectorStore's maximum document batch size through application.yml.
        @Min(1) @Max(100) int embeddingBatchSize,
        // Limits how many missing/stale documents one scheduled run can enqueue.
        @Min(1) @Max(500) int reconciliationBatchSize,
        // Kept in the typed configuration for operational visibility; @Scheduled resolves the same property directly.
        String reconciliationCron
) {
    public CvSemanticIndexProperties {
        if (overlapCharacters >= chunkCharacters) {
            throw new IllegalArgumentException("CV semantic chunk overlap must be smaller than the chunk size");
        }
    }
}
