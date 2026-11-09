package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;

/** Converts authorized tool-search outcomes into stable application-owned discovery workflow decisions. */
final class CvChatToolDiscoveryDecision {
    private static final String CONFIRMATION_MESSAGE =
            "Candidate matches were found. Please confirm the candidate cards before continuing.";
    private static final String CLARIFICATION_MESSAGE =
            "Please clarify the candidate names before continuing.";

    private CvChatToolDiscoveryDecision() {
    }

    static CvChatToolOrchestrationOutcome stabilize(CvChatToolOrchestrationMode mode,
                                                     CvChatToolOrchestrationOutcome providerOutcome,
                                                     CvChatToolDiscoveryState.Snapshot searches) {
        if (mode != CvChatToolOrchestrationMode.DISCOVERY || searches.searchCalls() == 0) {
            return providerOutcome;
        }
        // An empty authorized search means at least one model-selected reference needs recruiter clarification.
        if (searches.emptySearches() > 0) {
            return new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED, CLARIFICATION_MESSAGE);
        }
        // Two distinct successful searches prove that bounded tenant-authorized cards exist. The provider may propose
        // wording, but it cannot turn those application facts into a refusal or another clarification loop.
        if (searches.hasConfirmedMultiCandidateMatches()) {
            return new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED, CONFIRMATION_MESSAGE);
        }
        return providerOutcome;
    }
}
