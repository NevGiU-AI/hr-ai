package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Explicit candidate IDs, one bounded recruiter question, and an optional approved job; tenant context comes from
 * the authenticated session and the backend re-authorizes every supplied ID.
 */
public record CvChatComparisonRequest(
        @NotNull(message = "Candidate IDs are required")
        @Size(min = 2, max = 5, message = "Select between 2 and 5 candidates")
        List<@NotNull @Positive Long> candidateIds,
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,
        @Positive(message = "Approved job ID must be positive")
        Long jobId
) { }
