package com.nevgiu.hrai.chat.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolDiscoveryStateTest {

    @Test
    void exposesCountsWithoutQueriesOrCandidateData() {
        CvChatToolDiscoveryState state = new CvChatToolDiscoveryState();
        state.record("Ada Lovelace", 1);
        state.record("ada lovelace", 2);
        state.record("Missing Person", 0);

        assertThat(state.snapshot()).isEqualTo(new CvChatToolDiscoveryState.Snapshot(3, 1, 1));
        assertThat(CvChatToolDiscoveryState.Snapshot.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("searchCalls", "distinctSuccessfulSearches", "emptySearches");
    }

    @Test
    void treatsSeveralCardsForOneAmbiguousNameAsOneSuccessfulReference() {
        CvChatToolDiscoveryState state = new CvChatToolDiscoveryState();

        state.record("Fanon Jupkwo", 4);
        state.record("Yogish", 1);

        assertThat(state.snapshot()).isEqualTo(new CvChatToolDiscoveryState.Snapshot(2, 2, 0));
        assertThat(state.snapshot().hasConfirmedMultiCandidateMatches()).isTrue();
    }
}
