package com.nevgiu.hrai.chat.tool;

import java.util.List;

/** Bounded persisted evaluation history for one application-confirmed candidate. */
public record EvaluationEvidenceResult(
        Long candidateId,
        List<EvaluationEvidenceItem> evaluations,
        boolean truncated
) {
    public EvaluationEvidenceResult {
        evaluations = List.copyOf(evaluations);
    }
}
