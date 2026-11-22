package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.tool.CurrentDocumentStatus;
import com.nevgiu.hrai.chat.tool.CvChatDocumentToolState;
import com.nevgiu.hrai.chat.tool.CvChatEvaluationToolState;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.chat.tool.DocumentStatusResult;
import com.nevgiu.hrai.chat.tool.IngestionWarning;
import com.nevgiu.hrai.chat.tool.IngestionWarningsResult;
import com.nevgiu.hrai.chat.tool.EvaluationEvidenceItem;
import com.nevgiu.hrai.chat.tool.EvaluationEvidenceResult;
import com.nevgiu.hrai.chat.tool.EvaluationScores;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvChatDocumentToolServiceTest {
    private final CvChatToolScope scope = mock(CvChatToolScope.class);
    private final CvChatReadToolRegistry registry = mock(CvChatReadToolRegistry.class);
    private final CvChatDocumentToolState state = new CvChatDocumentToolState();
    private final CvChatEvaluationToolState evaluationState = new CvChatEvaluationToolState();
    private final CvChatDocumentToolModel model = mock(CvChatDocumentToolModel.class);
    private final CvChatDocumentToolService service =
            new CvChatDocumentToolService(scope, registry, state, evaluationState, model);

    @Test
    void returnsEmptyForProfessionalQuestionWithoutToolCalls() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        when(model.respond(eq("Does the candidate know Java?"), eq(List.of(7L)), anyList()))
                .thenReturn(new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.NOT_APPLICABLE));

        Optional<String> answer = service.answer("Does the candidate know Java?", candidates(7L, "Ada"));

        assertThat(answer).isEmpty();
        verify(scope).confirm(List.of(7L));
    }

    @Test
    void rendersRecordedStatusAndWarningsWithoutProviderProse() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            state.record(new DocumentStatusResult(7L, new CurrentDocumentStatus(
                    70L, CvIngestionStatus.NEEDS_REVIEW, CvExtractionMethod.OCR,
                    Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-21T10:00:00Z"))));
            state.record(new IngestionWarningsResult(7L, 70L,
                    List.of(IngestionWarning.MANUAL_REVIEW_REQUIRED)));
            return new CvChatDocumentToolOutcome(
                    CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS);
        }).when(model).respond(eq("Is this CV ready and are there warnings?"), eq(List.of(7L)), anyList());

        String answer = service.answer("Is this CV ready and are there warnings?",
                candidates(7L, "  Ada   Lovelace  ")).orElseThrow();

        assertThat(answer)
                .contains("Ada Lovelace (CANDIDATE-7)", "NEEDS_REVIEW", "OCR", "Last reprocessed at",
                        "MANUAL_REVIEW_REQUIRED")
                .doesNotContain("filename", "storage", "raw");
    }

    @Test
    void failsClosedWhenProviderOmitsAConfirmedCandidateToolCall() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            state.record(new DocumentStatusResult(7L, null));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS);
        }).when(model).respond(eq("Are these CVs imported?"), eq(List.of(7L, 8L)), anyList());

        assertThatThrownBy(() -> service.answer("Are these CVs imported?",
                List.of(new CandidateIdentity(7L, "Ada", null), new CandidateIdentity(8L, "Grace", null))))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV operational data service is unavailable");
    }

    @Test
    void preservesConfirmedOrderAndNamesEveryCandidate() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            state.record(new DocumentStatusResult(8L, null));
            state.record(new DocumentStatusResult(7L, null));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS);
        }).when(model).respond(eq("Are these CVs imported?"), eq(List.of(8L, 7L)), anyList());

        String answer = service.answer("Are these CVs imported?", List.of(
                new CandidateIdentity(8L, "Grace Hopper", null),
                new CandidateIdentity(7L, "Ada Lovelace", null))).orElseThrow();

        assertThat(answer).startsWith("Grace Hopper (CANDIDATE-8):")
                .contains("\nAda Lovelace (CANDIDATE-7):");
    }

    @Test
    void rendersCurrentAndStalePersistedEvaluationsWithoutProviderProse() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            evaluationState.record(new EvaluationEvidenceResult(7L, List.of(
                    evaluation(9L, true, null),
                    evaluation(8L, false, Instant.parse("2026-11-21T12:00:00Z"))), false));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY);
        }).when(model).respond(eq("What was the latest evaluation and was the previous one invalidated?"),
                eq(List.of(7L)), anyList());

        String answer = service.answer("What was the latest evaluation and was the previous one invalidated?",
                candidates(7L, " Ada   Lovelace ")).orElseThrow();

        assertThat(answer).contains("Ada Lovelace (CANDIDATE-7)", "Evaluation 9", "CURRENT", "Evaluation 8",
                        "STALE", "Invalidated at", "overall fit 80/100", "human review")
                .doesNotContain("explanation", "CV text", "provider");
    }

    @Test
    void rendersNoPersistedEvaluationAsAnExplicitBoundedResult() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            evaluationState.record(new EvaluationEvidenceResult(7L, List.of(), false));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY);
        }).when(model).respond(eq("Show the evaluation history"), eq(List.of(7L)), anyList());

        assertThat(service.answer("Show the evaluation history", candidates(7L, "Ada")).orElseThrow())
                .isEqualTo("Ada (CANDIDATE-7): No persisted AI-assisted evaluation is available.");
    }

    @Test
    void failsClosedWhenEvaluationToolOmitsAConfirmedCandidate() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            evaluationState.record(new EvaluationEvidenceResult(7L, List.of(evaluation(9L, true, null)), false));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY);
        }).when(model).respond(eq("Compare their latest evaluation scores"), eq(List.of(7L, 8L)), anyList());

        assertThatThrownBy(() -> service.answer("Compare their latest evaluation scores", List.of(
                new CandidateIdentity(7L, "Ada", null), new CandidateIdentity(8L, "Grace", null))))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV operational data service is unavailable");
    }

    private EvaluationEvidenceItem evaluation(Long id, boolean current, Instant invalidatedAt) {
        return new EvaluationEvidenceItem(id, 12L, "Backend Engineer",
                new EvaluationScores(70, 7, 6, 5, 60, 10, 8, 75, 80),
                Instant.parse("2026-11-20T12:00:00Z"), invalidatedAt, current);
    }

    private List<CandidateIdentity> candidates(Long id, String name) {
        return List.of(new CandidateIdentity(id, name, null));
    }
}
