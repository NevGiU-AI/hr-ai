package com.nevgiu.hrai.chat.memory;

import org.springframework.ai.tool.ToolCallback;
import java.util.List;

interface CvChatDocumentToolModel {
    CvChatDocumentToolOutcome respond(String message, List<Long> confirmedCandidateIds,
                                      List<ToolCallback> callbacks);
}
