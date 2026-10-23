package com.nevgiu.hrai.evaluation;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvOriginalAccessException;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
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
        // Return the same 404 for missing and cross-tenant IDs so record existence is not disclosed.
        candidates.findByIdAndOrganizationId(candidateId, organizationId)
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Candidate not found"));
        return evaluations.findHistoryByCandidateIdAndOrganizationId(candidateId, organizationId);
    }
}
