package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatReadTools;
import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;
import com.nevgiu.hrai.chat.tool.CvChatToolExecutionGuard;
import com.nevgiu.hrai.chat.tool.CvChatToolPlanningState;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                    documents, jobs, executorFixture.guard(), scope, new CvChatToolDiscoveryState(), planningState);
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
