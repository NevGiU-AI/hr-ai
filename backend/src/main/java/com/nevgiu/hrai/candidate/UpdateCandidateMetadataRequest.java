package com.nevgiu.hrai.candidate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Complete editable candidate profile submitted by an administrator or recruiter; omitted optional fields are cleared. */
public record UpdateCandidateMetadataRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 320) String email,
        @Size(max = 255) String location,
        @Size(max = 255) String currentTitle,
        // The upper bound rejects accidental birth years or other implausible values entered in this field.
        @PositiveOrZero @Max(100) Integer yearsExperience
) { }
