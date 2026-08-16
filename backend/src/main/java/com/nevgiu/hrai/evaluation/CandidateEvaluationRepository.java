package com.nevgiu.hrai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {
    List<CandidateEvaluation> findAllByOrganizationId(String organizationId);
}
