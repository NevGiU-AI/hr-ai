package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Partial human-supplied candidate name; organization context always comes from the authenticated session. */
public record CvChatCandidateSearchRequest(
        @NotBlank @Size(min = 2, max = 255) String query
) { }
