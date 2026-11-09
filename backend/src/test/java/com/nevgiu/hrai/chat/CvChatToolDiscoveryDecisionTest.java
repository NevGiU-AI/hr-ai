package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolDiscoveryDecisionTest {

    @Test
    void authorizedSearchFactsOverrideProviderRefusalOrClarification() {
        CvChatToolDiscoveryState.Snapshot matches = new CvChatToolDiscoveryState.Snapshot(2, 2, 0);

        assertThat(stabilize(CvChatToolOrchestrationOutcome.Kind.REFUSED, matches).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED);
        assertThat(stabilize(CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED, matches).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED);
    }

    @Test
    void emptySearchRequiresClarificationAndAbsentSearchKeepsProviderOutcome() {
        CvChatToolDiscoveryState.Snapshot missing = new CvChatToolDiscoveryState.Snapshot(2, 1, 1);
        assertThat(stabilize(CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED, missing).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED);

        CvChatToolDiscoveryState.Snapshot absent = new CvChatToolDiscoveryState.Snapshot(0, 0, 0);
        assertThat(stabilize(CvChatToolOrchestrationOutcome.Kind.REFUSED, absent).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.REFUSED);
    }

    @Test
    void confirmedScopeIsNeverRewrittenFromDiscoverySearchState() {
        CvChatToolOrchestrationOutcome ready = new CvChatToolOrchestrationOutcome(
                CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY, "Ready");

        assertThat(CvChatToolDiscoveryDecision.stabilize(CvChatToolOrchestrationMode.CONFIRMED_SCOPE, ready,
                new CvChatToolDiscoveryState.Snapshot(2, 2, 0))).isSameAs(ready);
    }

    private CvChatToolOrchestrationOutcome stabilize(CvChatToolOrchestrationOutcome.Kind kind,
                                                      CvChatToolDiscoveryState.Snapshot snapshot) {
        return CvChatToolDiscoveryDecision.stabilize(CvChatToolOrchestrationMode.DISCOVERY,
                new CvChatToolOrchestrationOutcome(kind, "Provider message"), snapshot);
    }
}
