package com.nevgiu.hrai.chat.memory;

import java.time.Instant;
import java.util.List;

record CvChatConversationResponse(
        String id,
        long version,
        List<CandidateCard> candidates,
        JobCard job,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {

    record CandidateCard(Long id, String name, String currentTitle) {
    }

    record JobCard(Long id, String title, String level) {
    }
}
