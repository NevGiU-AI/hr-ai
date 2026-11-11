package com.nevgiu.hrai.chat.memory;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Ordered member of the application-confirmed candidate scope. */
@Entity
@Table(name = "cv_chat_conversation_candidates")
public class CvChatConversationCandidate {
    @EmbeddedId
    private CvChatConversationCandidateId id;

    @Column(nullable = false, length = 100)
    private String organizationId;

    @Column(nullable = false)
    private int displayOrder;

    protected CvChatConversationCandidate() {
    }

    CvChatConversationCandidate(String conversationId, Long candidateId, String organizationId, int displayOrder) {
        this.id = new CvChatConversationCandidateId(conversationId, candidateId);
        this.organizationId = organizationId;
        this.displayOrder = displayOrder;
    }

    public Long getCandidateId() { return id.candidateId(); }
    public int getDisplayOrder() { return displayOrder; }
}
