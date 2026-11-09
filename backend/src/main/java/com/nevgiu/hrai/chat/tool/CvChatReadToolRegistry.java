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
 * Explicit allow list for future tool-enabled chat orchestration. Nothing registers these callbacks globally: a
 * reviewed model call must opt in with {@code .toolCallbacks(registry.callbacks())} for this request only.
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
            "search_candidates", "get_candidate_summary", "get_job_requirements");
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

    public List<ToolCallback> callbacks() {
        // The immutable list prevents an orchestrator from adding a callback after allow-list validation.
        return callbacks;
    }

    /** Confirmed-scope planning may search again and may summarize only IDs enforced by {@link CvChatToolScope}. */
    public List<ToolCallback> confirmedScopeCallbacks() {
        // Candidate discovery is already complete. Expose only the minimum tool needed for the confirmed plan.
        return matching(callback -> callback.getToolDefinition().name().equals("get_candidate_summary"));
    }

    /** Adds approved-job requirements only after application code confirms and authorizes a selected job. */
    public List<ToolCallback> confirmedScopeCallbacksWithJob() {
        // Search remains unavailable after confirmation; the model cannot widen the human-selected candidate scope.
        return matching(callback -> !callback.getToolDefinition().name().equals("search_candidates"));
    }

    private List<ToolCallback> matching(Predicate<ToolCallback> predicate) {
        return callbacks.stream().filter(predicate).toList();
    }
}
