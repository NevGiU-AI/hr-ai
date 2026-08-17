package com.nevgiu.hrai.security.audit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SecurityAuditRetentionService {
    private final SecurityAuditRepository events;
    private final SecurityAuditProperties properties;

    public SecurityAuditRetentionService(SecurityAuditRepository events, SecurityAuditProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.security.audit.cleanup-cron:0 17 3 * * *}")
    @Transactional
    public void deleteExpiredEvents() {
        events.deleteByCreatedAtBefore(Instant.now().minus(properties.retention()));
    }
}
