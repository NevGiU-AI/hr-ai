package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
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

        List<ToolCallback> callbacks = registry.callbacks();

        assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("search_candidates", "get_candidate_summary");
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

        assertThat(registry.callbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("search_candidates", "get_candidate_summary");
    }

    private CvChatReadTools tools() {
        return new CvChatReadTools(
                mock(CvChatCandidateResolutionService.class), mock(CandidateRepository.class),
                mock(CvDocumentRepository.class), mock(CvChatToolExecutionGuard.class));
    }
}
