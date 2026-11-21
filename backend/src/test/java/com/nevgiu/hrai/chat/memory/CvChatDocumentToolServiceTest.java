package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.tool.CurrentDocumentStatus;
import com.nevgiu.hrai.chat.tool.CvChatDocumentToolState;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.chat.tool.DocumentStatusResult;
import com.nevgiu.hrai.chat.tool.IngestionWarning;
import com.nevgiu.hrai.chat.tool.IngestionWarningsResult;
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
    private final CvChatDocumentToolModel model = mock(CvChatDocumentToolModel.class);
    private final CvChatDocumentToolService service = new CvChatDocumentToolService(scope, registry, state, model);

    @Test
    void returnsEmptyForProfessionalQuestionWithoutToolCalls() {
        when(registry.documentStatusCallbacks()).thenReturn(List.of());
        when(model.respond(eq("Does the candidate know Java?"), eq(List.of(7L)), anyList()))
                .thenReturn(new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.NOT_APPLICABLE));

        Optional<String> answer = service.answer("Does the candidate know Java?", List.of(7L));

        assertThat(answer).isEmpty();
        verify(scope).confirm(List.of(7L));
    }

    @Test
    void rendersRecordedStatusAndWarningsWithoutProviderProse() {
        when(registry.documentStatusCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            state.record(new DocumentStatusResult(7L, new CurrentDocumentStatus(
                    70L, CvIngestionStatus.NEEDS_REVIEW, CvExtractionMethod.OCR,
                    Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-21T10:00:00Z"))));
            state.record(new IngestionWarningsResult(7L, 70L,
                    List.of(IngestionWarning.MANUAL_REVIEW_REQUIRED)));
            return new CvChatDocumentToolOutcome(
                    CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS);
        }).when(model).respond(eq("Is this CV ready and are there warnings?"), eq(List.of(7L)), anyList());

        String answer = service.answer("Is this CV ready and are there warnings?", List.of(7L)).orElseThrow();

        assertThat(answer)
                .contains("CANDIDATE-7", "NEEDS_REVIEW", "OCR", "Last reprocessed at",
                        "MANUAL_REVIEW_REQUIRED")
                .doesNotContain("filename", "storage", "raw");
    }

    @Test
    void failsClosedWhenProviderOmitsAConfirmedCandidateToolCall() {
        when(registry.documentStatusCallbacks()).thenReturn(List.of());
        doAnswer(invocation -> {
            state.record(new DocumentStatusResult(7L, null));
            return new CvChatDocumentToolOutcome(CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS);
        }).when(model).respond(eq("Are these CVs imported?"), eq(List.of(7L, 8L)), anyList());

        assertThatThrownBy(() -> service.answer("Are these CVs imported?", List.of(7L, 8L)))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV document status service is unavailable");
    }
}
