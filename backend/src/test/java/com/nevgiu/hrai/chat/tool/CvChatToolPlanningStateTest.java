package com.nevgiu.hrai.chat.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolPlanningStateTest {

    @Test
    void requiresEveryExpectedCandidateAndOptionalJob() {
        CvChatToolPlanningState state = new CvChatToolPlanningState();

        state.recordCandidateSummary(7L);
        assertThat(state.covers(Set.of(7L, 9L), null)).isFalse();

        state.recordCandidateSummary(9L);
        assertThat(state.covers(Set.of(7L, 9L), null)).isTrue();
        assertThat(state.covers(Set.of(7L, 9L), 21L)).isFalse();

        state.recordJobRequirements(21L);
        assertThat(state.covers(Set.of(7L, 9L), 21L)).isTrue();
        assertThat(state.snapshot()).isEqualTo(new CvChatToolPlanningState.Snapshot(2, 1));
    }
}
