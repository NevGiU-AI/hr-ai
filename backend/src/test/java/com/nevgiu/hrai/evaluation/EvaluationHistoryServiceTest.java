package com.nevgiu.hrai.evaluation;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvOriginalAccessException;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvaluationHistoryServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateEvaluationRepository evaluations = mock(CandidateEvaluationRepository.class);
    private final EvaluationHistoryService service = new EvaluationHistoryService(candidates, evaluations);

    @Test
    void returnsNewestTenantScopedEvaluationsWithCurrentAndStaleState() {
        Candidate candidate = candidate(7L, "Ada Lovelace");
        Instant invalidatedAt = Instant.parse("2026-10-23T09:00:00Z");
        EvaluationHistoryItem current = history(2L, null);
        EvaluationHistoryItem stale = history(1L, invalidatedAt);
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(evaluations.findHistoryByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of(current, stale));

        var history = service.findForCandidate(7L, "tenant-a");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).current()).isTrue();
        assertThat(history.get(0).jobTitle()).isEqualTo("Backend Engineer");
        assertThat(history.get(1).current()).isFalse();
        assertThat(history.get(1).invalidatedAt()).isEqualTo(invalidatedAt);
    }

    @Test
    void hidesMissingAndCrossTenantCandidatesBehindNotFound() {
        when(candidates.findByIdAndOrganizationId(7L, "tenant-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findForCandidate(7L, "tenant-b"))
                .isInstanceOf(CvOriginalAccessException.class);
        verifyNoInteractions(evaluations);
    }

    private Candidate candidate(Long id, String name) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setName(name);
        return candidate;
    }

    private EvaluationHistoryItem history(Long id, Instant invalidatedAt) {
        return new EvaluationHistoryItem(id, 7L, "Ada Lovelace", 12L, "Backend Engineer",
                90, 8, 7, 8, 75, 9, 8, 88, 82, "Strong match",
                Instant.parse("2026-10-23T08:00:00Z"), invalidatedAt, invalidatedAt == null);
    }
}
