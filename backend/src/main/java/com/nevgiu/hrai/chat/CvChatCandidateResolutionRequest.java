package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A human-supplied exact candidate name; organization context always comes from the authenticated session. */
public record CvChatCandidateResolutionRequest(
        @NotBlank @Size(max = 255) String name
) { }
