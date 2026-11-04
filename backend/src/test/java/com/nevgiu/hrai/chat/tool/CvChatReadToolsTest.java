package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
import com.nevgiu.hrai.chat.CvChatCandidateSearchResponse;
import com.nevgiu.hrai.chat.CvChatException;
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
    private final CvChatToolExecutionGuard executionGuard = mock(CvChatToolExecutionGuard.class);
    private final CvChatToolScope scope = mock(CvChatToolScope.class);
    private final CvChatReadTools tools = new CvChatReadTools(
            candidateResolution, candidates, documents, jobs, executionGuard, scope);

    @BeforeEach
    void executeGuardedActionsSynchronously() {
        when(executionGuard.execute(any())).thenAnswer(invocation ->
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
    void rejectsReadOnlyAndUnauthenticatedCallsBeforeDataAccess() {
        authenticate("tenant-a", "ROLE_READ_ONLY");

        assertThatThrownBy(() -> tools.searchCandidates("Ada"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat access denied");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> tools.getCandidateSummary(7L))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Authentication required");
        verifyNoInteractions(candidateResolution, candidates, documents);
    }

    @Test
    void exposesOnlyReviewedReadToolNames() throws Exception {
        Method search = CvChatReadTools.class.getMethod("searchCandidates", String.class);
        Method summary = CvChatReadTools.class.getMethod("getCandidateSummary", Long.class);
        Method requirements = CvChatReadTools.class.getMethod("getJobRequirements", Long.class);

        assertThat(search.getAnnotation(Tool.class).name()).isEqualTo("search_candidates");
        assertThat(summary.getAnnotation(Tool.class).name()).isEqualTo("get_candidate_summary");
        assertThat(requirements.getAnnotation(Tool.class).name()).isEqualTo("get_job_requirements");
        assertThat(CvChatReadTools.class.getDeclaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(Tool.class))
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("searchCandidates", "getCandidateSummary", "getJobRequirements");
    }

    private void authenticate(String organizationId, String role) {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "reviewer@example.test", "", organizationId, true,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
