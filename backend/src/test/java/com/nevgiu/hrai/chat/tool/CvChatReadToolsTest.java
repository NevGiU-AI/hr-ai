package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvOriginalAccessException;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
import com.nevgiu.hrai.chat.CvChatCandidateSearchResponse;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.chat.CvChatOrganizationDiscoveryService;
import com.nevgiu.hrai.evaluation.EvaluationHistoryService;
import com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvChatReadToolsTest {
    private final CvChatCandidateResolutionService candidateResolution =
            mock(CvChatCandidateResolutionService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final EvaluationHistoryService evaluationHistory = mock(EvaluationHistoryService.class);
    private final CvChatToolExecutionGuard executionGuard = mock(CvChatToolExecutionGuard.class);
    private final CvChatToolScope scope = mock(CvChatToolScope.class);
    private final CvChatToolDiscoveryState discoveryState = mock(CvChatToolDiscoveryState.class);
    private final CvChatToolPlanningState planningState = mock(CvChatToolPlanningState.class);
    private final CvChatOrganizationDiscoveryService organizationDiscovery =
            mock(CvChatOrganizationDiscoveryService.class);
    private final CvChatReadTools tools = new CvChatReadTools(
            candidateResolution, candidates, documents, jobs, evaluationHistory, executionGuard, scope, discoveryState,
            planningState, organizationDiscovery);

    @BeforeEach
    void executeGuardedActionsSynchronously() {
        when(executionGuard.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        when(executionGuard.executeSemanticDiscovery(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesOnlyTheAuthenticatedOrganizationAndReturnsMinimalCards() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        when(candidateResolution.search("Ada", "tenant-a")).thenReturn(new CvChatCandidateSearchResponse(
                List.of(new CvChatCandidateSearchResponse.CandidateOption(7L, "Ada Lovelace", "Engineer")), false));

        CandidateSearchResult result = tools.searchCandidates("Ada");

        assertThat(result).isEqualTo(new CandidateSearchResult(
                List.of(new CandidateCard(7L, "Ada Lovelace", "Engineer")), false));
        verify(candidateResolution).search("Ada", "tenant-a");
        verify(discoveryState).record("Ada", 1);
    }

    @Test
    void searchesEvidenceOnlyInsideTheAuthenticatedOrganization() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        CandidateEvidenceSearchResult expected = new CandidateEvidenceSearchResult(
                List.of(new CandidateEvidenceCard(7L, "Ada Lovelace", "Engineer", List.of("CV-12"))), false);
        when(organizationDiscovery.discover("Java and Spring experience", "tenant-a")).thenReturn(expected);

        CandidateEvidenceSearchResult result = tools.searchCandidatesByEvidence("Java and Spring experience");

        assertThat(result).isEqualTo(expected);
        verify(organizationDiscovery).discover("Java and Spring experience", "tenant-a");
    }

    @Test
    void returnsMinimizedCurrentCandidateAndDocumentState() {
        authenticate("tenant-a", "ROLE_REVIEWER");
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setName("Ada Lovelace");
        candidate.setEmail("must-not-be-returned@example.test");
        candidate.setCurrentTitle("Engineer");
        candidate.setYearsExperience(8);
        candidate.setCvText("raw CV evidence must not be returned");
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(12L);
        when(document.getStatus()).thenReturn(CvIngestionStatus.IMPORTED);
        when(document.getExtractionMethod()).thenReturn(CvExtractionMethod.NATIVE);
        when(document.getImportedAt()).thenReturn(Instant.parse("2026-11-03T00:00:00Z"));
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(document));

        CandidateSummary result = tools.getCandidateSummary(7L);

        verify(scope).requireConfirmed(7L);
        verify(planningState).recordCandidateSummary(7L);
        assertThat(result).isEqualTo(new CandidateSummary(7L, "Ada Lovelace", "Engineer", 8,
                new CurrentDocumentStatus(12L, CvIngestionStatus.IMPORTED, CvExtractionMethod.NATIVE,
                        Instant.parse("2026-11-03T00:00:00Z"), null)));
        assertThat(CandidateSummary.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("email", "location", "cvText", "organizationId", "storageKey", "sha256");
    }

    @Test
    void makesMissingAndCrossTenantCandidateIdsIndistinguishable() {
        authenticate("tenant-a", "ROLE_ADMIN");
        when(candidates.findByIdAndOrganizationId(99L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.getCandidateSummary(99L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found");
        verify(scope).requireConfirmed(99L);
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsCandidateOutsideTheHumanConfirmedScopeBeforeDataAccess() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        org.mockito.Mockito.doThrow(new CvChatException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Candidate is outside the confirmed chat scope"))
                .when(scope).requireConfirmed(7L);

        assertThatThrownBy(() -> tools.getCandidateSummary(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate is outside the confirmed chat scope");
        verifyNoInteractions(candidates, documents);
    }

    @Test
    void returnsOnlyBoundedRequirementsForTheTenantAuthorizedConfirmedJob() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        Job job = new Job();
        job.setId(21L);
        job.setOrganizationId("must-not-be-returned");
        job.setTitle("Backend Engineer");
        job.setLevel("Senior");
        job.setSummary("Build reliable services");
        job.setResponsibilities("Design APIs");
        job.setRequiredQualifications("Java and Spring Boot");
        job.setPreferredQualifications("PostgreSQL");
        job.setSoftSkills("Clear communication");
        job.setSalaryRange("must-not-be-returned");
        job.setBenefits("must-not-be-returned");
        when(jobs.findByIdAndOrganizationId(21L, "tenant-a")).thenReturn(Optional.of(job));

        JobRequirements result = tools.getJobRequirements(21L);

        verify(scope).requireConfirmedJob(21L);
        verify(planningState).recordJobRequirements(21L);
        assertThat(result).isEqualTo(new JobRequirements(21L, "Backend Engineer", "Senior",
                "Build reliable services", "Design APIs", "Java and Spring Boot", "PostgreSQL",
                "Clear communication"));
        assertThat(JobRequirements.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("organizationId", "salaryRange", "benefits", "tone");
    }

    @Test
    void rejectsJobsOutsideTheHumanConfirmedScopeBeforeDataAccess() {
        authenticate("tenant-a", "ROLE_REVIEWER");
        org.mockito.Mockito.doThrow(new CvChatException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Approved job is outside the confirmed chat scope"))
                .when(scope).requireConfirmedJob(21L);

        assertThatThrownBy(() -> tools.getJobRequirements(21L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Approved job is outside the confirmed chat scope");
        verifyNoInteractions(jobs);
    }

    @Test
    void boundsLongJobRequirementFieldsAndHidesCrossTenantExistence() {
        authenticate("tenant-a", "ROLE_ADMIN");
        Job job = new Job();
        job.setId(21L);
        job.setResponsibilities("x".repeat(2_100));
        when(jobs.findByIdAndOrganizationId(21L, "tenant-a")).thenReturn(Optional.of(job));

        assertThat(tools.getJobRequirements(21L).responsibilities()).hasSize(2_000);

        when(jobs.findByIdAndOrganizationId(22L, "tenant-a")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tools.getJobRequirements(22L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Approved job not found");
        verify(scope).requireConfirmedJob(21L);
        verify(scope).requireConfirmedJob(22L);
    }

    @Test
    void returnsOnlyBoundedPersistedEvaluationHistoryForTheConfirmedCandidate() {
        authenticate("tenant-a", "ROLE_REVIEWER");
        Instant staleAt = Instant.parse("2026-11-01T09:00:00Z");
        List<EvaluationEvidenceSnapshot> history = List.of(
                evaluation(6L, "x".repeat(400), null),
                evaluation(5L, "Backend Engineer", null),
                evaluation(4L, "Platform Engineer", staleAt),
                evaluation(3L, "API Engineer", staleAt),
                evaluation(2L, "Java Engineer", staleAt),
                evaluation(1L, "Legacy Engineer", staleAt));
        when(evaluationHistory.findRecentEvidenceForCandidate(7L, "tenant-a", 6)).thenReturn(history);

        EvaluationEvidenceResult result = tools.getEvaluationEvidence(7L);

        verify(scope).requireConfirmed(7L);
        verify(evaluationHistory).findRecentEvidenceForCandidate(7L, "tenant-a", 6);
        assertThat(result.candidateId()).isEqualTo(7L);
        assertThat(result.evaluations()).hasSize(5);
        assertThat(result.truncated()).isTrue();
        assertThat(result.evaluations().getFirst().jobTitle()).hasSize(255);
        assertThat(result.evaluations().getFirst().current()).isTrue();
        assertThat(result.evaluations().get(2).invalidatedAt()).isEqualTo(staleAt);
        assertThat(result.evaluations().getFirst().scores()).isEqualTo(
                new EvaluationScores(90, 8, 7, 8, 75, 9, 8, 88, 82));
        assertThat(EvaluationEvidenceItem.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("candidateName", "organizationId", "email", "cvText", "storageKey", "sha256",
                        "prompt", "model", "provider", "aiExplanation", "modelGeneratedExplanation");
    }

    @Test
    void rejectsEvaluationEvidenceOutsideTheConfirmedScopeBeforeHistoryAccess() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        org.mockito.Mockito.doThrow(new CvChatException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Candidate is outside the confirmed chat scope"))
                .when(scope).requireConfirmed(7L);

        assertThatThrownBy(() -> tools.getEvaluationEvidence(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate is outside the confirmed chat scope");
        verifyNoInteractions(evaluationHistory);
    }

    @Test
    void keepsMissingAndCrossTenantEvaluationCandidatesIndistinguishable() {
        authenticate("tenant-a", "ROLE_ADMIN");
        when(evaluationHistory.findRecentEvidenceForCandidate(99L, "tenant-a", 6))
                .thenThrow(new CvOriginalAccessException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Candidate not found"));

        assertThatThrownBy(() -> tools.getEvaluationEvidence(99L))
                .isInstanceOf(CvOriginalAccessException.class)
                .hasMessage("Candidate not found");
        verify(scope).requireConfirmed(99L);
    }

    @Test
    void returnsOnlyMinimizedStatusForTheConfirmedCandidatesNewestDocument() {
        authenticate("tenant-a", "ROLE_REVIEWER");
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(12L);
        when(document.getStatus()).thenReturn(CvIngestionStatus.NEEDS_REVIEW);
        when(document.getExtractionMethod()).thenReturn(CvExtractionMethod.OCR);
        when(document.getImportedAt()).thenReturn(Instant.parse("2026-11-10T01:00:00Z"));
        when(document.getReprocessedAt()).thenReturn(Instant.parse("2026-11-10T02:00:00Z"));
        CvDocument olderDocument = mock(CvDocument.class);
        when(olderDocument.getId()).thenReturn(11L);
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        // The repository contract orders newest first; the tool must expose only that current processing state.
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of(document, olderDocument));

        DocumentStatusResult result = tools.getDocumentStatus(7L);

        verify(scope).requireConfirmed(7L);
        assertThat(result).isEqualTo(new DocumentStatusResult(7L,
                new CurrentDocumentStatus(12L, CvIngestionStatus.NEEDS_REVIEW, CvExtractionMethod.OCR,
                        Instant.parse("2026-11-10T01:00:00Z"), Instant.parse("2026-11-10T02:00:00Z"))));
        assertThat(DocumentStatusResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("organizationId", "filename", "ingestionError", "cvText", "storageKey", "sha256");
    }

    @Test
    void returnsApplicationOwnedWarningsWithoutRawIngestionErrors() {
        authenticate("tenant-a", "ROLE_ADMIN");
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(12L);
        when(document.getStatus()).thenReturn(CvIngestionStatus.NEEDS_REVIEW);
        when(document.getIngestionError()).thenReturn("provider secret and parser stack trace");
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(document));

        IngestionWarningsResult result = tools.getIngestionWarnings(7L);

        verify(scope).requireConfirmed(7L);
        assertThat(result).isEqualTo(new IngestionWarningsResult(
                7L, 12L, List.of(IngestionWarning.MANUAL_REVIEW_REQUIRED)));
        assertThat(result.toString()).doesNotContain("provider secret", "stack trace");
    }

    @Test
    void reportsNoDocumentWithoutMakingTheCandidateLookMissing() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of());

        assertThat(tools.getDocumentStatus(7L)).isEqualTo(new DocumentStatusResult(7L, null));
        assertThat(tools.getIngestionWarnings(7L)).isEqualTo(new IngestionWarningsResult(
                7L, null, List.of(IngestionWarning.NO_DOCUMENT_AVAILABLE)));
    }

    @Test
    void rejectsDocumentToolsOutsideConfirmedScopeBeforeDataAccess() {
        authenticate("tenant-a", "ROLE_RECRUITER");
        org.mockito.Mockito.doThrow(new CvChatException(org.springframework.http.HttpStatus.FORBIDDEN,
                        "Candidate is outside the confirmed chat scope"))
                .when(scope).requireConfirmed(7L);

        assertThatThrownBy(() -> tools.getDocumentStatus(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate is outside the confirmed chat scope");
        assertThatThrownBy(() -> tools.getIngestionWarnings(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate is outside the confirmed chat scope");
        verifyNoInteractions(candidates, documents);
    }

    @Test
    void keepsMissingAndCrossTenantDocumentCandidatesIndistinguishable() {
        authenticate("tenant-a", "ROLE_ADMIN");
        when(candidates.findByIdAndOrganizationId(99L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.getDocumentStatus(99L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found");
        assertThatThrownBy(() -> tools.getIngestionWarnings(99L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found");
        verify(scope, org.mockito.Mockito.times(2)).requireConfirmed(99L);
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsReadOnlyAndUnauthenticatedCallsBeforeDataAccess() {
        authenticate("tenant-a", "ROLE_READ_ONLY");

        assertThatThrownBy(() -> tools.searchCandidates("Ada"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat access denied");
        assertThatThrownBy(() -> tools.getEvaluationEvidence(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat access denied");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> tools.getCandidateSummary(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Authentication required");
        assertThatThrownBy(() -> tools.getEvaluationEvidence(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Authentication required");
        verifyNoInteractions(candidateResolution, candidates, documents, evaluationHistory, scope);
    }

    @Test
    void exposesOnlyReviewedReadToolNames() throws Exception {
        Method search = CvChatReadTools.class.getMethod("searchCandidates", String.class);
        Method evidenceSearch = CvChatReadTools.class.getMethod("searchCandidatesByEvidence", String.class);
        Method summary = CvChatReadTools.class.getMethod("getCandidateSummary", Long.class);
        Method requirements = CvChatReadTools.class.getMethod("getJobRequirements", Long.class);
        Method evaluationEvidence = CvChatReadTools.class.getMethod("getEvaluationEvidence", Long.class);
        Method documentStatus = CvChatReadTools.class.getMethod("getDocumentStatus", Long.class);
        Method ingestionWarnings = CvChatReadTools.class.getMethod("getIngestionWarnings", Long.class);

        assertThat(search.getAnnotation(Tool.class).name()).isEqualTo("search_candidates");
        assertThat(evidenceSearch.getAnnotation(Tool.class).name()).isEqualTo("search_candidates_by_evidence");
        assertThat(summary.getAnnotation(Tool.class).name()).isEqualTo("get_candidate_summary");
        assertThat(requirements.getAnnotation(Tool.class).name()).isEqualTo("get_job_requirements");
        assertThat(evaluationEvidence.getAnnotation(Tool.class).name()).isEqualTo("get_evaluation_evidence");
        assertThat(documentStatus.getAnnotation(Tool.class).name()).isEqualTo("get_document_status");
        assertThat(ingestionWarnings.getAnnotation(Tool.class).name()).isEqualTo("get_ingestion_warnings");
        assertThat(CvChatReadTools.class.getDeclaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(Tool.class))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("searchCandidates", "searchCandidatesByEvidence", "getCandidateSummary",
                        "getJobRequirements", "getEvaluationEvidence", "getDocumentStatus",
                        "getIngestionWarnings");
    }

    private EvaluationEvidenceSnapshot evaluation(Long id, String jobTitle, Instant invalidatedAt) {
        return new EvaluationEvidenceSnapshot(id, 100L + id, jobTitle,
                90, 8, 7, 8, 75, 9, 8, 88, 82,
                Instant.parse("2026-11-01T08:00:00Z"), invalidatedAt, invalidatedAt == null);
    }

    private void authenticate(String organizationId, String role) {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "reviewer@example.test", "", organizationId, true,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
