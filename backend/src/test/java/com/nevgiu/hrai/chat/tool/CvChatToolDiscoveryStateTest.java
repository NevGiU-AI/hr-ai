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
        assertThat(state.coversExactly(java.util.List.of("Ada Lovelace", "Missing Person"))).isFalse();
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

    @Test
    void requiresEveryExpectedReferenceExactlyOnceWithoutExtraSearches() {
        CvChatToolDiscoveryState exact = new CvChatToolDiscoveryState();
        exact.record(" Fanon ", 1);
        exact.record("BORIS", 1);
        assertThat(exact.coversExactly(java.util.List.of("fanon", "boris"))).isTrue();

        exact.record("Fanon", 1);
        assertThat(exact.coversExactly(java.util.List.of("fanon", "boris"))).isFalse();

        CvChatToolDiscoveryState extra = new CvChatToolDiscoveryState();
        extra.record("Fanon", 1);
        extra.record("Boris", 1);
        extra.record("Yogesh", 1);
        assertThat(extra.coversExactly(java.util.List.of("Fanon", "Boris"))).isFalse();

        CvChatToolDiscoveryState duplicateExpectation = new CvChatToolDiscoveryState();
        duplicateExpectation.record("Fanon", 1);
        duplicateExpectation.record("Fanon", 1);
        assertThat(duplicateExpectation.coversExactly(java.util.List.of("Fanon", "Fanon"))).isFalse();
    }
}
