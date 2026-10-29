package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Startup-validated limits for the personal-data-bearing evidence sent to the CV chat model.
 *
 * <p>{@link ConfigurationProperties} maps {@code app.cv-chat.*} values from application configuration onto this
 * immutable record. {@link Validated} activates the Bean Validation constraints below during application startup, so
 * an unsafe or unusable deployment value stops the backend instead of being accepted silently.</p>
 *
 * <p>The limits bound data disclosure, prompt size, model cost, latency, and memory use. They are defensive limits,
 * not authorization controls: tenant authorization is completed before evidence is constructed.</p>
 */
@Validated
@ConfigurationProperties("app.cv-chat")
public record CvChatProperties(
        // At least one page is needed for a cited answer; 30 prevents a request from exporting an unusually long CV.
        @Min(1) @Max(30) int maxEvidencePages,

        // Excerpts below 200 characters are rarely useful; 4,000 caps personal data and tokens sent per evidence item.
        @Min(200) @Max(4000) int maxExcerptCharacters,

        // Retrieval has its own rollout switch so indexing can remain healthy while chat behavior is validated.
        boolean semanticRetrievalEnabled,

        // Only a small number of query-relevant chunks may be disclosed to the model for one question.
        @Min(1) @Max(20) int semanticMaxMatches,

        // Low-scoring neighbors are rejected before model invocation instead of being presented as relevant evidence.
        @DecimalMin("0.0") @DecimalMax("1.0") double semanticMinSimilarity
) { }
