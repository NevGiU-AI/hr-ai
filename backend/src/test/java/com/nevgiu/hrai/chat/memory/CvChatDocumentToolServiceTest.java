package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.CvChatAnswerKind;
import com.nevgiu.hrai.chat.CvChatComparisonService;
import com.nevgiu.hrai.chat.CvChatResponse;
import com.nevgiu.hrai.chat.CvChatService;
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
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CvChatDocumentToolServiceTest {
    private final CvChatToolScope scope = mock(CvChatToolScope.class);
    private final CvChatReadToolRegistry registry = mock(CvChatReadToolRegistry.class);
    private final CvChatReadTools readTools = mock(CvChatReadTools.class);
    private final CvChatDocumentToolState state = new CvChatDocumentToolState();
    private final CvChatEvaluationToolState evaluationState = new CvChatEvaluationToolState();
    private final CvChatDocumentToolModel model = mock(CvChatDocumentToolModel.class);
    private final CvChatDocumentToolService service =
            new CvChatDocumentToolService(scope, registry, readTools, state, evaluationState, model);

    @Test
    void keepsGroundedAndOperationalSourcesSeparateAcrossConversationTurns() {
        var conversations = mock(CvChatConversationService.class);
        var grounded = mock(CvChatService.class);
        var comparison = mock(CvChatComparisonService.class);
        var actor = new AppUserPrincipal(1L, "owner@example.com", "hash", "tenant-a", true, List.of());
        var candidates = candidates(7L, "Ada");
        String id = "conversation-1";
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        String groundedQuestion = "Which Java version does the CV document?";
        String groundedAnswer = "The CV documents Java 21 [CV-7].";
        var citations = List.of(new CvChatResponse.Citation("CV-7", 7L, 70L, null, "Java 21"));
        var history = new ArrayList<Message>();
        var questions = List.of(groundedQuestion, "Was this CV processed with OCR?",
                "Were any ingestion warnings recorded?", "What is the persisted evaluation history?",
                "Which Java version was that?");

        for (int index = 0; index < questions.size(); index++) {
            // A conversation survives across requests; callback counts and results do not.
            var requestStatus = new CvChatDocumentToolState();
            var requestEvaluations = new CvChatEvaluationToolState();
            var requestTools = mock(CvChatReadTools.class);
            var requestModel = mock(CvChatDocumentToolModel.class);
            var operational = new CvChatDocumentToolService(scope, registry, requestTools,
                    requestStatus, requestEvaluations, requestModel);
            var turns = new CvChatConversationTurnService(conversations, grounded, comparison, operational);
            String question = questions.get(index);
            long version = index;
            var turnHistory = List.copyOf(history);
            when(conversations.executionScope(id, actor)).thenReturn(
                    new CvChatConversationService.ExecutionScope(id, version, "tenant-a", candidates, null));
            when(conversations.history(id)).thenReturn(turnHistory);

            boolean isGrounded = index == 0 || index == questions.size() - 1;
            var expectedKind = isGrounded ? CvChatAnswerKind.ANSWER : CvChatAnswerKind.TOOL_STATUS;
            var expectedCitations = isGrounded ? citations : List.<CvChatResponse.Citation>of();
            when(conversations.appendTurn(eq(id), eq(version), eq(question), org.mockito.ArgumentMatchers.anyString(),
                    eq(expectedCitations), eq(expectedKind), org.mockito.ArgumentMatchers.isNull(), eq(actor)))
                    .thenReturn(new CvChatConversationResponse(id, version + 1,
                            List.of(new CvChatConversationResponse.CandidateCard(7L, "Ada", null)),
                            null, now, now, now.plusSeconds(600)));

            switch (index) {
                case 0, 4 -> {
                    when(requestModel.respond(eq(question), eq(List.of(7L)), anyList()))
                            .thenReturn(new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.NOT_APPLICABLE));
                    when(grounded.ask(7L, question, null, "tenant-a", turnHistory))
                            .thenReturn(new CvChatResponse(CvChatAnswerKind.ANSWER, groundedAnswer, citations));
                }
                case 1 -> when(requestTools.getDocumentStatus(7L)).thenAnswer(call -> {
                    var result = new DocumentStatusResult(7L, new CurrentDocumentStatus(
                            70L, CvIngestionStatus.IMPORTED, CvExtractionMethod.OCR, now, null));
                    requestStatus.record(result);
                    return result;
                });
                case 2 -> when(requestTools.getIngestionWarnings(7L)).thenAnswer(call -> {
                    var result = new IngestionWarningsResult(7L, 70L, List.of(IngestionWarning.MANUAL_REVIEW_REQUIRED));
                    requestStatus.record(result);
                    return result;
                });
                case 3 -> when(requestTools.getEvaluationEvidence(7L)).thenAnswer(call -> {
                    var result = new EvaluationEvidenceResult(7L, List.of(evaluation(9L, true, null),
                            evaluation(8L, false, now)), false);
                    requestEvaluations.record(result);
                    return result;
                });
                default -> throw new AssertionError("Unexpected turn");
            }

            var response = turns.ask(id, question, actor);
            assertThat(response.conversationId()).isEqualTo(id);
            assertThat(response.version()).isEqualTo(version + 1);
            assertThat(response.kind()).isEqualTo(expectedKind);
            assertThat(response.citations()).isEqualTo(expectedCitations);
            if (!isGrounded) assertThat(response.answer()).doesNotContain("[CV-");
            switch (index) {
                case 0, 4 -> {
                    assertThat(response.answer()).isEqualTo(groundedAnswer);
                    verify(grounded).ask(7L, question, null, "tenant-a", turnHistory);
                    verifyNoInteractions(requestTools);
                }
                case 1 -> assertThat(response.answer()).contains("IMPORTED", "OCR");
                case 2 -> assertThat(response.answer()).contains("MANUAL_REVIEW_REQUIRED");
                case 3 -> assertThat(response.answer()).contains("Evaluation 9", "CURRENT", "Evaluation 8",
                        "STALE", "Invalidated at", "overall fit 80/100");
                default -> throw new AssertionError("Unexpected turn");
            }
            if (!isGrounded) verifyNoInteractions(requestModel);
            verify(conversations).appendTurn(id, version, question, response.answer(),
                    expectedCitations, expectedKind, null, actor);
            history.add(new UserMessage(question));
            history.add(new AssistantMessage(response.answer()));
        }
        verifyNoMoreInteractions(grounded);
        verifyNoInteractions(comparison);
    }

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
        }).when(model).respond(eq("Are these records available?"), eq(List.of(7L, 8L)), anyList());

        assertThatThrownBy(() -> service.answer("Are these records available?",
                List.of(new CandidateIdentity(7L, "Ada", null), new CandidateIdentity(8L, "Grace", null))))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV operational data service is unavailable");
    }

    @Test
    void preservesConfirmedOrderAndNamesEveryCandidate() {
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            DocumentStatusResult result = new DocumentStatusResult(id, null);
            state.record(result);
            return result;
        }).when(readTools).getDocumentStatus(anyLong());

        String answer = service.answer("Are these CVs imported?", List.of(
                new CandidateIdentity(8L, "Grace Hopper", null),
                new CandidateIdentity(7L, "Ada Lovelace", null))).orElseThrow();

        assertThat(answer).startsWith("Grace Hopper (CANDIDATE-8):")
                .contains("\n\nAda Lovelace (CANDIDATE-7):");
        verifyNoInteractions(model);
    }

    @Test
    void executesShortOcrQuestionExactlyOnceForEveryConfirmedCandidate() {
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            DocumentStatusResult result = new DocumentStatusResult(id, new CurrentDocumentStatus(
                    id + 100, CvIngestionStatus.IMPORTED, CvExtractionMethod.NATIVE,
                    Instant.parse("2026-11-20T10:00:00Z"), null));
            state.record(result);
            return result;
        }).when(readTools).getDocumentStatus(anyLong());

        List<CandidateIdentity> confirmedCandidates = List.of(
                new CandidateIdentity(7L, "Ada", null),
                new CandidateIdentity(8L, "Grace", null),
                new CandidateIdentity(9L, "Linus", null),
                new CandidateIdentity(10L, "Margaret", null),
                new CandidateIdentity(11L, "Edsger", null));

        String answer = service.answer("CVs processed with OCR?", confirmedCandidates).orElseThrow();

        assertThat(answer)
                .contains("Ada (CANDIDATE-7)", "Grace (CANDIDATE-8)", "Linus (CANDIDATE-9)",
                        "Margaret (CANDIDATE-10)", "Edsger (CANDIDATE-11)")
                .doesNotContain("OCR");
        verify(readTools).getDocumentStatus(7L);
        verify(readTools).getDocumentStatus(8L);
        verify(readTools).getDocumentStatus(9L);
        verify(readTools).getDocumentStatus(10L);
        verify(readTools).getDocumentStatus(11L);
        verifyNoInteractions(model);
    }

    @Test
    void rendersCurrentAndStalePersistedEvaluationsWithoutProviderProse() {
        doAnswer(invocation -> {
            EvaluationEvidenceResult result = new EvaluationEvidenceResult(7L, List.of(
                    evaluation(9L, true, null),
                    evaluation(8L, false, Instant.parse("2026-11-21T12:00:00Z"))), false);
            evaluationState.record(result);
            return result;
        }).when(readTools).getEvaluationEvidence(7L);

        String answer = service.answer("What was the latest evaluation and was the previous one invalidated?",
                candidates(7L, " Ada   Lovelace ")).orElseThrow();

        assertThat(answer).contains("Ada Lovelace (CANDIDATE-7)", "Evaluation 9", "CURRENT", "Evaluation 8",
                        "STALE", "Invalidated at", "overall fit 80/100", "human review")
                .doesNotContain("explanation", "CV text", "provider");
        verifyNoInteractions(model);
    }

    @Test
    void separatesCandidateEvaluationHistoriesIntoParagraphs() {
        doAnswer(invocation -> {
            Long candidateId = invocation.getArgument(0);
            EvaluationEvidenceResult result = new EvaluationEvidenceResult(candidateId, List.of(), false);
            evaluationState.record(result);
            return result;
        }).when(readTools).getEvaluationEvidence(anyLong());

        String answer = service.answer("Show the evaluation history for each selected candidate", List.of(
                new CandidateIdentity(8L, "Grace Hopper", null),
                new CandidateIdentity(7L, "Ada Lovelace", null))).orElseThrow();

        assertThat(answer).isEqualTo(
                "Grace Hopper (CANDIDATE-8): No persisted AI-assisted evaluation is available.\n\n"
                        + "Ada Lovelace (CANDIDATE-7): No persisted AI-assisted evaluation is available.");
        verifyNoInteractions(model);
    }

    @Test
    void rendersNoPersistedEvaluationAsAnExplicitBoundedResult() {
        doAnswer(invocation -> {
            EvaluationEvidenceResult result = new EvaluationEvidenceResult(7L, List.of(), false);
            evaluationState.record(result);
            return result;
        }).when(readTools).getEvaluationEvidence(7L);

        assertThat(service.answer("Show the evaluation history", candidates(7L, "Ada")).orElseThrow())
                .isEqualTo("Ada (CANDIDATE-7): No persisted AI-assisted evaluation is available.");
    }

    @Test
    void failsClosedWhenEvaluationToolOmitsAConfirmedCandidate() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            evaluationState.record(new EvaluationEvidenceResult(7L, List.of(evaluation(9L, true, null)), false));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.EVALUATION_HISTORY);
        }).when(model).respond(eq("Compare their stored scoring records"), eq(List.of(7L, 8L)), anyList());

        assertThatThrownBy(() -> service.answer("Compare their stored scoring records", List.of(
                new CandidateIdentity(7L, "Ada", null), new CandidateIdentity(8L, "Grace", null))))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV operational data service is unavailable");
    }

    @Test
    void preservesReviewedTimeoutFromDeterministicOperationalTool() {
        when(readTools.getDocumentStatus(7L)).thenThrow(
                new CvChatException(HttpStatus.GATEWAY_TIMEOUT, "CV chat tool execution timed out"));

        assertThatThrownBy(() -> service.answer("Was this CV processed with OCR?", candidates(7L, "Ada")))
                .isInstanceOfSatisfying(CvChatException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(failure).hasMessage("CV chat tool execution timed out");
                });
        verifyNoInteractions(model);
    }

    @Test
    void preservesReviewedTimeoutRethrownBySpringAiOperationalCallback() {
        when(registry.conversationalOperationalCallbacks()).thenReturn(List.of());
        when(model.respond(eq("Could you check its processing state?"), eq(List.of(7L)), anyList()))
                .thenThrow(new CvChatException(HttpStatus.GATEWAY_TIMEOUT,
                        "CV chat tool execution timed out"));

        assertThatThrownBy(() -> service.answer("Could you check its processing state?", candidates(7L, "Ada")))
                .isInstanceOfSatisfying(CvChatException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(failure).hasMessage("CV chat tool execution timed out");
                });
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
