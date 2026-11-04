package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Explicit human-confirmed IDs plus bounded planning text. Organization, roles, and authorization decisions are
 * intentionally absent and are resolved from trusted server-side context.
 */
public record CvChatToolConfirmedScopeRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2_000, message = "Message must not exceed 2000 characters")
        String message,
        @NotNull(message = "Candidate IDs are required")
        @Size(min = 1, max = 5, message = "Confirm between 1 and 5 candidates")
        List<@NotNull @Positive Long> candidateIds,
        @Positive(message = "Approved job ID must be positive")
        Long jobId
) { }
