package com.nevgiu.hrai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Professional tenant-wide search intent; organization scope always comes from the authenticated session. */
public record CvChatOrganizationDiscoveryRequest(
        @NotBlank @Size(min = 2, max = 2_000) String message
) { }
