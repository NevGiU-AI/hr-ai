package com.nevgiu.hrai.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {
    @Transactional(readOnly = true)
    List<CandidateEvaluation> findAllByOrganizationId(String organizationId);

    // Project directly into the API read model so history does not hydrate raw CV text or large job-description LOBs.
    @Transactional(readOnly = true)
    @Query("""
            select new com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem(
                evaluation.id, candidate.id, candidate.name, job.id, job.title,
                evaluation.skillsMatchScore, evaluation.experienceRelevanceScore,
                evaluation.educationFitScore, evaluation.achievementImpactScore,
                evaluation.keywordDensityScore, evaluation.employmentGapScore,
                evaluation.readabilityScore, evaluation.aiConfidenceScore,
                evaluation.overallFitScore, evaluation.aiExplanation,
                evaluation.createdAt, evaluation.invalidatedAt,
                case when evaluation.invalidatedAt is null then true else false end)
            from CandidateEvaluation evaluation
            join evaluation.candidate candidate
            join evaluation.job job
            where candidate.id = :candidateId
              and evaluation.organizationId = :organizationId
            order by evaluation.createdAt desc, evaluation.id desc
            """)
    List<EvaluationHistoryItem> findHistoryByCandidateIdAndOrganizationId(
            Long candidateId, String organizationId);

    long deleteAllByCandidateIdAndOrganizationId(Long candidateId, String organizationId);

    @Modifying
    // Reprocessing supersedes evidence; timestamping preserves the old result while excluding it from current views.
    @Query("""
            update CandidateEvaluation evaluation set evaluation.invalidatedAt = :invalidatedAt
            where evaluation.candidate.id = :candidateId
              and evaluation.organizationId = :organizationId
              and evaluation.invalidatedAt is null
            """)
    int invalidateCurrentByCandidateIdAndOrganizationId(Long candidateId, String organizationId,
                                                         Instant invalidatedAt);
}
