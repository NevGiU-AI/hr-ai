package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashMap;
import java.util.Map;

/** Request-local record of minimized document-tool results actually returned to the model. */
@Component
@RequestScope
public class CvChatDocumentToolState {
    private final Map<Long, DocumentStatusResult> statuses = new LinkedHashMap<>();
    private final Map<Long, IngestionWarningsResult> warnings = new LinkedHashMap<>();
    private final Map<Long, Integer> statusCalls = new LinkedHashMap<>();
    private final Map<Long, Integer> warningCalls = new LinkedHashMap<>();

    public void record(DocumentStatusResult result) {
        statuses.put(result.candidateId(), result);
        statusCalls.merge(result.candidateId(), 1, Integer::sum);
    }
    public void record(IngestionWarningsResult result) {
        warnings.put(result.candidateId(), result);
        warningCalls.merge(result.candidateId(), 1, Integer::sum);
    }

    public Snapshot snapshot() {
        return new Snapshot(Map.copyOf(statuses), Map.copyOf(warnings),
                Map.copyOf(statusCalls), Map.copyOf(warningCalls));
    }

    public record Snapshot(Map<Long, DocumentStatusResult> statuses,
                           Map<Long, IngestionWarningsResult> warnings,
                           Map<Long, Integer> statusCalls,
                           Map<Long, Integer> warningCalls) { }
}
