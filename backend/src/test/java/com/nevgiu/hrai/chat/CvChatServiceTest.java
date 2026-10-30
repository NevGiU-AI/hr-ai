package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatServiceTest {
    private final CvChatEvidenceService evidenceService = mock(CvChatEvidenceService.class);
    private final CvChatQuestionPolicy questionPolicy = mock(CvChatQuestionPolicy.class);
    private final CvChatModel model = mock(CvChatModel.class);
    private final CvChatService service = new CvChatService(evidenceService, questionPolicy, model);

    CvChatServiceTest() {
        when(questionPolicy.classify(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
    }

    @Test
    void answersFromTenantScopedEvidenceAndReturnsOnlyCitedSources() {
        CvChatEvidence evidence = new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null,
                "Java and Spring Boot engineer with PostgreSQL experience.");
        when(evidenceService.find(7L, "Does this candidate know Java?", "tenant-a")).thenReturn(List.of(evidence));
        when(model.answer(org.mockito.ArgumentMatchers.eq("Does this candidate know Java?"),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("The CV lists Java and Spring Boot experience [CV-CANDIDATE-7].");

        CvChatResponse response = service.ask(7L, " Does this candidate know Java? ", "tenant-a");

        var order = inOrder(evidenceService, questionPolicy);
        order.verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        order.verify(questionPolicy).classify("Does this candidate know Java?");
        order.verify(evidenceService).find(7L, "Does this candidate know Java?", "tenant-a");
        assertThat(response.answer()).contains("[CV-CANDIDATE-7]");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.candidateId()).isEqualTo(7L);
            assertThat(citation.reference()).isEqualTo("CV-CANDIDATE-7");
            assertThat(citation.excerpt()).contains("PostgreSQL");
        });
    }

    @Test
    void rejectsProviderCitationsThatWereNotInAuthorizedEvidence() {
        when(evidenceService.find(7L, "Summarize", "tenant-a")).thenReturn(List.of(
                new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null, "Java engineer")));
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("Unsupported claim [CV-CANDIDATE-999].");

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider returned an unsupported citation")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    @Test
    void returnsBackendControlledMessageWhenJobRelevantEvidenceIsInsufficient() {
        stubEvidence("Does the candidate know Kubernetes?");
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("  INSUFFICIENT_EVIDENCE  ");

        CvChatResponse response = service.ask(7L, "Does the candidate know Kubernetes?", "tenant-a");

        assertThat(response.answer())
                .isEqualTo("The available CV evidence does not provide enough information to answer this question.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void returnsBackendControlledMessageForOutOfScopeOrSensitiveQuestions() {
        stubEvidence("What is the candidate's political affiliation?");
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("OUT_OF_SCOPE");

        CvChatResponse response = service.ask(7L, "What is the candidate's political affiliation?", "tenant-a");

        assertThat(response.answer()).isEqualTo(
                "I can only answer job-relevant questions supported by the authorized CV evidence. "
                        + "I cannot provide or infer protected or sensitive personal characteristics.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void rejectsSensitiveQuestionsBeforeCandidateEvidenceRetrieval() {
        when(questionPolicy.classify("What is his religion?"))
                .thenReturn(CvChatQuestionPolicyResult.SENSITIVE);

        CvChatResponse response = service.ask(7L, " What is his religion? ", "tenant-a");

        assertThat(response.answer()).isEqualTo(
                "I can only answer job-relevant questions supported by the authorized CV evidence. "
                        + "I cannot provide or infer protected or sensitive personal characteristics.");
        assertThat(response.citations()).isEmpty();
        verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        verify(evidenceService, never()).find(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(model);
    }

    @Test
    void rejectsUnrelatedQuestionsBeforeCandidateEvidenceRetrieval() {
        when(questionPolicy.classify("What is the weather?"))
                .thenReturn(CvChatQuestionPolicyResult.OUT_OF_SCOPE);

        CvChatResponse response = service.ask(7L, "What is the weather?", "tenant-a");

        assertThat(response.citations()).isEmpty();
        verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        verify(evidenceService, never()).find(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(model);
    }

    @Test
    void failsClosedWhenQuestionPolicyIsUnavailable() {
        when(questionPolicy.classify("Summarize"))
                .thenThrow(new IllegalStateException("sensitive provider detail"));

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat question policy is unavailable")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
        verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        verify(evidenceService, never()).find(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(model);
    }

    @Test
    void preservesTenantScopedNotFoundBeforeQuestionClassification() {
        CvChatException notFound = new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found");
        doThrow(notFound).when(evidenceService).authorizeCandidate(99L, "tenant-a");

        assertThatThrownBy(() -> service.ask(99L, "What is his religion?", "tenant-a"))
                .isSameAs(notFound);
        verifyNoInteractions(questionPolicy, model);
    }

    @Test
    void stillRejectsArbitraryUncitedProviderText() {
        stubEvidence("Summarize");
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("This is an uncited provider answer.");

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider returned an answer without evidence citations")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    @Test
    void hidesProviderFailuresBehindAStableApiError() {
        when(evidenceService.find(7L, "Summarize", "tenant-a")).thenReturn(List.of(
                new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null, "Java engineer")));
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new IllegalStateException("provider response contained internal details"));

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider is unavailable")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    @Test
    void returnsControlledInsufficientEvidenceWithoutCallingTheModelWhenRetrievalFindsNothing() {
        when(evidenceService.find(7L, "Kubernetes", "tenant-a")).thenReturn(List.of());

        CvChatResponse response = service.ask(7L, " Kubernetes ", "tenant-a");

        assertThat(response.answer())
                .isEqualTo("The available CV evidence does not provide enough information to answer this question.");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(model);
        verify(questionPolicy).classify("Kubernetes");
        verify(evidenceService).find(7L, "Kubernetes", "tenant-a");
    }

    private void stubEvidence(String question) {
        when(evidenceService.find(7L, question, "tenant-a")).thenReturn(List.of(
                new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null, "Java engineer")));
    }

}
