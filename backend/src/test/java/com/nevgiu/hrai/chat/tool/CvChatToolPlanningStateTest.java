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

    @Test
    void rejectsDuplicateExtraAndUnrequestedCalls() {
        CvChatToolPlanningState duplicateCandidate = new CvChatToolPlanningState();
        duplicateCandidate.recordCandidateSummary(7L);
        duplicateCandidate.recordCandidateSummary(7L);
        assertThat(duplicateCandidate.covers(Set.of(7L), null)).isFalse();

        CvChatToolPlanningState extraCandidate = new CvChatToolPlanningState();
        extraCandidate.recordCandidateSummary(7L);
        extraCandidate.recordCandidateSummary(9L);
        assertThat(extraCandidate.covers(Set.of(7L), null)).isFalse();

        CvChatToolPlanningState unrequestedJob = new CvChatToolPlanningState();
        unrequestedJob.recordCandidateSummary(7L);
        unrequestedJob.recordJobRequirements(21L);
        assertThat(unrequestedJob.covers(Set.of(7L), null)).isFalse();

        CvChatToolPlanningState duplicateJob = new CvChatToolPlanningState();
        duplicateJob.recordCandidateSummary(7L);
        duplicateJob.recordJobRequirements(21L);
        duplicateJob.recordJobRequirements(21L);
        assertThat(duplicateJob.covers(Set.of(7L), 21L)).isFalse();
    }
}
