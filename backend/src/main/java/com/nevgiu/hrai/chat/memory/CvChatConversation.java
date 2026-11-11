package com.nevgiu.hrai.chat.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Application-owned authorization boundary for one future conversational workflow.
 *
 * <p>No model prompt or Spring AI memory row can change these server-owned tenant, owner, candidate, or job fields.
 * Relationships are represented by scalar IDs so every lookup remains explicitly tenant scoped.</p>
 */
@Entity
@Table(name = "cv_chat_conversations")
public class CvChatConversation {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String organizationId;

    @Column(nullable = false)
    private Long ownerUserId;

    private Long confirmedJobId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected CvChatConversation() {
    }

    CvChatConversation(String id, String organizationId, Long ownerUserId, Long confirmedJobId,
                       Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.ownerUserId = ownerUserId;
        this.confirmedJobId = confirmedJobId;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    void replaceJob(Long jobId, Instant now, Instant newExpiry) {
        confirmedJobId = jobId;
        updatedAt = now;
        expiresAt = newExpiry;
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getConfirmedJobId() { return confirmedJobId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
