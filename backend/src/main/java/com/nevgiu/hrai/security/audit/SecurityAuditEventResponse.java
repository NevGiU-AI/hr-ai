package com.nevgiu.hrai.security.audit;

import java.time.Instant;

public record SecurityAuditEventResponse(Long id, String organizationId, Long actorUserId, String actorEmail,
                                         Long targetUserId, String targetEmail, SecurityEventType eventType,
                                         SecurityEventOutcome outcome, String details, Instant createdAt) {
    static SecurityAuditEventResponse from(SecurityAuditEvent event) {
        return new SecurityAuditEventResponse(event.getId(), event.getOrganizationId(), event.getActorUserId(),
                event.getActorEmail(), event.getTargetUserId(), event.getTargetEmail(), event.getEventType(), event.getOutcome(),
                event.getDetails(), event.getCreatedAt());
    }
}
