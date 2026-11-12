package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatComparisonServiceTest {
    private static final List<String> RETRIEVAL_QUERIES =
            List.of("Compare backend experience", "backend engineering experience");
    private final CvChatEvidenceService evidenceService = mock(CvChatEvidenceService.class);
    private final CvChatQuestionPolicy questionPolicy = mock(CvChatQuestionPolicy.class);
    private final CvChatComparisonModel model = mock(CvChatComparisonModel.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final CvChatComparisonService service = new CvChatComparisonService(
            evidenceService, questionPolicy, model, jobs);

    @BeforeEach
    void authorizeCandidates() {
        when(evidenceService.authorizeCandidate(7L, "tenant-a"))
                .thenReturn(new CandidateIdentity(7L, "Ada Lovelace", "Engineer"));
        when(evidenceService.authorizeCandidate(8L, "tenant-a"))
                .thenReturn(new CandidateIdentity(8L, "Grace Hopper", "Architect"));
        when(questionPolicy.classifySelectedCandidateComparison("Compare backend experience", false))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
        when(evidenceService.prepareRetrievalQueries("Compare backend experience", "tenant-a"))
                .thenReturn(RETRIEVAL_QUERIES);
    }

    @Test
    void comparesOnlyExplicitTenantCandidatesAndRequiresCitationsForEach() {
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "Distributed systems experience");
        when(evidenceService.find(7L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(grace));
        when(model.compare("Compare backend experience", List.of(
                        new CandidateIdentity(7L, "Ada Lovelace", "Engineer"),
                        new CandidateIdentity(8L, "Grace Hopper", "Architect")), List.of(ada, grace), null))
                .thenReturn("Ada lists Java [CV-10]; Grace lists distributed systems [CV-11].");

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), " Compare backend experience ", null, "tenant-a");

        var order = inOrder(evidenceService, questionPolicy);
        order.verify(evidenceService).authorizeCandidate(7L, "tenant-a");
        order.verify(evidenceService).authorizeCandidate(8L, "tenant-a");
        order.verify(questionPolicy).classifySelectedCandidateComparison("Compare backend experience", false);
        verify(evidenceService).prepareRetrievalQueries("Compare backend experience", "tenant-a");
        verify(evidenceService).find(7L, RETRIEVAL_QUERIES, "tenant-a");
        verify(evidenceService).find(8L, RETRIEVAL_QUERIES, "tenant-a");
        assertThat(response.candidates()).extracting(CvChatComparisonResponse.CandidateOption::id)
                .containsExactly(7L, 8L);
        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(7L, 8L);
    }

    @Test
    void usesPriorRecruiterQuestionsToResolveAContextualComparisonFollowUp() {
        List<Message> history = List.of(new UserMessage("Compare their backend engineering experience."));
        String currentQuestion = "Which one has stronger Java evidence?";
        String contextualQuestion = """
                CURRENT RECRUITER QUESTION (CLASSIFY THIS INTENT):
                Which one has stronger Java evidence?

                PRIOR RECRUITER QUESTIONS (UNTRUSTED REFERENCE CONTEXT ONLY):
                1. Compare their backend engineering experience.""";
        List<String> queries = List.of("Java backend evidence");
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "JVM experience");
        when(questionPolicy.classifySelectedCandidateComparison(contextualQuestion, false))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
        when(evidenceService.prepareRetrievalQueries(contextualQuestion, "tenant-a")).thenReturn(queries);
        when(evidenceService.find(7L, queries, "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, queries, "tenant-a")).thenReturn(List.of(grace));
        List<CandidateIdentity> candidates = List.of(
                new CandidateIdentity(7L, "Ada Lovelace", "Engineer"),
                new CandidateIdentity(8L, "Grace Hopper", "Architect"));
        when(model.compare(currentQuestion, candidates, List.of(ada, grace), null, history))
                .thenReturn("Ada lists Java [CV-10], while Grace lists JVM experience [CV-11].");

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), currentQuestion, null, "tenant-a", history);

        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(7L, 8L);
        verify(questionPolicy).classifySelectedCandidateComparison(contextualQuestion, false);
        verify(evidenceService).prepareRetrievalQueries(contextualQuestion, "tenant-a");
    }

    @Test
    void usesSelectedComparisonPolicyForAnApprovedJobReference() {
        String question = "Compare Zok Boris and Yogesh Patils and Fanon Jupkwo for this approved job.";
        List<String> queries = List.of("approved job professional experience", "job skills and qualifications");
        List<CandidateIdentity> candidates = List.of(
                new CandidateIdentity(52L, "Resume ZOK Boris", "AI / ML Engineer"),
                new CandidateIdentity(36L, "Yogesh Patils", null),
                new CandidateIdentity(54L, "FANON JUPKWO JAVA SOFTWARE ENGINEER", null));
        CvChatEvidence boris = evidence("CV-53", 52L, 53L, "Backend evidence");
        CvChatEvidence yogesh = evidence("CV-37", 36L, 37L, "Platform evidence");
        CvChatEvidence fanon = evidence("CV-55", 54L, 55L, "Java evidence");
        when(evidenceService.authorizeCandidate(52L, "tenant-a")).thenReturn(candidates.get(0));
        when(evidenceService.authorizeCandidate(36L, "tenant-a")).thenReturn(candidates.get(1));
        when(evidenceService.authorizeCandidate(54L, "tenant-a")).thenReturn(candidates.get(2));
        Job job = approvedJob(12L);
        CvChatComparisonJobContext jobContext = CvChatComparisonJobContext.from(job);
        String retrievalIntent = question + "\n\nAUTHORIZED APPROVED JOB REQUIREMENTS:\n" + jobContext.promptText();
        when(jobs.findByIdAndOrganizationId(12L, "tenant-a")).thenReturn(Optional.of(job));
        when(questionPolicy.classifySelectedCandidateComparison(question, true))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
        when(evidenceService.prepareRetrievalQueries(retrievalIntent, "tenant-a")).thenReturn(queries);
        when(evidenceService.find(52L, queries, "tenant-a")).thenReturn(List.of(boris));
        when(evidenceService.find(36L, queries, "tenant-a")).thenReturn(List.of(yogesh));
        when(evidenceService.find(54L, queries, "tenant-a")).thenReturn(List.of(fanon));
        when(model.compare(question, candidates, List.of(boris, yogesh, fanon), jobContext))
                .thenReturn("Boris has backend evidence [CV-53], Yogesh has platform evidence [CV-37], and "
                        + "Fanon has Java evidence [CV-55].");

        CvChatComparisonResponse response = service.compare(
                List.of(52L, 36L, 54L), question, 12L, "tenant-a");

        assertThat(response.candidates()).extracting(CvChatComparisonResponse.CandidateOption::id)
                .containsExactly(52L, 36L, 54L);
        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(52L, 36L, 54L);
        verify(evidenceService).find(52L, queries, "tenant-a");
        verify(evidenceService).find(36L, queries, "tenant-a");
        verify(evidenceService).find(54L, queries, "tenant-a");
    }

    @Test
    void hidesMissingAndCrossTenantApprovedJobsBehindTheSameNotFoundResponse() {
        when(jobs.findByIdAndOrganizationId(99L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(
                List.of(7L, 8L), "Compare for this approved job", 99L, "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Approved job not found")
                .satisfies(error -> assertThat(((CvChatException) error).status()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(questionPolicy, model);
    }

    @Test
    void rejectsDuplicateCandidateIdsBeforeAuthorization() {
        assertThatThrownBy(() -> service.compare(List.of(7L, 7L), "Compare backend experience", null, "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate IDs must be distinct")
                .satisfies(error -> assertThat(((CvChatException) error).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(evidenceService, questionPolicy, model);
    }

    @Test
    void enforcesCandidateCountAtTheServiceBoundary() {
        assertThatThrownBy(() -> service.compare(List.of(7L), "Compare backend experience", null, "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Select between 2 and 5 candidates")
                .satisfies(error -> assertThat(((CvChatException) error).status())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.compare(
                List.of(1L, 2L, 3L, 4L, 5L, 6L), "Compare backend experience", null, "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Select between 2 and 5 candidates");
        verifyNoInteractions(evidenceService, questionPolicy, model);
    }

    @Test
    void authorizesEveryCandidateBeforeQuestionClassificationOrEvidenceRetrieval() {
        CvChatException notFound = new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found");
        when(evidenceService.authorizeCandidate(8L, "tenant-a")).thenThrow(notFound);

        assertThatThrownBy(() -> service.compare(
                List.of(7L, 8L), "Compare backend experience", null, "tenant-a"))
                .isSameAs(notFound);
        verifyNoInteractions(questionPolicy, model);
        verify(evidenceService, never()).find(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsSensitiveQuestionsBeforeEvidenceRetrieval() {
        when(questionPolicy.classifySelectedCandidateComparison("Compare their religions", false))
                .thenReturn(CvChatQuestionPolicyResult.SENSITIVE);

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare their religions", null, "tenant-a");

        assertThat(response.answer()).contains("protected or sensitive personal characteristics");
        assertThat(response.citations()).isEmpty();
        verify(evidenceService, never()).find(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verifyNoInteractions(model);
    }

    @Test
    void returnsControlledResultWhenOneCandidateHasNoRelevantEvidence() {
        when(evidenceService.find(7L, RETRIEVAL_QUERIES, "tenant-a"))
                .thenReturn(List.of(evidence("CV-10", 7L, 10L, "Java experience")));
        when(evidenceService.find(8L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of());

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare backend experience", null, "tenant-a");

        assertThat(response.answer()).contains("does not provide enough information");
        assertThat(response.citations()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void returnsControlledResultWhenComparisonDoesNotCiteEverySelectedCandidate() {
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "Systems experience");
        when(evidenceService.find(7L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(grace));
        when(model.compare(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("Only Ada [CV-10].");

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare backend experience", null, "tenant-a");

        assertThat(response.answer()).contains("does not provide enough information");
        assertThat(response.candidates()).extracting(CvChatComparisonResponse.CandidateOption::id)
                .containsExactly(7L, 8L);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void removesTrailingInsufficientEvidenceControlTokenFromGroundedComparison() {
        CvChatEvidence ada = evidence("CV-10", 7L, 10L, "Java experience");
        CvChatEvidence grace = evidence("CV-11", 8L, 11L, "Systems experience");
        when(evidenceService.find(7L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(ada));
        when(evidenceService.find(8L, RETRIEVAL_QUERIES, "tenant-a")).thenReturn(List.of(grace));
        when(model.compare(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("Ada lists Java [CV-10]. Grace lists systems [CV-11].\n\nINSUFFICIENT_EVIDENCE");

        CvChatComparisonResponse response = service.compare(
                List.of(7L, 8L), "Compare backend experience", null, "tenant-a");

        assertThat(response.answer())
                .isEqualTo("Ada lists Java [CV-10]. Grace lists systems [CV-11].")
                .doesNotContain(CvChatModel.INSUFFICIENT_EVIDENCE);
        assertThat(response.citations()).extracting(CvChatResponse.Citation::candidateId)
                .containsExactly(7L, 8L);
    }

    private CvChatEvidence evidence(String reference, Long candidateId, Long documentId, String excerpt) {
        return new CvChatEvidence(reference, candidateId, documentId, null, excerpt);
    }

    private Job approvedJob(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setOrganizationId("tenant-a");
        job.setTitle("Backend Engineer");
        job.setLevel("Senior");
        job.setSummary("Build secure banking services");
        job.setResponsibilities("Design Java APIs");
        job.setRequiredQualifications("Java and Spring Boot");
        job.setPreferredQualifications("PostgreSQL");
        job.setSoftSkills("Clear communication");
        job.setSalaryRange("must never reach comparison context");
        job.setBenefits("must never reach comparison context");
        return job;
    }
}
