package com.nevgiu.hrai.chat.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Explicit allow list for future tool-enabled chat orchestration. Nothing registers these callbacks globally: a
 * reviewed model call must opt in with {@code .toolCallbacks(registry.callbacks())} for this request only.
 */
@Component
@RequestScope
public class CvChatReadToolRegistry {
    private static final Set<String> ALLOWED_TOOL_NAMES = Set.of("search_candidates", "get_candidate_summary");
    private final List<ToolCallback> callbacks;

    public CvChatReadToolRegistry(CvChatReadTools tools) {
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
        return callbacks;
    }
}
