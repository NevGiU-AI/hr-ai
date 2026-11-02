package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Explicit candidate IDs and one bounded recruiter comparison question; tenant context comes from the session. */
public record CvChatComparisonRequest(
        @NotNull(message = "Candidate IDs are required")
        @Size(min = 2, max = 5, message = "Select between 2 and 5 candidates")
        List<@NotNull @Positive Long> candidateIds,
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message
) { }
