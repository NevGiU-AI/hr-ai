package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded evidence limits prevent one chat request from exporting an entire CV corpus to the model. */
@Validated
@ConfigurationProperties("app.cv-chat")
public record CvChatProperties(
        @Min(1) @Max(30) int maxEvidencePages,
        @Min(200) @Max(4000) int maxExcerptCharacters
) { }
