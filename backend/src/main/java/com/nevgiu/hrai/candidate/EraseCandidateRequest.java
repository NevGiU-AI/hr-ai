package com.nevgiu.hrai.candidate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record EraseCandidateRequest(
        @NotNull(message = "Erasure confirmation is required")
        @AssertTrue(message = "Erasure must be explicitly confirmed")
        Boolean confirmed
) {}
