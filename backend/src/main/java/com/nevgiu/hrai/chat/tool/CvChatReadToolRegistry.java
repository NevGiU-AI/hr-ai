package com.nevgiu.hrai.chat.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Explicit allow list for tool-enabled chat orchestration. Nothing registers these callbacks globally: every reviewed
 * model call must opt in to one of the narrow, phase-specific callback subsets exposed below.
 *
 * <p>Spring components are singleton-scoped by default. This registry deliberately uses {@link RequestScope} instead:
 * Spring constructs a fresh registry for each HTTP request and discards it when the response completes. Each generated
 * callback is therefore bound to that request's {@link CvChatReadTools}, execution guard, confirmed scope, and
 * discovery state. Retaining these callbacks in the default singleton scope could accidentally retain or resolve
 * state belonging to another concurrent request.</p>
 *
 * <p>When this request-scoped registry is injected into the singleton orchestration service, Spring injects a scoped
 * proxy. Calling the proxy during a request resolves the actual registry instance for that request.</p>
 */
@Component
@RequestScope
public class CvChatReadToolRegistry {
    private static final Set<String> ALLOWED_TOOL_NAMES = Set.of(
            "search_candidates", "get_candidate_summary", "get_job_requirements", "get_evaluation_evidence",
            "get_document_status", "get_ingestion_warnings");
    private static final Set<String> CONFIRMED_SCOPE_TOOL_NAMES = Set.of("get_candidate_summary");
    private static final Set<String> CONFIRMED_SCOPE_WITH_JOB_TOOL_NAMES = Set.of(
            "get_candidate_summary", "get_job_requirements");
    private static final Set<String> EVALUATION_EVIDENCE_TOOL_NAMES = Set.of("get_evaluation_evidence");
    private static final Set<String> DOCUMENT_STATUS_TOOL_NAMES = Set.of(
            "get_document_status", "get_ingestion_warnings");
    private final List<ToolCallback> callbacks;

    public CvChatReadToolRegistry(CvChatReadTools tools) {
        // This constructor runs once per HTTP request, not once at application startup. The callbacks below therefore
        // wrap the current request's tool object and must not be cached beyond that request.
        // Spring AI discovers @Tool methods and generates their JSON schemas. Compare the discovered names with a
        // closed allow list so adding another annotated method cannot silently expose it to a future model call.
        List<ToolCallback> discovered = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks());
        Set<String> discoveredNames = discovered.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!discoveredNames.equals(ALLOWED_TOOL_NAMES)) {
            throw new IllegalStateException("Unexpected CV chat read-tool registration");
        }
        this.callbacks = List.copyOf(discovered);
    }

    /** Package-private inspection hook for the exact allow-list and generated-schema regression tests. */
    List<ToolCallback> allReviewedCallbacksForTesting() {
        // Production orchestration never receives this complete set; it must choose a phase-specific public subset.
        return callbacks;
    }

    /** Confirmed-scope planning may search again and may summarize only IDs enforced by {@link CvChatToolScope}. */
    public List<ToolCallback> confirmedScopeCallbacks() {
        // Candidate discovery is already complete. Expose only the minimum tool needed for the confirmed plan.
        return matching(callback -> CONFIRMED_SCOPE_TOOL_NAMES.contains(callback.getToolDefinition().name()));
    }

    /** Adds approved-job requirements only after application code confirms and authorizes a selected job. */
    public List<ToolCallback> confirmedScopeCallbacksWithJob() {
        // Use an explicit set rather than "everything except search" so newly reviewed tools are not exposed to this
        // existing preflight accidentally. The model cannot widen scope or read unrelated data after confirmation.
        return matching(callback -> CONFIRMED_SCOPE_WITH_JOB_TOOL_NAMES.contains(callback.getToolDefinition().name()));
    }

    /**
     * Supplies the persisted evaluation-history tool to a future evaluation-aware conversational answer path.
     *
     * <p>The current confirmed-scope call is advisory and discards tool output, so deliberately keep this callback out
     * of both generic preflight subsets. Registering it there would consume database/model calls without changing the
     * recruiter-visible comparison. The dedicated chat path must opt in explicitly after installing confirmed scope.</p>
     */
    public List<ToolCallback> evaluationEvidenceCallbacks() {
        return matching(callback -> EVALUATION_EVIDENCE_TOOL_NAMES.contains(callback.getToolDefinition().name()));
    }

    /** Supplies document processing state only to a future answer path that explicitly consumes these results. */
    public List<ToolCallback> documentStatusCallbacks() {
        // Keep operational state out of the current confirmed-scope preflight, which discards provider output.
        return matching(callback -> DOCUMENT_STATUS_TOOL_NAMES.contains(callback.getToolDefinition().name()));
    }

    private List<ToolCallback> matching(Predicate<ToolCallback> predicate) {
        return callbacks.stream().filter(predicate).toList();
    }
}
