package com.nevgiu.hrai.chat.memory;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

/** Composite identity prevents the same candidate appearing twice in one conversation. */
@Embeddable
public record CvChatConversationCandidateId(String conversationId, Long candidateId) implements Serializable {
    public CvChatConversationCandidateId {
        // JPA constructs this value reflectively; validation belongs to the application service.
    }
}
