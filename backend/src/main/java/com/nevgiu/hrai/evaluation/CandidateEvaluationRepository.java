package com.nevgiu.hrai.evaluation;

import com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot;
import com.nevgiu.hrai.evaluation.dto.EvaluationHistoryItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation, Long> {
    String HISTORY_PROJECTION_QUERY = """
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
              and candidate.organizationId = :organizationId
              and job.organizationId = :organizationId
            order by evaluation.createdAt desc, evaluation.id desc
            """;
    String EVIDENCE_PROJECTION_QUERY = """
            select new com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot(
                evaluation.id, job.id, job.title,
                evaluation.skillsMatchScore, evaluation.experienceRelevanceScore,
                evaluation.educationFitScore, evaluation.achievementImpactScore,
                evaluation.keywordDensityScore, evaluation.employmentGapScore,
                evaluation.readabilityScore, evaluation.aiConfidenceScore,
                evaluation.overallFitScore, evaluation.createdAt, evaluation.invalidatedAt,
                case when evaluation.invalidatedAt is null then true else false end)
            from CandidateEvaluation evaluation
            join evaluation.candidate candidate
            join evaluation.job job
            where candidate.id = :candidateId
              and evaluation.organizationId = :organizationId
              and candidate.organizationId = :organizationId
              and job.organizationId = :organizationId
            order by evaluation.createdAt desc, evaluation.id desc
            """;

    @Transactional(readOnly = true)
    List<CandidateEvaluation> findAllByOrganizationId(String organizationId);

    // Project directly into the API read model so history does not hydrate raw CV text or large job-description LOBs.
    @Transactional(readOnly = true)
    @Query(HISTORY_PROJECTION_QUERY)
    List<EvaluationHistoryItem> findHistoryByCandidateIdAndOrganizationId(
            Long candidateId, String organizationId);

    /** Uses a privacy-minimized, tenant-consistent projection and database-enforced limit for model-facing reads. */
    @Transactional(readOnly = true)
    @Query(EVIDENCE_PROJECTION_QUERY)
    List<EvaluationEvidenceSnapshot> findRecentEvidenceByCandidateIdAndOrganizationId(
            Long candidateId, String organizationId, Pageable pageable);

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
