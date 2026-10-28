package com.nevgiu.hrai.candidate.semantic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounds semantic indexing cost and the amount of personal information sent to the embedding provider. */
@Validated
@ConfigurationProperties("app.cv-semantic-index")
public record CvSemanticIndexProperties(
        boolean enabled,
        @Min(400) @Max(4000) int chunkCharacters,
        @Min(0) @Max(1000) int overlapCharacters,
        @Min(1) @Max(200) int maxChunksPerDocument,
        @Min(1) @Max(100) int embeddingBatchSize,
        @Min(1) @Max(500) int reconciliationBatchSize,
        String reconciliationCron
) {
    public CvSemanticIndexProperties {
        if (overlapCharacters >= chunkCharacters) {
            throw new IllegalArgumentException("CV semantic chunk overlap must be smaller than the chunk size");
        }
    }
}
