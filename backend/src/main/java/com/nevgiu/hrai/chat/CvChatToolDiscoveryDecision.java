package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;

/**
 * Converts authorized tool-search outcomes into stable application-owned discovery workflow decisions.
 *
 * <p>The system prompt tells the model how discovery should work, but a prompt is guidance rather than an enforceable
 * application rule. A provider may still return {@code REFUSED} or {@code CLARIFICATION_REQUIRED} after its own tool
 * calls found valid candidates. This class therefore checks the facts observed by the application-owned tools after
 * the model call completes. It does not search candidates itself and it does not replace the model's natural-language
 * interpretation; it only makes the final confirmation-versus-clarification transition deterministic.</p>
 *
 * <p>Security refusals are decided before tools run, so this postcondition cannot turn a sensitive or explicit
 * workflow-bypass request into a confirmation response.</p>
 */
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
        // Confirmed-scope planning has different rules. If the model did not call the discovery search tool, there
        // are no application-observed search facts with which to correct its proposed outcome.
        if (mode != CvChatToolOrchestrationMode.DISCOVERY || searches.searchCalls() == 0) {
            return providerOutcome;
        }
        // An empty authorized search means at least one model-selected reference needs recruiter clarification.
        if (searches.emptySearches() > 0) {
            return new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED, CLARIFICATION_MESSAGE);
        }
        // Two distinct successful searches prove that bounded tenant-authorized cards exist for at least two search
        // references. The provider may propose wording, but it cannot turn those application facts into a refusal or
        // another clarification loop. Angular will still require the recruiter to select and confirm the exact cards.
        if (searches.hasConfirmedMultiCandidateMatches()) {
            return new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED, CONFIRMATION_MESSAGE);
        }
        // A single successful search is not enough to prove that a comparison request resolved every person. Preserve
        // the provider result instead of inventing confirmation from incomplete tool evidence.
        return providerOutcome;
    }
}
