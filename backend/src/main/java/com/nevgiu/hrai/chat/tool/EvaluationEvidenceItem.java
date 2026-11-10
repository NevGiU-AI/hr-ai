package com.nevgiu.hrai.chat.tool;

import java.time.Instant;

/**
 * Minimized persisted evaluation-history row for a confirmed candidate.
 *
 * <p>The generated explanation is intentionally absent: it may repeat personal information or raw CV text. The
 * current schema also lacks metric-level CV citations, prompt/model settings, weights, and source-document versions,
 * so this record is persisted evaluation context rather than independently verified or reproducible source evidence.</p>
 */
public record EvaluationEvidenceItem(
        Long evaluationId,
        Long jobId,
        String jobTitle,
        EvaluationScores scores,
        Instant createdAt,
        Instant invalidatedAt,
        boolean current
) { }
