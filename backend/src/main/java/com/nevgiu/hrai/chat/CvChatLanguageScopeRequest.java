package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Natural recruiter message interpreted only to propose a candidate scope for explicit confirmation. */
public record CvChatLanguageScopeRequest(
        @NotBlank @Size(max = 2000) String message
) { }
