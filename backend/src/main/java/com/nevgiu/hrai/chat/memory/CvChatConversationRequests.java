package com.nevgiu.hrai.chat.memory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

final class CvChatConversationRequests {
    private CvChatConversationRequests() {
    }

    record Create(
            @NotEmpty @Size(max = 5) List<@NotNull @Positive Long> candidateIds,
            @Positive Long jobId) {
    }

    /** Version makes a full-scope replacement fail safely when two browser tabs update the same conversation. */
    record ReplaceScope(
            @Min(0) long version,
            @NotEmpty @Size(max = 5) List<@NotNull @Positive Long> candidateIds,
            @Positive Long jobId) {
    }
}
