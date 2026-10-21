package com.nevgiu.hrai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {
    @Transactional(readOnly = true)
    List<CandidateEvaluation> findAllByOrganizationId(String organizationId);

    long deleteAllByCandidateIdAndOrganizationId(Long candidateId, String organizationId);

    @Modifying
    @Query("""
            update CandidateEvaluation evaluation set evaluation.invalidatedAt = :invalidatedAt
            where evaluation.candidate.id = :candidateId
              and evaluation.organizationId = :organizationId
              and evaluation.invalidatedAt is null
            """)
    int invalidateCurrentByCandidateIdAndOrganizationId(Long candidateId, String organizationId,
                                                         Instant invalidatedAt);
}
