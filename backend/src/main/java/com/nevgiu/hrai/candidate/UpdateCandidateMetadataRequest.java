package com.nevgiu.hrai.candidate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCandidateMetadataRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 320) String email,
        @Size(max = 255) String location,
        @Size(max = 255) String currentTitle,
        @PositiveOrZero @Max(100) Integer yearsExperience
) { }
