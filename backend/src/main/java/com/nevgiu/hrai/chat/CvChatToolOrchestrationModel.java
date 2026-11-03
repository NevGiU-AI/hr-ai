package com.nevgiu.hrai.chat;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/** Provider boundary for one explicitly allow-listed, read-only tool-planning call. */
interface CvChatToolOrchestrationModel {
    String respond(String message, List<ToolCallback> callbacks);
}
