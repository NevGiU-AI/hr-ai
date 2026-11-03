package com.nevgiu.hrai.chat;

import java.util.List;

/** Minimal tenant-scoped choices returned before any CV or evaluation evidence is retrieved. */
public record CvChatLanguageScopeResponse(String message, List<Reference> references) {
    public enum Status {
        RESOLVED,
        CHOICES,
        NOT_FOUND
    }

    public record Reference(String requestedName, Status status, List<CandidateOption> candidates,
                            boolean truncated) { }

    public record CandidateOption(Long id, String name, String currentTitle) { }
}
