package com.nevgiu.hrai.security.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "security_audit_events", indexes = {
        @Index(name = "idx_security_audit_org_created", columnList = "organizationId,createdAt"),
        @Index(name = "idx_security_audit_created", columnList = "createdAt")
})
public class SecurityAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 100) private String organizationId;
    private Long actorUserId;
    @Column(length = 320) private String actorEmail;
    private Long targetUserId;
    @Column(length = 320) private String targetEmail;
    @Column(length = 64) private String targetIdentifierHash;
    @Column(length = 64) private String clientIpHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private SecurityEventType eventType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SecurityEventOutcome outcome;
    @Column(length = 500) private String details;
    @Column(nullable = false) private Instant createdAt;

    protected SecurityAuditEvent() {}

    public SecurityAuditEvent(String organizationId, Long actorUserId, String actorEmail, Long targetUserId,
                              String targetEmail, String targetIdentifierHash, String clientIpHash,
                              SecurityEventType eventType, SecurityEventOutcome outcome, String details) {
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.targetUserId = targetUserId;
        this.targetEmail = targetEmail;
        this.targetIdentifierHash = targetIdentifierHash;
        this.clientIpHash = clientIpHash;
        this.eventType = eventType;
        this.outcome = outcome;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public Long getTargetUserId() { return targetUserId; }
    public String getTargetEmail() { return targetEmail; }
    public String getTargetIdentifierHash() { return targetIdentifierHash; }
    public String getClientIpHash() { return clientIpHash; }
    public SecurityEventType getEventType() { return eventType; }
    public SecurityEventOutcome getOutcome() { return outcome; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
