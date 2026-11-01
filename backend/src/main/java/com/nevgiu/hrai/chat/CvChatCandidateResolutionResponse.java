package com.nevgiu.hrai.chat;

import java.util.List;

/**
 * Minimal tenant-scoped candidate identities that a UI can turn into an explicit selection.
 *
 * <p>Email, CV text, organization ID, and other personal data are intentionally absent. A single result is resolved;
 * multiple exact-name results require the user to choose one rather than letting the model guess.</p>
 */
public record CvChatCandidateResolutionResponse(Status status, List<CandidateOption> candidates, boolean truncated) {
    public enum Status {
        RESOLVED,
        AMBIGUOUS
    }

    public record CandidateOption(Long id, String name, String currentTitle) { }
}
