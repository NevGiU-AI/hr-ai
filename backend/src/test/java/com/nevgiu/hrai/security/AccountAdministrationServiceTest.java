package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.CreateAccountRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAdministrationServiceTest {
    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AccountAdministrationService service;

    @Test
    void createsAccountInsideAuthenticatedAdministratorsOrganization() {
        when(users.findByEmailIgnoreCase("recruiter@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-secure-password")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create("tenant-a", new CreateAccountRequest(" Recruiter@Example.com ", "a-secure-password",
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

        assertThatThrownBy(() -> service.create("tenant-a", new CreateAccountRequest(
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

        assertThatThrownBy(() -> service.create("tenant-a", new CreateAccountRequest(
                "race@example.com", "a-secure-password", Set.of(AppRole.RECRUITER))))
                .isInstanceOf(AccountAdministrationException.class)
                .hasMessage("An account could not be created with this email");
    }
}
