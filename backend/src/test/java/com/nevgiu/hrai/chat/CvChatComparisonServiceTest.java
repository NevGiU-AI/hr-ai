package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatComparisonServiceTest {
    private final CvChatEvidenceService evidenceService = mock(CvChatEvidenceService.class);
    private final CvChatQuestionPolicy questionPolicy = mock(CvChatQuestionPolicy.class);
    private final CvChatComparisonModel model = mock(CvChatComparisonModel.class);
    private final CvChatComparisonService service = new CvChatComparisonService(
            evidenceService, questionPolicy, model);

    @BeforeEach
    void authorizeCandidates() {
        when(evidenceService.authorizeCandidate(7L, "tenant-a"))
                .thenReturn(new CandidateIdentity(7L, "Ada Lovelace", "Engineer"));
        when(evidenceService.authorizeCandidate(8L, "tenant-a"))
                .thenReturn(new CandidateIdentity(8L, "Grace Hopper", "Architect"));
        when(questionPolicy.classify("Compare backend experience"))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
    }

    @Test
    void comparesOnlyExplicitTenantCandidatesAndRequiresCitationsForEach() {
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "Distributed systems experience");
        when(evidenceService.find(7L, "Compare backend experience", "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, "Compare backend experience", "tenant-a")).thenReturn(List.of(grace));
        when(model.compare("Compare backend experience", List.of(
                        new CandidateIdentity(7L, "Ada Lovelace", "Engineer"),
                        new CandidateIdentity(8L, "Grace Hopper", "Architect")), List.of(ada, grace)))
                .thenReturn("Ada lists Java [CV-10]; Grace lists distributed systems [CV-11].");

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), " Compare backend experience ", "tenant-a");

        var order = inOrder(evidenceService, questionPolicy);
        order.verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        order.verify(evidenceService).authorizeCandidate(8L, "tenant-a");
        order.verify(questionPolicy).classify("Compare backend experience");
        assertThat(response.candidates()).extracting(CvChatComparisonResponse.CandidateOption::id)
                .containsExactly(7L, 8L);
        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(7L, 8L);
    }

    @Test
    void rejectsDuplicateCandidateIdsBeforeAuthorization() {
        assertThatThrownBy(() -> service.compare(List.of(7L, 7L), "Compare backend experience", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate IDs must be distinct")
                .satisfies(error -> assertThat(((CvChatException) error).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(evidenceService, questionPolicy, model);
    }

    @Test
    void enforcesCandidateCountAtTheServiceBoundary() {
        assertThatThrownBy(() -> service.compare(List.of(7L), "Compare backend experience", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Select between 2 and 5 candidates")
                .satisfies(error -> assertThat(((CvChatException) error).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.compare(
                List.of(1L, 2L, 3L, 4L, 5L, 6L), "Compare backend experience", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Select between 2 and 5 candidates");
        verifyNoInteractions(evidenceService, questionPolicy, model);
    }

    @Test
    void authorizesEveryCandidateBeforeQuestionClassificationOrEvidenceRetrieval() {
        CvChatException notFound = new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found");
        when(evidenceService.authorizeCandidate(8L, "tenant-a")).thenThrow(notFound);

        assertThatThrownBy(() -> service.compare(List.of(7L, 8L), "Compare backend experience", "tenant-a"))
                .isSameAs(notFound);
        verifyNoInteractions(questionPolicy, model);
        verify(evidenceService, never()).find(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsSensitiveQuestionsBeforeEvidenceRetrieval() {
        when(questionPolicy.classify("Compare their religions"))
                .thenReturn(CvChatQuestionPolicyResult.SENSITIVE);

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare their religions", "tenant-a");

        assertThat(response.answer()).contains("protected or sensitive personal characteristics");
        assertThat(response.citations()).isEmpty();
        verify(evidenceService, never()).find(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(model);
    }

    @Test
    void returnsControlledResultWhenOneCandidateHasNoRelevantEvidence() {
        when(evidenceService.find(7L, "Compare backend experience", "tenant-a"))
                .thenReturn(List.of(evidence("CV-10", 7L, 10L, "Java experience")));
        when(evidenceService.find(8L, "Compare backend experience", "tenant-a")).thenReturn(List.of());

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare backend experience", "tenant-a");

        assertThat(response.answer()).contains("does not provide enough information");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void rejectsAComparisonThatDoesNotCiteEverySelectedCandidate() {
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "Systems experience");
        when(evidenceService.find(7L, "Compare backend experience", "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, "Compare backend experience", "tenant-a")).thenReturn(List.of(grace));
        when(model.compare(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn("Only Ada [CV-10].");

        assertThatThrownBy(() -> service.compare(
                List.of(7L, 8L), "Compare backend experience", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat provider returned a comparison without evidence for every candidate");
    }

    private CvChatEvidence evidence(String reference, Long candidateId, Long documentId, String excerpt) {
        return new CvChatEvidence(reference, candidateId, documentId, null, excerpt);
    }
}
