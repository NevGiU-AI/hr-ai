package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.tool.CurrentDocumentStatus;
import com.nevgiu.hrai.chat.tool.CvChatDocumentToolState;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.chat.tool.DocumentStatusResult;
import com.nevgiu.hrai.chat.tool.IngestionWarning;
import com.nevgiu.hrai.chat.tool.IngestionWarningsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Routes operational questions through the reviewed tools and renders only their recorded safe DTOs. */
@Service
class CvChatDocumentToolService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatDocumentToolService.class);
    private final CvChatToolScope toolScope;
    private final CvChatReadToolRegistry toolRegistry;
    private final CvChatDocumentToolState state;
    private final CvChatDocumentToolModel model;

    CvChatDocumentToolService(CvChatToolScope toolScope, CvChatReadToolRegistry toolRegistry,
                              CvChatDocumentToolState state, CvChatDocumentToolModel model) {
        this.toolScope = toolScope;
        this.toolRegistry = toolRegistry;
        this.state = state;
        this.model = model;
    }

    Optional<String> answer(String message, List<Long> candidateIds) {
        toolScope.confirm(candidateIds);
        CvChatDocumentToolOutcome outcome;
        try {
            outcome = model.respond(message, candidateIds, toolRegistry.documentStatusCallbacks());
        } catch (RuntimeException failure) {
            LOGGER.warn("CV chat document-tool routing failed candidateCount={} failureType={}",
                    candidateIds.size(), failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV document status service is unavailable");
        }
        var snapshot = state.snapshot();
        if (outcome == null || outcome.kind() == null) {
            throw unavailable(candidateIds.size(), "MALFORMED_OUTCOME");
        }
        if (outcome.kind() == CvChatDocumentToolOutcome.Kind.NOT_APPLICABLE) {
            if (!snapshot.statuses().isEmpty() || !snapshot.warnings().isEmpty()) {
                throw unavailable(candidateIds.size(), "UNEXPECTED_TOOL_CALL");
            }
            return Optional.empty();
        }
        boolean needsStatus = outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS
                || outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS;
        boolean needsWarnings = outcome.kind() == CvChatDocumentToolOutcome.Kind.INGESTION_WARNINGS
                || outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS;
        if (!covers(snapshot.statuses(), snapshot.statusCalls(), candidateIds, needsStatus)
                || !covers(snapshot.warnings(), snapshot.warningCalls(), candidateIds, needsWarnings)) {
            throw unavailable(candidateIds.size(), "INCOMPLETE_TOOL_CALLS");
        }
        LOGGER.info("CV chat document tools completed candidateCount={} statusCalls={} warningCalls={}",
                candidateIds.size(), snapshot.statuses().size(), snapshot.warnings().size());
        return Optional.of(render(candidateIds, snapshot, needsStatus, needsWarnings));
    }

    private boolean covers(Map<Long, ?> results, Map<Long, Integer> calls,
                           List<Long> candidateIds, boolean required) {
        if (!required) return results.isEmpty() && calls.isEmpty();
        Set<Long> expected = Set.copyOf(candidateIds);
        return results.keySet().equals(expected) && calls.keySet().equals(expected)
                && calls.values().stream().allMatch(count -> count == 1);
    }

    private String render(List<Long> candidateIds, CvChatDocumentToolState.Snapshot snapshot,
                          boolean includeStatus, boolean includeWarnings) {
        return candidateIds.stream().map(candidateId -> {
            StringBuilder line = new StringBuilder("CANDIDATE-").append(candidateId).append(": ");
            if (includeStatus) appendStatus(line, snapshot.statuses().get(candidateId));
            if (includeStatus && includeWarnings) line.append(' ');
            if (includeWarnings) appendWarnings(line, snapshot.warnings().get(candidateId));
            return line.toString();
        }).collect(Collectors.joining("\n"));
    }

    private void appendStatus(StringBuilder answer, DocumentStatusResult result) {
        CurrentDocumentStatus document = result.currentDocument();
        if (document == null) {
            answer.append("No current CV document is available.");
            return;
        }
        answer.append("Current document status is ").append(document.status()).append(". Text extraction used ")
                .append(document.extractionMethod()).append(". Imported at ").append(document.importedAt()).append('.');
        if (document.reprocessedAt() == null) {
            answer.append(" The document has not been reprocessed.");
        } else {
            answer.append(" Last reprocessed at ").append(document.reprocessedAt()).append('.');
        }
    }

    private void appendWarnings(StringBuilder answer, IngestionWarningsResult result) {
        List<IngestionWarning> warnings = result.warnings();
        if (warnings.isEmpty()) {
            answer.append("No ingestion warnings are recorded.");
        } else {
            answer.append("Ingestion warnings: ")
                    .append(warnings.stream().map(Enum::name).collect(Collectors.joining(", "))).append('.');
        }
    }

    private CvChatException unavailable(int candidateCount, String stage) {
        LOGGER.warn("CV chat document-tool postcondition failed candidateCount={} stage={}", candidateCount, stage);
        return new CvChatException(HttpStatus.BAD_GATEWAY, "CV document status service is unavailable");
    }
}
