package com.nevgiu.hrai.evaluation;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvOriginalAccessException;
import com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Reads persisted evaluation history without weakening candidate tenant isolation. */
@Service
public class EvaluationHistoryService {
    private final CandidateRepository candidates;
    private final CandidateEvaluationRepository evaluations;

    public EvaluationHistoryService(CandidateRepository candidates, CandidateEvaluationRepository evaluations) {
        this.candidates = candidates;
        this.evaluations = evaluations;
    }

    @Transactional(readOnly = true)
    public List<EvaluationHistoryItem> findForCandidate(Long candidateId, String organizationId) {
        requireTenantCandidate(candidateId, organizationId);
        return evaluations.findHistoryByCandidateIdAndOrganizationId(candidateId, organizationId);
    }

    /**
     * Returns at most {@code maxResults} newest evaluation-history rows for a bounded downstream consumer.
     *
     * <p>The caller may request one extra row to detect truncation without loading the candidate's complete history.
     * This method keeps the same missing-versus-cross-tenant 404 behavior as the public history endpoint.</p>
     */
    @Transactional(readOnly = true)
    public List<EvaluationEvidenceSnapshot> findRecentEvidenceForCandidate(Long candidateId, String organizationId,
                                                                            int maxResults) {
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("maxResults must be between 1 and 100");
        }
        requireTenantCandidate(candidateId, organizationId);
        return evaluations.findRecentEvidenceByCandidateIdAndOrganizationId(
                candidateId, organizationId, PageRequest.of(0, maxResults));
    }

    private void requireTenantCandidate(Long candidateId, String organizationId) {
        // Return the same 404 for missing and cross-tenant IDs so record existence is not disclosed.
        candidates.findByIdAndOrganizationId(candidateId, organizationId)
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }
}
