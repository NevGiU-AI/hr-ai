package com.nevgiu.hrai.security.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SecurityAuditRepositoryTest {
    @Autowired SecurityAuditRepository events;

    @Test
    void returnsOnlyEventsFromTheRequestedOrganization() {
        events.save(event("tenant-a", SecurityEventType.LOGIN_SUCCEEDED));
        events.save(event("tenant-b", SecurityEventType.ACCOUNT_DISABLED));

        var page = events.findAllByOrganizationIdOrderByCreatedAtDesc("tenant-a", PageRequest.of(0, 20));

        assertThat(page.getContent()).singleElement()
                .extracting(SecurityAuditEvent::getEventType)
                .isEqualTo(SecurityEventType.LOGIN_SUCCEEDED);
    }

    private SecurityAuditEvent event(String organizationId, SecurityEventType type) {
        return new SecurityAuditEvent(organizationId, 1L, "admin@example.com", 2L, "user@example.com",
                null, null, type, SecurityEventOutcome.SUCCESS, null);
    }
}
