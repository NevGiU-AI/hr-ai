package com.nevgiu.hrai.security.audit;

import com.nevgiu.hrai.security.AppRole;
import com.nevgiu.hrai.security.AppUser;
import com.nevgiu.hrai.security.AppUserRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceTest {
    @Mock SecurityAuditRepository events;
    @Mock SecurityAuditWriter writer;
    @Mock AppUserRepository users;

    @Test
    void associatesFailedLoginWithTheKnownUsersOrganization() {
        AppUser user = new AppUser("person@example.com", "hash", "tenant-a", Set.of(AppRole.RECRUITER));
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));

        service().loginAttempt(SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE,
                " Person@Example.com ", "203.0.113.10", "bad credentials");

        SecurityAuditEvent event = capturedEvent();
        assertThat(event.getOrganizationId()).isEqualTo("tenant-a");
        assertThat(event.getTargetEmail()).isEqualTo("person@example.com");
        assertThat(event.getTargetIdentifierHash()).isNull();
        assertThat(event.getClientIpHash()).hasSize(64).doesNotContain("203.0.113.10");
    }

    @Test
    void hashesUnknownLoginIdentifiersWithoutAssigningATenant() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        service().loginAttempt(SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE,
                "unknown@example.com", "203.0.113.11", null);

        SecurityAuditEvent event = capturedEvent();
        assertThat(event.getOrganizationId()).isNull();
        assertThat(event.getTargetEmail()).isNull();
        assertThat(event.getTargetIdentifierHash()).hasSize(64).doesNotContain("unknown@example.com");
    }

    @Test
    void includesTheAdministratorsEmailSnapshotInAdministrationEvents() {
        AppUser administrator = new AppUser("admin@example.com", "hash", "tenant-a", Set.of(AppRole.ADMIN));
        AppUser target = new AppUser("person@example.com", "hash", "tenant-a", Set.of(AppRole.RECRUITER));
        when(users.findById(1L)).thenReturn(Optional.of(administrator));

        service().administration(SecurityEventType.ROLES_CHANGED, 1L, "tenant-a", target,
                "roles=READ_ONLY");

        SecurityAuditEvent event = capturedEvent();
        assertThat(event.getActorUserId()).isEqualTo(1L);
        assertThat(event.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(event.getTargetEmail()).isEqualTo("person@example.com");
    }

    @Test
    void identifiesAuthenticatedPasswordFailureActorAndTargetAsTheSameUser() {
        var principal = new AppUserPrincipal(2L, "person@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));

        service().passwordChangeFailed(principal, "reason=current-password-mismatch");

        SecurityAuditEvent event = capturedEvent();
        assertThat(event.getActorUserId()).isEqualTo(2L);
        assertThat(event.getActorEmail()).isEqualTo("person@example.com");
        assertThat(event.getTargetUserId()).isEqualTo(2L);
        assertThat(event.getTargetEmail()).isEqualTo("person@example.com");
        assertThat(event.getOutcome()).isEqualTo(SecurityEventOutcome.FAILURE);
    }

    @Test
    void recordsSessionLimitEnforcementWithoutSessionIdentifiers() {
        var principal = new AppUserPrincipal(2L, "person@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));

        service().sessionLimitEnforced(principal, 1, 3);

        SecurityAuditEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(SecurityEventType.SESSION_LIMIT_ENFORCED);
        assertThat(event.getActorEmail()).isEqualTo("person@example.com");
        assertThat(event.getTargetEmail()).isEqualTo("person@example.com");
        assertThat(event.getDetails()).isEqualTo("expiredSessions=1;maximumSessions=3");
        assertThat(event.getDetails()).doesNotContain("session-");
    }

    private SecurityAuditService service() {
        return new SecurityAuditService(events, writer, users);
    }

    private SecurityAuditEvent capturedEvent() {
        ArgumentCaptor<SecurityAuditEvent> event = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(writer).save(event.capture());
        return event.getValue();
    }
}
