package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import com.nevgiu.hrai.security.dto.ChangePasswordRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {
    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock AccountSessionService sessions;
    @Mock SecurityAuditService audit;

    @Test
    void changesPasswordAndRevokesEverySession() {
        AppUser account = new AppUser("person@example.com", "old-hash", "tenant-a", Set.of(AppRole.RECRUITER));
        when(users.findByIdAndOrganizationId(1L, "tenant-a")).thenReturn(Optional.of(account));
        when(passwords.matches("current-password", "old-hash")).thenReturn(true);
        when(passwords.matches("new-secure-password", "old-hash")).thenReturn(false);
        when(passwords.encode("new-secure-password")).thenReturn("new-hash");

        service().change(principal(), new ChangePasswordRequest("current-password", "new-secure-password"));

        verify(users).saveAndFlush(account);
        verify(sessions).revoke("person@example.com");
        verify(audit).administration(SecurityEventType.PASSWORD_CHANGED, 1L, "tenant-a", account, null);
    }

    @Test
    void rejectsAnIncorrectCurrentPasswordWithoutChangingOrRevoking() {
        AppUser account = new AppUser("person@example.com", "old-hash", "tenant-a", Set.of(AppRole.RECRUITER));
        when(users.findByIdAndOrganizationId(1L, "tenant-a")).thenReturn(Optional.of(account));
        when(passwords.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service().change(principal(),
                new ChangePasswordRequest("wrong-password", "new-secure-password")))
                .isInstanceOf(PasswordManagementException.class)
                .hasMessage("Current password is incorrect");

        verify(users, never()).saveAndFlush(account);
        verify(sessions, never()).revoke("person@example.com");
        verify(audit).passwordChangeFailed(principal(), "reason=current-password-mismatch");
    }

    private PasswordManagementService service() {
        return new PasswordManagementService(users, passwords, sessions, audit);
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(1L, "person@example.com", "old-hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));
    }
}
