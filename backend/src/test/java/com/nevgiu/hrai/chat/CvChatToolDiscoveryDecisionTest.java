package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolDiscoveryDecisionTest {

    @Test
    void deterministicDiscoveryConfirmsAmbiguousAndUniqueSuccessfulReferences() {
        CvChatToolDiscoveryState.Snapshot fanonAndYogish = new CvChatToolDiscoveryState.Snapshot(2, 2, 0);

        assertThat(CvChatToolDiscoveryDecision.decide(fanonAndYogish).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED);
    }

    @Test
    void deterministicDiscoveryClarifiesMissingOrIncompleteReferences() {
        assertThat(CvChatToolDiscoveryDecision.decide(
                new CvChatToolDiscoveryState.Snapshot(2, 1, 1)).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED);
        assertThat(CvChatToolDiscoveryDecision.decide(
                new CvChatToolDiscoveryState.Snapshot(0, 0, 0)).kind())
                .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED);
    }

}
