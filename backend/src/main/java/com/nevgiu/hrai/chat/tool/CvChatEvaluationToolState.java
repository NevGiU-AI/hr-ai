package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashMap;
import java.util.Map;

/** Request-local proof of the evaluation-history callbacks that actually completed successfully. */
@Component
@RequestScope
public class CvChatEvaluationToolState {
    private final Map<Long, EvaluationEvidenceResult> results = new LinkedHashMap<>();
    private final Map<Long, Integer> calls = new LinkedHashMap<>();

    public void record(EvaluationEvidenceResult result) {
        // Count separately from the result map so repeated model calls cannot be hidden by replacement of one key.
        results.put(result.candidateId(), result);
        calls.merge(result.candidateId(), 1, Integer::sum);
    }

    public Snapshot snapshot() {
        return new Snapshot(Map.copyOf(results), Map.copyOf(calls));
    }

    public record Snapshot(Map<Long, EvaluationEvidenceResult> results, Map<Long, Integer> calls) { }
}
