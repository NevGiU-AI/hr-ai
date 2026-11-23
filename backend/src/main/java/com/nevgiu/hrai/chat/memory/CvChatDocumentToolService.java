package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.tool.CurrentDocumentStatus;
import com.nevgiu.hrai.chat.tool.CvChatDocumentToolState;
import com.nevgiu.hrai.chat.tool.CvChatEvaluationToolState;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatReadTools;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.chat.tool.DocumentStatusResult;
import com.nevgiu.hrai.chat.tool.IngestionWarning;
import com.nevgiu.hrai.chat.tool.IngestionWarningsResult;
import com.nevgiu.hrai.chat.tool.EvaluationEvidenceItem;
import com.nevgiu.hrai.chat.tool.EvaluationEvidenceResult;
import com.nevgiu.hrai.chat.tool.EvaluationScores;
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
    private final CvChatReadTools readTools;
    private final CvChatDocumentToolState state;
    private final CvChatEvaluationToolState evaluationState;
    private final CvChatDocumentToolModel model;

    CvChatDocumentToolService(CvChatToolScope toolScope, CvChatReadToolRegistry toolRegistry, CvChatReadTools readTools,
                              CvChatDocumentToolState state, CvChatEvaluationToolState evaluationState,
                              CvChatDocumentToolModel model) {
        this.toolScope = toolScope;
        this.toolRegistry = toolRegistry;
        this.readTools = readTools;
        this.state = state;
        this.evaluationState = evaluationState;
        this.model = model;
    }

    Optional<String> answer(String message, List<CandidateIdentity> candidates) {
        List<Long> candidateIds = candidates.stream().map(CandidateIdentity::id).toList();
        // Conversation scope has already been reauthorized from the database. Reinstall it in this request-local tool
        // boundary because a model-generated tool argument is never authorization by itself.
        toolScope.confirm(candidateIds);
        CvChatDocumentToolOutcome outcome;
        try {
            CvChatOperationalIntent.Kind explicitIntent = CvChatOperationalIntent.classify(message);
            if (explicitIntent == CvChatOperationalIntent.Kind.AMBIGUOUS) {
                // Only genuinely ambiguous language reaches the provider. Explicit operational requests use the
                // deterministic path below so mandatory per-candidate execution cannot vary by wording or model run.
                outcome = model.respond(message, candidateIds, toolRegistry.conversationalOperationalCallbacks());
            } else {
                outcome = executeExplicit(explicitIntent, candidateIds);
            }
        } catch (CvChatException failure) {
            // Read-tool failures already carry a reviewed status and privacy-safe message. CvChatToolExecutor creates
            // the 504 timeout and 503 interruption responses; CvChatToolExecutionGuard creates the 429 global and
            // capability-budget responses; CvChatReadTools enforces authentication and role access; CvChatToolScope
            // enforces confirmed candidate/job authorization; and CvChatToolArguments rejects malformed model-generated
            // IDs and queries. Preserve those outcomes instead of flattening them to a misleading provider-style 502.
            throw failure;
        } catch (RuntimeException failure) {
            LOGGER.warn("CV chat document-tool routing failed candidateCount={} failureType={}",
                    candidateIds.size(), failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV operational data service is unavailable");
        }
        // The model's structured kind describes its intended route, but it does not prove that callbacks ran. The
        // request-local snapshot below records the real Java tool results and call counts independently.
        var snapshot = state.snapshot();
        var evaluationSnapshot = evaluationState.snapshot();
        if (outcome == null || outcome.kind() == null) {
            throw unavailable(candidateIds.size(), "MALFORMED_OUTCOME");
        }
        if (outcome.kind() == CvChatDocumentToolOutcome.Kind.NOT_APPLICABLE) {
            // A model must not inspect operational data speculatively and then claim this is a normal RAG question.
            // Such a mismatch is a postcondition failure instead of silently discarding an unauthorized extra read.
            if (!snapshot.statuses().isEmpty() || !snapshot.warnings().isEmpty()
                    || !evaluationSnapshot.results().isEmpty()) {
                throw unavailable(candidateIds.size(), "UNEXPECTED_TOOL_CALL");
            }
            return Optional.empty();
        }
        boolean needsStatus = outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS
                || outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS;
        boolean needsWarnings = outcome.kind() == CvChatDocumentToolOutcome.Kind.INGESTION_WARNINGS
                || outcome.kind() == CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS;
        boolean needsEvaluations = outcome.kind() == CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY;
        // Validate results and counts rather than trusting the provider's outcome. This proves that every confirmed
        // candidate was read exactly once by every required tool and that no unrequested tool category executed.
        if (!covers(snapshot.statuses(), snapshot.statusCalls(), candidateIds, needsStatus)
                || !covers(snapshot.warnings(), snapshot.warningCalls(), candidateIds, needsWarnings)
                || !covers(evaluationSnapshot.results(), evaluationSnapshot.calls(), candidateIds, needsEvaluations)) {
            throw unavailable(candidateIds.size(), "INCOMPLETE_TOOL_CALLS");
        }
        LOGGER.info("CV chat operational tools completed candidateCount={} statusCalls={} warningCalls={} evaluationCalls={}",
                candidateIds.size(), snapshot.statuses().size(), snapshot.warnings().size(),
                evaluationSnapshot.results().size());
        if (needsEvaluations) return Optional.of(renderEvaluations(candidates, evaluationSnapshot));
        return Optional.of(render(candidates, snapshot, needsStatus, needsWarnings));
    }

    private CvChatDocumentToolOutcome executeExplicit(CvChatOperationalIntent.Kind intent, List<Long> candidateIds) {
        // Reuse the same @Tool methods as Spring AI callbacks. They independently enforce confirmed scope, tenant and
        // role authorization, guarded timeouts/call budgets, result bounds, and request-local execution evidence.
        for (Long candidateId : candidateIds) {
            if (intent == CvChatOperationalIntent.Kind.DOCUMENT_STATUS
                    || intent == CvChatOperationalIntent.Kind.DOCUMENT_STATUS_AND_WARNINGS) {
                readTools.getDocumentStatus(candidateId);
            }
            if (intent == CvChatOperationalIntent.Kind.INGESTION_WARNINGS
                    || intent == CvChatOperationalIntent.Kind.DOCUMENT_STATUS_AND_WARNINGS) {
                readTools.getIngestionWarnings(candidateId);
            }
            if (intent == CvChatOperationalIntent.Kind.EVALUATION_HISTORY) {
                readTools.getEvaluationEvidence(candidateId);
            }
        }
        return new CvChatDocumentToolOutcome(switch (intent) {
            case DOCUMENT_STATUS -> CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS;
            case INGESTION_WARNINGS -> CvChatDocumentToolOutcome.Kind.INGESTION_WARNINGS;
            case DOCUMENT_STATUS_AND_WARNINGS -> CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS;
            case EVALUATION_HISTORY -> CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY;
            case AMBIGUOUS -> throw new IllegalStateException("Ambiguous intent cannot be executed deterministically");
        });
    }

    private boolean covers(Map<Long, ?> results, Map<Long, Integer> calls,
                           List<Long> candidateIds, boolean required) {
        // Map values alone cannot detect duplicate calls because a later result replaces the earlier value. Separate
        // counters retain that evidence and let this check reject retries or recursion even when IDs are identical.
        if (!required) return results.isEmpty() && calls.isEmpty();
        Set<Long> expected = Set.copyOf(candidateIds);
        return results.keySet().equals(expected) && calls.keySet().equals(expected)
                && calls.values().stream().allMatch(count -> count == 1);
    }

    private String render(List<CandidateIdentity> candidates, CvChatDocumentToolState.Snapshot snapshot,
                          boolean includeStatus, boolean includeWarnings) {
        // Never display provider-written presentation text or raw entity fields. Render the closed safe DTOs in the
        // application-confirmed order so the response remains deterministic and auditable.
        return candidates.stream().map(candidate -> {
            Long candidateId = candidate.id();
            StringBuilder line = new StringBuilder(candidateLabel(candidate)).append(": ");
            if (includeStatus) appendStatus(line, snapshot.statuses().get(candidateId));
            if (includeStatus && includeWarnings) line.append(' ');
            if (includeWarnings) appendWarnings(line, snapshot.warnings().get(candidateId));
            return line.toString();
        // A blank line makes each candidate a distinct paragraph in both Angular renderers. They preserve backend
        // whitespace, so a single newline technically separated records but still produced a visually dense block.
        }).collect(Collectors.joining("\n\n"));
    }

    /** Uses the same first-mention convention as grounded single- and multi-candidate answers. */
    private String candidateLabel(CandidateIdentity candidate) {
        String name = candidate.name() == null || candidate.name().isBlank()
                ? "Candidate #" + candidate.id()
                : candidate.name().replaceAll("\\s+", " ").trim();
        return name + " (CANDIDATE-" + candidate.id() + ")";
    }

    private String renderEvaluations(List<CandidateIdentity> candidates,
                                     CvChatEvaluationToolState.Snapshot snapshot) {
        return candidates.stream().map(candidate -> {
            EvaluationEvidenceResult result = snapshot.results().get(candidate.id());
            if (result.evaluations().isEmpty()) {
                return candidateLabel(candidate) + ": No persisted AI-assisted evaluation is available.";
            }
            String rows = result.evaluations().stream().map(this::renderEvaluation).collect(Collectors.joining(" "));
            return candidateLabel(candidate) + ": Persisted AI-assisted evaluation history: " + rows
                    + (result.truncated() ? " Additional older evaluations are not shown." : "")
                    + " Scores are decision-support context for human review, not an autonomous hiring decision.";
        // Evaluation histories can be long. Keep each confirmed candidate in a separate paragraph so one candidate's
        // scores and stale/current state cannot visually run into the next candidate's record.
        }).collect(Collectors.joining("\n\n"));
    }

    private String renderEvaluation(EvaluationEvidenceItem item) {
        EvaluationScores scores = item.scores();
        String job = item.jobTitle() == null || item.jobTitle().isBlank()
                ? "JOB-" + item.jobId() : item.jobTitle() + " (JOB-" + item.jobId() + ")";
        StringBuilder row = new StringBuilder("Evaluation ").append(item.evaluationId()).append(" for ").append(job)
                .append(" is ").append(item.current() ? "CURRENT" : "STALE").append(", created at ")
                .append(item.createdAt()).append(". Scores: overall fit ").append(scores.overallFit())
                .append("/100, skills match ").append(scores.skillsMatch()).append("/100, experience relevance ")
                .append(scores.experienceRelevance()).append("/10, education fit ").append(scores.educationFit())
                .append("/10, achievement impact ").append(scores.achievementImpact())
                .append("/10, keyword density ").append(scores.keywordDensity())
                .append("/100, employment gap ").append(scores.employmentGap()).append("/10, readability ")
                .append(scores.readability()).append("/10, AI confidence ").append(scores.aiConfidence()).append("/100.");
        if (item.invalidatedAt() != null) row.append(" Invalidated at ").append(item.invalidatedAt()).append('.');
        return row.toString();
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
        // Warning enums are application-owned disclosure categories. Raw ingestionError text is deliberately absent
        // because it can contain parser/provider internals, filenames, or other unsafe diagnostic material.
        List<IngestionWarning> warnings = result.warnings();
        if (warnings.isEmpty()) {
            answer.append("No ingestion warnings are recorded.");
        } else {
            answer.append("Ingestion warnings: ")
                    .append(warnings.stream().map(Enum::name).collect(Collectors.joining(", "))).append('.');
        }
    }

    private CvChatException unavailable(int candidateCount, String stage) {
        LOGGER.warn("CV chat operational-tool postcondition failed candidateCount={} stage={}", candidateCount, stage);
        return new CvChatException(HttpStatus.BAD_GATEWAY, "CV operational data service is unavailable");
    }
}
