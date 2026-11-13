package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatResponse;

import java.util.List;

/** Bounded persisted timeline returned only after conversation owner and tenant authorization. */
record CvChatConversationHistoryResponse(List<TimelineMessage> messages) {
    record TimelineMessage(String role, String content, List<CvChatResponse.Citation> citations) {
    }
}
