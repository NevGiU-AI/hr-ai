package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatReadTools;
import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;
import com.nevgiu.hrai.chat.tool.CvChatToolExecutionGuard;
import com.nevgiu.hrai.chat.tool.CvChatToolPlanningState;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.evaluation.EvaluationHistoryService;
import com.nevgiu.hrai.evaluation.dto.EvaluationEvidenceSnapshot;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the actual Spring AI method callbacks and request-local postcondition state without a billable model. */
class CvChatToolOrchestrationCallbackIntegrationTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dispatchesFiveCandidateAndOneJobCallbacksBeforeReturningReady() throws Exception {
        authenticate();
        CandidateRepository candidates = mock(CandidateRepository.class);
        CvDocumentRepository documents = mock(CvDocumentRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        CvChatEvidenceService evidence = mock(CvChatEvidenceService.class);
        CvChatQuestionPolicy policy = mock(CvChatQuestionPolicy.class);
        when(policy.classify("Compare the confirmed candidates for the selected job"))
                .thenReturn(CvChatQuestionPolicyResult.JOB_RELEVANT);
        when(candidates.findByIdAndOrganizationId(anyLong(), eq("tenant-a")))
                .thenAnswer(invocation -> Optional.of(candidate(invocation.<Long>getArgument(0))));
        when(documents.findAllByCandidateIdAndOrganizationId(anyLong(), eq("tenant-a"))).thenReturn(List.of());
        Job job = new Job();
        job.setId(21L);
        job.setTitle("Backend Engineer");
        when(jobs.findByIdAndOrganizationId(21L, "tenant-a")).thenReturn(Optional.of(job));

        CvChatToolScope scope = new CvChatToolScope();
        CvChatToolPlanningState planningState = new CvChatToolPlanningState();
        ExecutorFixture executorFixture = executorFixture();
        try {
            CvChatReadTools tools = new CvChatReadTools(mock(CvChatCandidateResolutionService.class), candidates,
                    documents, jobs, mock(EvaluationHistoryService.class), executorFixture.guard(), scope,
                    new CvChatToolDiscoveryState(), planningState);
            CvChatReadToolRegistry registry = new CvChatReadToolRegistry(tools);
            CvChatToolOrchestrationModel callbackDispatchingModel = (message, mode, callbacks, candidateIds, jobId) -> {
                candidateIds.forEach(candidateId -> callback(callbacks, "get_candidate_summary")
                        .call("{\"candidateId\":" + candidateId + "}"));
                if (jobId != null) {
                    callback(callbacks, "get_job_requirements").call("{\"jobId\":" + jobId + "}");
                }
                return new CvChatToolOrchestrationOutcome(
                        CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY, "Provider text is discarded");
            };
            CvChatToolOrchestrationService service = new CvChatToolOrchestrationService(evidence, policy, jobs,
                    new CvChatToolDiscoveryState(), mock(CvChatCandidateReferenceExtractor.class), tools, registry,
                    scope, planningState, callbackDispatchingModel);

            CvChatToolOrchestrationOutcome outcome = service.planConfirmedScope(
                    "Compare the confirmed candidates for the selected job", List.of(1L, 2L, 3L, 4L, 5L), 21L,
                    "tenant-a");

            assertThat(outcome).isEqualTo(new CvChatToolOrchestrationOutcome(
                    CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY,
                    "Confirmed candidate scope is ready for grounded comparison."));
            assertThat(planningState.covers(java.util.Set.of(1L, 2L, 3L, 4L, 5L), 21L)).isTrue();
            assertThat(planningState.snapshot()).isEqualTo(new CvChatToolPlanningState.Snapshot(5, 1));
        } finally {
            executorFixture.close();
        }
    }

    @Test
    void evaluationEvidenceCallbackUsesConfirmedTenantScopeAndReturnsBoundedMinimizedHistory() throws Exception {
        authenticate();
        EvaluationHistoryService evaluationHistory = mock(EvaluationHistoryService.class);
        when(evaluationHistory.findRecentEvidenceForCandidate(1L, "tenant-a", 6)).thenReturn(List.of(
                evaluation(101L, true),
                evaluation(102L, false),
                evaluation(103L, false),
                evaluation(104L, false),
                evaluation(105L, false),
                evaluation(106L, false)));
        CvChatToolScope scope = new CvChatToolScope();
        scope.confirm(List.of(1L));
        ExecutorFixture executorFixture = executorFixture();
        try {
            CvChatReadTools tools = new CvChatReadTools(mock(CvChatCandidateResolutionService.class),
                    mock(CandidateRepository.class), mock(CvDocumentRepository.class), mock(JobRepository.class),
                    evaluationHistory, executorFixture.guard(), scope, new CvChatToolDiscoveryState(),
                    new CvChatToolPlanningState());
            CvChatReadToolRegistry registry = new CvChatReadToolRegistry(tools);

            String result = callback(registry.evaluationEvidenceCallbacks(), "get_evaluation_evidence")
                    .call("{\"candidateId\":1}");

            verify(evaluationHistory).findRecentEvidenceForCandidate(1L, "tenant-a", 6);
            assertThat(result)
                    .contains("\"candidateId\":1", "\"evaluationId\":101", "\"evaluationId\":105",
                            "\"truncated\":true", "\"skillsMatch\":80")
                    .doesNotContain("\"evaluationId\":106", "Synthetic Candidate", "reviewer@example.test",
                            "tenant-a", "organizationId", "candidateName", "aiExplanation", "generatedRationale");
        } finally {
            executorFixture.close();
        }
    }

    @Test
    void evaluationEvidenceCallbackRejectsAnUnconfirmedCandidateBeforeHistoryAccess() throws Exception {
        authenticate();
        EvaluationHistoryService evaluationHistory = mock(EvaluationHistoryService.class);
        CvChatToolScope scope = new CvChatToolScope();
        scope.confirm(List.of(1L));
        ExecutorFixture executorFixture = executorFixture();
        try {
            CvChatReadTools tools = new CvChatReadTools(mock(CvChatCandidateResolutionService.class),
                    mock(CandidateRepository.class), mock(CvDocumentRepository.class), mock(JobRepository.class),
                    evaluationHistory, executorFixture.guard(), scope, new CvChatToolDiscoveryState(),
                    new CvChatToolPlanningState());
            CvChatReadToolRegistry registry = new CvChatReadToolRegistry(tools);
            ToolCallback callback = callback(registry.evaluationEvidenceCallbacks(), "get_evaluation_evidence");

            Throwable failure = catchThrowable(() -> callback.call("{\"candidateId\":2}"));

            assertThat(failure).isInstanceOf(RuntimeException.class);
            assertThat(rootCause(failure))
                    .isInstanceOf(CvChatException.class)
                    .hasMessage("Candidate is outside the confirmed chat scope");
            verifyNoInteractions(evaluationHistory);
        } finally {
            executorFixture.close();
        }
    }

    @Test
    void documentCallbacksUseConfirmedTenantScopeAndHideRawProcessingDetails() throws Exception {
        authenticate();
        CandidateRepository candidates = mock(CandidateRepository.class);
        CvDocumentRepository documents = mock(CvDocumentRepository.class);
        when(candidates.findByIdAndOrganizationId(1L, "tenant-a")).thenReturn(Optional.of(candidate(1L)));
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(61L);
        when(document.getStatus()).thenReturn(CvIngestionStatus.NEEDS_REVIEW);
        when(document.getExtractionMethod()).thenReturn(CvExtractionMethod.OCR);
        when(document.getImportedAt()).thenReturn(Instant.parse("2026-11-10T10:00:00Z"));
        when(document.getIngestionError()).thenReturn("private parser and provider diagnostic");
        when(documents.findAllByCandidateIdAndOrganizationId(1L, "tenant-a")).thenReturn(List.of(document));
        CvChatToolScope scope = new CvChatToolScope();
        scope.confirm(List.of(1L));
        ExecutorFixture executorFixture = executorFixture();
        try {
            CvChatReadTools tools = new CvChatReadTools(mock(CvChatCandidateResolutionService.class), candidates,
                    documents, mock(JobRepository.class), mock(EvaluationHistoryService.class),
                    executorFixture.guard(), scope, new CvChatToolDiscoveryState(), new CvChatToolPlanningState());
            CvChatReadToolRegistry registry = new CvChatReadToolRegistry(tools);

            String status = callback(registry.documentStatusCallbacks(), "get_document_status")
                    .call("{\"candidateId\":1}");
            String warnings = callback(registry.documentStatusCallbacks(), "get_ingestion_warnings")
                    .call("{\"candidateId\":1}");

            verify(candidates, org.mockito.Mockito.times(2)).findByIdAndOrganizationId(1L, "tenant-a");
            verify(documents, org.mockito.Mockito.times(2))
                    .findAllByCandidateIdAndOrganizationId(1L, "tenant-a");
            assertThat(status)
                    .contains("\"candidateId\":1", "\"documentId\":61", "\"status\":\"NEEDS_REVIEW\"",
                            "\"extractionMethod\":\"OCR\"")
                    .doesNotContain("tenant-a", "organizationId", "filename", "storageKey", "sha256", "cvText");
            assertThat(warnings)
                    .contains("\"candidateId\":1", "\"documentId\":61", "MANUAL_REVIEW_REQUIRED")
                    .doesNotContain("private parser", "provider diagnostic", "ingestionError");
        } finally {
            executorFixture.close();
        }
    }

    private ToolCallback callback(List<ToolCallback> callbacks, String name) {
        return callbacks.stream()
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing callback: " + name));
    }

    private Candidate candidate(Long id) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setName("Synthetic Candidate " + id);
        return candidate;
    }

    private EvaluationEvidenceSnapshot evaluation(Long id, boolean current) {
        Instant createdAt = Instant.parse("2026-11-10T10:00:00Z").minusSeconds(id);
        return new EvaluationEvidenceSnapshot(id, 21L, "Backend Engineer",
                80, 8, 7, 6, 70, 9, 8, 85, 79, createdAt,
                current ? null : createdAt.plusSeconds(60), current);
    }

    private Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void authenticate() {
        AppUserPrincipal principal = new AppUserPrincipal(1L, "reviewer@example.test", "", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Constructs package-private executor collaborators reflectively so the test can exercise their real behavior. */
    private ExecutorFixture executorFixture() throws Exception {
        Class<?> executorType = Class.forName("com.nevgiu.hrai.chat.tool.CvChatToolExecutor");
        Constructor<?> executorConstructor = executorType.getDeclaredConstructor(Duration.class);
        executorConstructor.setAccessible(true);
        Object executor = executorConstructor.newInstance(Duration.ofSeconds(2));
        Constructor<CvChatToolExecutionGuard> guardConstructor =
                CvChatToolExecutionGuard.class.getDeclaredConstructor(executorType);
        guardConstructor.setAccessible(true);
        CvChatToolExecutionGuard guard = guardConstructor.newInstance(executor);
        Method shutdown = executorType.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        return new ExecutorFixture(guard, executor, shutdown);
    }

    private record ExecutorFixture(CvChatToolExecutionGuard guard, Object executor, Method shutdown) {
        void close() throws Exception {
            shutdown.invoke(executor);
        }
    }
}
