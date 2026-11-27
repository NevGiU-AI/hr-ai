package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatAnswerKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Store the stable API name, not the enum ordinal. Legacy rows and user messages have no answer outcome.
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private CvChatAnswerKind answerKind;

    @Column(length = 2)
    private String responseLanguage;

    @Column(nullable = false)
    private Instant createdAt;

    protected CvChatMessage() {
    }

    CvChatMessage(String conversationId, String role, String content, CvChatAnswerKind answerKind,
                  String responseLanguage, Instant createdAt) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.answerKind = answerKind;
        this.responseLanguage = responseLanguage;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    String getRole() { return role; }
    String getContent() { return content; }
    CvChatAnswerKind getAnswerKind() { return answerKind; }
    String getResponseLanguage() { return responseLanguage; }
}
