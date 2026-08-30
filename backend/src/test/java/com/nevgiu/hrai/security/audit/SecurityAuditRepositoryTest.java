package com.nevgiu.hrai.security.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class SecurityAuditRepositoryTest {
    @Autowired SecurityAuditRepository events;
    @Autowired JdbcTemplate jdbc;

    @Test
    void returnsOnlyEventsFromTheRequestedOrganization() {
        events.save(event("tenant-a", SecurityEventType.LOGIN_SUCCEEDED));
        events.save(event("tenant-b", SecurityEventType.ACCOUNT_DISABLED));

        var page = events.findAllByOrganizationIdOrderByCreatedAtDesc("tenant-a", PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement()
                .extracting(SecurityAuditEvent::getEventType)
                .isEqualTo(SecurityEventType.LOGIN_SUCCEEDED);
    }

    @Test
    void storesPasswordEventsWithoutDatabaseEnumCheckConstraints() {
        events.saveAndFlush(event("tenant-a", SecurityEventType.PASSWORD_CHANGE_FAILED));
        events.saveAndFlush(event("tenant-a", SecurityEventType.PASSWORD_CHANGED));
        events.saveAndFlush(event("tenant-a", SecurityEventType.PASSWORD_RESET));

        Integer checkConstraints = jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = 'SECURITY_AUDIT_EVENTS' AND CONSTRAINT_TYPE = 'CHECK'
                """, Integer.class);
        assertThat(checkConstraints).isZero();
    }

    private SecurityAuditEvent event(String organizationId, SecurityEventType type) {
        return new SecurityAuditEvent(organizationId, 1L, "admin@example.com", 2L, "user@example.com",
                null, null, type, SecurityEventOutcome.SUCCESS, null);
    }
}
