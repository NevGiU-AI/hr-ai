package com.nevgiu.hrai.security.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SecurityAuditWriter {
    private final SecurityAuditRepository events;

    SecurityAuditWriter(SecurityAuditRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void save(SecurityAuditEvent event) {
        events.saveAndFlush(event);
    }
}
