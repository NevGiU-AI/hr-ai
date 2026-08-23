package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.CreateAccountRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountRolesRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountStatusRequest;
import com.nevgiu.hrai.security.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import com.nevgiu.hrai.security.audit.SecurityAuditService;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationServiceTest {
    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccountSessionService sessions;
    @Mock LoginThrottleService loginThrottle;
    @Mock SecurityAuditService audit;
    @InjectMocks AccountAdministrationService service;

    @Test
    void createsAccountInsideAuthenticatedAdministratorsOrganization() {
        when(users.findByEmailIgnoreCase("recruiter@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-secure-password")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create("tenant-a", 1L, new CreateAccountRequest(" Recruiter@Example.com ", "a-secure-password",
                Set.of(AppRole.RECRUITER)));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("recruiter@example.com");
        assertThat(saved.getValue().getOrganizationId()).isEqualTo("tenant-a");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getValue().getRoles()).containsExactly(AppRole.RECRUITER);
    }

    @Test
    void rejectsAnEmailThatAlreadyBelongsToAnyOrganization() {
        when(users.findByEmailIgnoreCase("existing@example.com")).thenReturn(Optional.of(
                new AppUser("existing@example.com", "hash", "tenant-b", Set.of(AppRole.ADMIN))));

        assertThatThrownBy(() -> service.create("tenant-a", 1L, new CreateAccountRequest(
                "existing@example.com", "a-secure-password", Set.of(AppRole.RECRUITER))))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("An account could not be created with this email");
    }

    @Test
    void convertsAConcurrentUniqueEmailInsertIntoAConflict() {
        when(users.findByEmailIgnoreCase("race@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-secure-password")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenThrow(new DataIntegrityViolationException("unique email"));

        assertThatThrownBy(() -> service.create("tenant-a", 1L, new CreateAccountRequest(
                "race@example.com", "a-secure-password", Set.of(AppRole.RECRUITER))))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("An account could not be created with this email");
    }

    @Test
    void updatesRolesInsideTheActorsOrganizationAndRevokesExistingSessions() {
        AppUser account = org.mockito.Mockito.mock(AppUser.class);
        when(account.getId()).thenReturn(2L);
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.getOrganizationId()).thenReturn("tenant-a");
        when(account.isEnabled()).thenReturn(true);
        when(account.getRoles()).thenReturn(Set.of(AppRole.RECRUITER));
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.of(account));
        when(users.saveAndFlush(account)).thenReturn(account);

        service.updateRoles("tenant-a", 1L, 2L,
                new UpdateAccountRolesRequest(Set.of(AppRole.REVIEWER)));

        verify(account).replaceRoles(Set.of(AppRole.REVIEWER));
        verify(sessions).revoke("user@example.com");
    }

    @Test
    void rejectsCrossTenantAccountMutationAsNotFound() {
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus("tenant-a", 1L, 2L,
                new UpdateAccountStatusRequest(false)))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("Account not found");
        verify(sessions, never()).revoke(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void preventsRemovingTheLastEnabledAdministrator() {
        AppUser account = org.mockito.Mockito.mock(AppUser.class);
        when(account.isEnabled()).thenReturn(true);
        when(account.getRoles()).thenReturn(Set.of(AppRole.ADMIN));
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.of(account));
        when(users.findAllByOrganizationIdAndEnabledTrueAndRolesContaining("tenant-a", AppRole.ADMIN))
                .thenReturn(java.util.List.of(account));

        assertThatThrownBy(() -> service.updateRoles("tenant-a", 1L, 2L,
                new UpdateAccountRolesRequest(Set.of(AppRole.RECRUITER))))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("The organization must retain at least one enabled administrator");
    }

    @Test
    void disablesAnotherAccountAndRevokesItsSessions() {
        AppUser account = org.mockito.Mockito.mock(AppUser.class);
        when(account.getId()).thenReturn(2L);
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.getOrganizationId()).thenReturn("tenant-a");
        when(account.isEnabled()).thenReturn(false);
        when(account.getRoles()).thenReturn(Set.of(AppRole.RECRUITER));
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.of(account));
        when(users.saveAndFlush(account)).thenReturn(account);

        service.updateStatus("tenant-a", 1L, 2L, new UpdateAccountStatusRequest(false));

        verify(account).setEnabled(false);
        verify(sessions).revoke("user@example.com");
    }

    @Test
    void rejectsSelfManagement() {
        assertThatThrownBy(() -> service.revokeSessions("tenant-a", 1L, 1L))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("You cannot revoke your own session");
        verify(users, never()).findByIdAndOrganizationId(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unlocksAnAccountInsideTheAdministratorsOrganization() {
        AppUser account = org.mockito.Mockito.mock(AppUser.class);
        when(account.getId()).thenReturn(2L);
        when(account.getEmail()).thenReturn("user@example.com");
        when(account.getOrganizationId()).thenReturn("tenant-a");
        when(account.isEnabled()).thenReturn(true);
        when(account.getRoles()).thenReturn(Set.of(AppRole.RECRUITER));
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.of(account));

        service.unlock("tenant-a", 1L, 2L);

        verify(loginThrottle).unlockAccount("user@example.com");
    }

    @Test
    void administratorResetsAnotherAccountsPasswordAndRevokesSessions() {
        AppUser account = org.mockito.Mockito.mock(AppUser.class);
        when(account.getEmail()).thenReturn("user@example.com");
        when(users.findByIdAndOrganizationId(2L, "tenant-a")).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("replacement-password")).thenReturn("replacement-hash");

        service.resetPassword("tenant-a", 1L, 2L, new ResetPasswordRequest("replacement-password"));

        verify(account).setPasswordHash("replacement-hash");
        verify(users).saveAndFlush(account);
        verify(sessions).revoke("user@example.com");
        verify(audit).administration(com.nevgiu.hrai.security.audit.SecurityEventType.PASSWORD_RESET,
                1L, "tenant-a", account, null);
    }
}
