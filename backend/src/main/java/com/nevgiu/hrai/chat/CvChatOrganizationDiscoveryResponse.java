package com.nevgiu.hrai.chat;

import java.util.List;

/** Bounded tenant-wide discovery cards that still require explicit recruiter confirmation. */
public record CvChatOrganizationDiscoveryResponse(
        CvChatToolOrchestrationOutcome.Kind kind,
        String message,
        List<CandidateOption> candidates,
        boolean truncated
) {
    public record CandidateOption(Long id, String name, String currentTitle, List<String> evidenceReferences) { }
}
