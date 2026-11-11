package com.nevgiu.hrai.chat.memory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Operational bounds for durable, owner-scoped CV-chat conversations. */
@Validated
@ConfigurationProperties("app.cv-chat.conversation")
public record CvChatConversationProperties(
        @NotNull Duration ttl,
        @Min(2) @Max(50) int maxMemoryMessages,
        @Min(1) @Max(1000) int cleanupBatchSize) {
}
