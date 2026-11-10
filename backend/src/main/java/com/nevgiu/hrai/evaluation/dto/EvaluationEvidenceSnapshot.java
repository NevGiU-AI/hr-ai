package com.nevgiu.hrai.evaluation.dto;

import java.time.Instant;

/**
 * Privacy-minimized persistence projection used by the evaluation-evidence read tool.
 *
 * <p>The query deliberately omits candidate identity, organization identifiers, raw CV content, and the generated
 * explanation. A stored explanation may repeat personal information or CV text, so it must not cross this
 * model-facing boundary until a separately reviewed redaction and citation design exists.</p>
 */
public record EvaluationEvidenceSnapshot(
        Long evaluationId,
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
        Instant createdAt,
        Instant invalidatedAt,
        boolean current
) { }
