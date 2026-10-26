package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvChatServiceTest {
    private final CvChatEvidenceService evidenceService = mock(CvChatEvidenceService.class);
    private final CvChatModel model = mock(CvChatModel.class);
    private final CvChatService service = new CvChatService(evidenceService, model);

    @Test
    void answersFromTenantScopedEvidenceAndReturnsOnlyCitedSources() {
        CvChatEvidence evidence = new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null,
                "Java and Spring Boot engineer with PostgreSQL experience.");
        when(evidenceService.find(7L, "tenant-a")).thenReturn(List.of(evidence));
        when(model.answer(org.mockito.ArgumentMatchers.eq("Does this candidate know Java?"),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("The CV lists Java and Spring Boot experience [CV-CANDIDATE-7].");

        CvChatResponse response = service.ask(7L, " Does this candidate know Java? ", "tenant-a");

        assertThat(response.answer()).contains("[CV-CANDIDATE-7]");
        assertThat(response.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.candidateId()).isEqualTo(7L);
            assertThat(citation.reference()).isEqualTo("CV-CANDIDATE-7");
            assertThat(citation.excerpt()).contains("PostgreSQL");
        });
    }

    @Test
    void rejectsProviderCitationsThatWereNotInAuthorizedEvidence() {
        when(evidenceService.find(7L, "tenant-a")).thenReturn(List.of(
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
        stubEvidence();
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("  INSUFFICIENT_EVIDENCE  ");

        CvChatResponse response = service.ask(7L, "Does the candidate know Kubernetes?", "tenant-a");

        assertThat(response.answer())
                .isEqualTo("The available CV evidence does not provide enough information to answer this question.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void returnsBackendControlledMessageForOutOfScopeOrSensitiveQuestions() {
        stubEvidence();
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("OUT_OF_SCOPE");

        CvChatResponse response = service.ask(7L, "What is the candidate's political affiliation?", "tenant-a");

        assertThat(response.answer()).isEqualTo(
                "I can only answer job-relevant questions supported by the authorized CV evidence. "
                        + "I cannot provide or infer protected or sensitive personal characteristics.");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void stillRejectsArbitraryUncitedProviderText() {
        stubEvidence();
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn("This is an uncited provider answer.");

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider returned an answer without evidence citations")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    @Test
    void hidesProviderFailuresBehindAStableApiError() {
        when(evidenceService.find(7L, "tenant-a")).thenReturn(List.of(
                new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null, "Java engineer")));
        when(model.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new IllegalStateException("provider response contained internal details"));

        assertThatThrownBy(() -> service.ask(7L, "Summarize", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider is unavailable")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    private void stubEvidence() {
        when(evidenceService.find(7L, "tenant-a")).thenReturn(List.of(
                new CvChatEvidence("CV-CANDIDATE-7", 7L, null, null, "Java engineer")));
    }

}
