package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;

/**
 * Converts authorized tool-search outcomes into stable application-owned discovery workflow decisions.
 *
 * <p>The earlier design asked the model whether candidate searches should run. Because a prompt is guidance rather
 * than an enforceable application rule, the provider could refuse a valid request before calling any tool. Production
 * discovery now extracts bounded candidate references first, invokes the authorized searches from Java, and gives
 * this class only the resulting counts. It does not search candidates itself; it owns the final
 * confirmation-versus-clarification transition.</p>
 *
 * <p>Security refusals are decided before reference extraction or tools run, so this decision cannot turn a sensitive
 * or explicit workflow-bypass request into a confirmation response.</p>
 */
final class CvChatToolDiscoveryDecision {
    private static final String CONFIRMATION_MESSAGE =
            "Candidate matches were found. Please confirm the candidate cards before continuing.";
    private static final String CLARIFICATION_MESSAGE =
            "Please clarify the candidate names before continuing.";

    private CvChatToolDiscoveryDecision() {
    }

    /**
     * Produces the discovery outcome directly from application-observed searches.
     *
     * <p>Production discovery uses this method after Java extracts bounded candidate references and invokes the
     * tenant-authorized search tool for each reference. A model is not asked whether those searches should happen.</p>
     */
    static CvChatToolOrchestrationOutcome decide(CvChatToolDiscoveryState.Snapshot searches) {
        if (searches.emptySearches() > 0 || !searches.hasCandidateMatches()) {
            return new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED, CLARIFICATION_MESSAGE);
        }
        return new CvChatToolOrchestrationOutcome(
                CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED, CONFIRMATION_MESSAGE);
    }

}
