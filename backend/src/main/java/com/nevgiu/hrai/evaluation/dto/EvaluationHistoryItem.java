package com.nevgiu.hrai.evaluation.dto;

import java.time.Instant;

/** Tenant-scoped evaluation history item with enough context for human review. */
public record EvaluationHistoryItem(
        Long id,
        Long candidateId,
        String candidateName,
        Long jobId,
        String jobTitle,
        int skillsMatchScore,
        int experienceRelevanceScore,
        int educationFitScore,
        int achievementImpactScore,
        int keywordDensityScore,
        int employmentGapScore,
        int readabilityScore,
        int aiConfidenceScore,
        int overallFitScore,
        String aiExplanation,
        Instant createdAt,
        Instant invalidatedAt,
        boolean current
) { }
