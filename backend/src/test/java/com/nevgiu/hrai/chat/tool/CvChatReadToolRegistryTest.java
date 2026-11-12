package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
import com.nevgiu.hrai.chat.CvChatOrganizationDiscoveryService;
import com.nevgiu.hrai.evaluation.EvaluationHistoryService;
import com.nevgiu.hrai.job.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.aop.framework.ProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CvChatReadToolRegistryTest {

    @Test
    void registersOnlyTheReviewedToolsAndKeepsTrustedContextOutOfTheirSchemas() {
        CvChatReadTools tools = tools();
        CvChatReadToolRegistry registry = new CvChatReadToolRegistry(tools);

        List<ToolCallback> callbacks = registry.allReviewedCallbacksForTesting();

        assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("search_candidates", "get_candidate_summary", "get_job_requirements",
                        "search_candidates_by_evidence", "get_evaluation_evidence", "get_document_status",
                        "get_ingestion_warnings");
        assertThat(registry.confirmedScopeCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("get_candidate_summary");
        assertThat(registry.confirmedScopeCallbacksWithJob())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("get_candidate_summary", "get_job_requirements");
        assertThat(registry.evaluationEvidenceCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("get_evaluation_evidence");
        assertThat(registry.documentStatusCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("get_document_status", "get_ingestion_warnings");
        assertThat(registry.organizationDiscoveryCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("search_candidates_by_evidence");
        assertThat(callbacks).allSatisfy(callback -> assertThat(callback.getToolDefinition().inputSchema())
                .doesNotContainIgnoringCase("organization")
                .doesNotContainIgnoringCase("tenant")
                .doesNotContainIgnoringCase("role")
                .doesNotContainIgnoringCase("authorization"));
    }

    @Test
    void discoversAnnotationsThroughTheClassProxyUsedForRequestScopedBeans() {
        ProxyFactory proxyFactory = new ProxyFactory(tools());
        proxyFactory.setProxyTargetClass(true);

        CvChatReadToolRegistry registry = new CvChatReadToolRegistry((CvChatReadTools) proxyFactory.getProxy());

        assertThat(registry.allReviewedCallbacksForTesting())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("search_candidates", "get_candidate_summary", "get_job_requirements",
                        "search_candidates_by_evidence", "get_evaluation_evidence", "get_document_status",
                        "get_ingestion_warnings");
    }

    private CvChatReadTools tools() {
        return new CvChatReadTools(
                mock(CvChatCandidateResolutionService.class), mock(CandidateRepository.class),
                mock(CvDocumentRepository.class), mock(JobRepository.class), mock(EvaluationHistoryService.class),
                mock(CvChatToolExecutionGuard.class), mock(CvChatToolScope.class), mock(CvChatToolDiscoveryState.class),
                mock(CvChatToolPlanningState.class), mock(CvChatOrganizationDiscoveryService.class));
    }
}
