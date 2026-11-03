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
 * <p>The registry is request-scoped because each callback is bound to that request's {@link CvChatReadTools} proxy and
 * execution guard. Retaining callbacks in a singleton could accidentally retain or resolve the wrong request scope.</p>
 */
@Component
@RequestScope
public class CvChatReadToolRegistry {
    private static final Set<String> ALLOWED_TOOL_NAMES = Set.of("search_candidates", "get_candidate_summary");
    private final List<ToolCallback> callbacks;

    public CvChatReadToolRegistry(CvChatReadTools tools) {
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

    /** Discovery cannot read candidate details before explicit human confirmation. */
    public List<ToolCallback> discoveryCallbacks() {
        return matching(callback -> callback.getToolDefinition().name().equals("search_candidates"));
    }

    /** Confirmed-scope planning may search again and may summarize only IDs enforced by {@link CvChatToolScope}. */
    public List<ToolCallback> confirmedScopeCallbacks() {
        return callbacks;
    }

    private List<ToolCallback> matching(Predicate<ToolCallback> predicate) {
        return callbacks.stream().filter(predicate).toList();
    }
}
