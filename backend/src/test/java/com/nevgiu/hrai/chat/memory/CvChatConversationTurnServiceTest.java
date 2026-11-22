package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.chat.CvChatComparisonResponse;
import com.nevgiu.hrai.chat.CvChatComparisonService;
import com.nevgiu.hrai.chat.CvChatResponse;
import com.nevgiu.hrai.chat.CvChatService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatConversationTurnServiceTest {
    private final CvChatConversationService conversations = mock(CvChatConversationService.class);
    private final CvChatService singleCandidateChat = mock(CvChatService.class);
    private final CvChatComparisonService comparisonChat = mock(CvChatComparisonService.class);
    private final CvChatDocumentToolService documentTools = mock(CvChatDocumentToolService.class);
    private final CvChatConversationTurnService service = new CvChatConversationTurnService(
            conversations, singleCandidateChat, comparisonChat, documentTools);

    @Test
    void reusesAuthorizedSingleCandidateScopeAndPersistsTheCompletedTurn() {
        AppUserPrincipal actor = actor();
        List<Message> history = List.of(new AssistantMessage("Earlier grounded answer [CV-7]."));
        when(conversations.executionScope("conversation-1", actor)).thenReturn(
                new CvChatConversationService.ExecutionScope(
                        "conversation-1", 3, "tenant-a", List.of(candidate(7L, "Ada")), null));
        when(conversations.history("conversation-1")).thenReturn(history);
        when(documentTools.answer("What about Spring?", List.of(candidate(7L, "Ada"))))
                .thenReturn(java.util.Optional.empty());
        when(singleCandidateChat.ask(7L, "What about Spring?", null, "tenant-a", history)).thenReturn(
                new CvChatResponse("Spring is listed [CV-7].", List.of(citation(7L))));
        when(conversations.appendTurn("conversation-1", 3, "What about Spring?",
                "Spring is listed [CV-7].", List.of(citation(7L)), actor)).thenReturn(conversationResponse(4));

        CvChatConversationTurnResponse response = service.ask(
                "conversation-1", " What about Spring? ", actor);

        assertThat(response.answer()).isEqualTo("Spring is listed [CV-7].");
        assertThat(response.citations()).singleElement().extracting(CvChatResponse.Citation::candidateId)
                .isEqualTo(7L);
        assertThat(response.version()).isEqualTo(4);
        verifyNoInteractions(comparisonChat);
    }

    @Test
    void comparesOnlyTheCandidateAndJobScopeLoadedFromTheConversation() {
        AppUserPrincipal actor = actor();
        when(conversations.executionScope("conversation-2", actor)).thenReturn(
                new CvChatConversationService.ExecutionScope(
                        "conversation-2", 5, "tenant-a",
                        List.of(candidate(7L, "Ada"), candidate(8L, "Grace")), 12L));
        when(conversations.history("conversation-2")).thenReturn(List.of());
        when(documentTools.answer("Who better matches it?",
                List.of(candidate(7L, "Ada"), candidate(8L, "Grace"))))
                .thenReturn(java.util.Optional.empty());
        when(comparisonChat.compare(List.of(7L, 8L), "Who better matches it?", 12L, "tenant-a", List.of()))
                .thenReturn(new CvChatComparisonResponse("Candidate 7 matches [CV-7] and 8 [CV-8].",
                        List.of(), List.of(citation(7L), citation(8L))));
        when(conversations.appendTurn("conversation-2", 5, "Who better matches it?",
                "Candidate 7 matches [CV-7] and 8 [CV-8].", List.of(citation(7L), citation(8L)), actor))
                .thenReturn(conversationResponse(6));

        CvChatConversationTurnResponse response = service.ask(
                "conversation-2", "Who better matches it?", actor);

        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(7L, 8L);
        verifyNoInteractions(singleCandidateChat);
    }

    @Test
    void passesThePersistedJobScopeToSingleCandidateChat() {
        AppUserPrincipal actor = actor();
        when(conversations.executionScope("conversation-3", actor)).thenReturn(
                new CvChatConversationService.ExecutionScope(
                        "conversation-3", 2, "tenant-a", List.of(candidate(7L, "Ada")), 12L));
        when(conversations.history("conversation-3")).thenReturn(List.of());
        when(documentTools.answer("Is the candidate suitable?", List.of(candidate(7L, "Ada"))))
                .thenReturn(java.util.Optional.empty());
        when(singleCandidateChat.ask(7L, "Is the candidate suitable?", 12L, "tenant-a", List.of()))
                .thenReturn(new CvChatResponse("The candidate matches [CV-7].", List.of(citation(7L))));
        when(conversations.appendTurn("conversation-3", 2, "Is the candidate suitable?",
                "The candidate matches [CV-7].", List.of(citation(7L)), actor))
                .thenReturn(conversationResponse(3));

        service.ask("conversation-3", "Is the candidate suitable?", actor);

        verify(singleCandidateChat).ask(7L, "Is the candidate suitable?", 12L, "tenant-a", List.of());
        verifyNoInteractions(comparisonChat);
    }

    @Test
    void persistsOperationalToolAnswerWithoutRunningCvRetrieval() {
        AppUserPrincipal actor = actor();
        when(conversations.executionScope("conversation-4", actor)).thenReturn(
                new CvChatConversationService.ExecutionScope(
                        "conversation-4", 4, "tenant-a", List.of(candidate(7L, "Ada Lovelace")), null));
        when(conversations.history("conversation-4")).thenReturn(List.of());
        when(documentTools.answer("Was this CV processed with OCR?", List.of(candidate(7L, "Ada Lovelace"))))
                .thenReturn(java.util.Optional.of(
                        "Ada Lovelace (CANDIDATE-7): Current document status is IMPORTED. "
                                + "Text extraction used OCR."));
        when(conversations.appendTurn("conversation-4", 4, "Was this CV processed with OCR?",
                "Ada Lovelace (CANDIDATE-7): Current document status is IMPORTED. Text extraction used OCR.",
                List.of(), actor)).thenReturn(conversationResponse(5));

        CvChatConversationTurnResponse response = service.ask(
                "conversation-4", "Was this CV processed with OCR?", actor);

        assertThat(response.answer()).contains("Text extraction used OCR");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(singleCandidateChat, comparisonChat);
    }

    @Test
    void persistsEvaluationHistoryWithoutRunningDocumentOrCvRetrieval() {
        AppUserPrincipal actor = actor();
        when(conversations.executionScope("conversation-5", actor)).thenReturn(
                new CvChatConversationService.ExecutionScope(
                        "conversation-5", 1, "tenant-a", List.of(candidate(7L, "Ada Lovelace")), null));
        when(conversations.history("conversation-5")).thenReturn(List.of());
        when(documentTools.answer("What was the latest evaluation?",
                List.of(candidate(7L, "Ada Lovelace"))))
                .thenReturn(java.util.Optional.of("Ada Lovelace (CANDIDATE-7): Persisted AI-assisted evaluation "
                        + "history: Evaluation 9 is CURRENT."));
        when(conversations.appendTurn("conversation-5", 1, "What was the latest evaluation?",
                "Ada Lovelace (CANDIDATE-7): Persisted AI-assisted evaluation history: Evaluation 9 is CURRENT.",
                List.of(), actor)).thenReturn(conversationResponse(2));

        CvChatConversationTurnResponse response = service.ask(
                "conversation-5", "What was the latest evaluation?", actor);

        assertThat(response.answer()).contains("Persisted AI-assisted evaluation history");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(singleCandidateChat, comparisonChat);
    }

    private CvChatConversationResponse conversationResponse(long version) {
        Instant now = Instant.parse("2026-11-11T22:00:00Z");
        return new CvChatConversationResponse("conversation-1", version,
                List.of(new CvChatConversationResponse.CandidateCard(7L, "Ada", "Engineer")),
                null, now, now, now.plusSeconds(3600));
    }

    private CvChatResponse.Citation citation(Long candidateId) {
        return new CvChatResponse.Citation("CV-" + candidateId, candidateId, candidateId, null, "Evidence");
    }

    private AppUserPrincipal actor() {
        return new AppUserPrincipal(1L, "owner@example.com", "hash", "tenant-a", true, List.of());
    }

    private CandidateIdentity candidate(Long id, String name) {
        return new CandidateIdentity(id, name, null);
    }
}
