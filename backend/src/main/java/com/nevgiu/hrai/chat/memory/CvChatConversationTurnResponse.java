package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatResponse;

import java.time.Instant;
import java.util.List;

/** One grounded follow-up plus the still-authorized visible scope and refreshed conversation state. */
record CvChatConversationTurnResponse(
        String conversationId,
        long version,
        String answer,
        List<CvChatConversationResponse.CandidateCard> candidates,
        CvChatConversationResponse.JobCard job,
        List<CvChatResponse.Citation> citations,
        Instant expiresAt
) {
}
