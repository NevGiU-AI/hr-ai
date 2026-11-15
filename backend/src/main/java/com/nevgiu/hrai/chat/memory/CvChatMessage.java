package com.nevgiu.hrai.chat.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Application-owned visible timeline message; it is separate from bounded model-context memory. */
@Entity
@Table(name = "cv_chat_messages")
class CvChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String conversationId;

    @Column(nullable = false, length = 10)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected CvChatMessage() {
    }

    CvChatMessage(String conversationId, String role, String content, Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    String getRole() { return role; }
    String getContent() { return content; }
}
