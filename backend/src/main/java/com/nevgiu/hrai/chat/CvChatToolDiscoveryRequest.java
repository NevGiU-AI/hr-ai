package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bounded recruiter text for read-tool discovery; trusted tenant and role context never enters this payload. */
public record CvChatToolDiscoveryRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2_000, message = "Message must not exceed 2000 characters")
        String message
) { }
