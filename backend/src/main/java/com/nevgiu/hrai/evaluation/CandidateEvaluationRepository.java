package com.nevgiu.hrai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {
    @Transactional(readOnly = true)
    List<CandidateEvaluation> findAllByOrganizationId(String organizationId);
}
