package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountAdministrationController.class)
@Import(SecurityConfig.class)
class AccountAdministrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccountAdministrationService accounts;
    @MockitoBean AppUserDetailsService userDetailsService;

    @Test
    void administratorsOnlySeeAccountsFromTheirOrganization() throws Exception {
        AppUserPrincipal principal = principal("tenant-a", "ROLE_ADMIN");
        when(accounts.findAll("tenant-a")).thenReturn(List.of(
                new AccountResponse(2L, "recruiter@example.com", "tenant-a", true,
                        Set.of(AppRole.RECRUITER))));

        mvc.perform(get("/api/admin/users").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value("tenant-a"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void recruitersCannotAccessAccountAdministration() throws Exception {
        mvc.perform(get("/api/admin/users").with(user(principal("tenant-a", "ROLE_RECRUITER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCreatesAnAccountInTheAuthenticatedOrganization() throws Exception {
        AppUserPrincipal principal = principal("tenant-a", "ROLE_ADMIN");
        when(accounts.create(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AccountResponse(2L, "recruiter@example.com", "tenant-a", true,
                        Set.of(AppRole.RECRUITER)));

        mvc.perform(post("/api/admin/users")
                        .with(user(principal)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"email":"recruiter@example.com","password":"a-secure-password","roles":["RECRUITER"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value("tenant-a"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidAccountInput() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .with(user(principal("tenant-a", "ROLE_ADMIN"))).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"email":"not-an-email","password":"short","roles":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void returnsTheTypedApiErrorForDuplicateEmail() throws Exception {
        AppUserPrincipal principal = principal("tenant-a", "ROLE_ADMIN");
        when(accounts.create(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new AccountAdministrationException(org.springframework.http.HttpStatus.CONFLICT,
                        "An account could not be created with this email"));

        mvc.perform(post("/api/admin/users")
                        .with(user(principal)).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"email":"existing@example.com","password":"a-secure-password","roles":["RECRUITER"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account could not be created with this email"));
    }

    @Test
    void administratorUpdatesAnotherAccountsRoles() throws Exception {
        when(accounts.updateRoles(org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AccountResponse(2L, "reviewer@example.com", "tenant-a", true,
                        Set.of(AppRole.REVIEWER)));

        mvc.perform(put("/api/admin/users/2/roles").with(user(principal("tenant-a", "ROLE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"roles\":[\"REVIEWER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("REVIEWER"));
    }

    @Test
    void administratorDisablesAnotherAccount() throws Exception {
        when(accounts.updateStatus(org.mockito.ArgumentMatchers.eq("tenant-a"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AccountResponse(2L, "user@example.com", "tenant-a", false,
                        Set.of(AppRole.RECRUITER)));

        mvc.perform(put("/api/admin/users/2/status").with(user(principal("tenant-a", "ROLE_ADMIN"))).with(csrf())
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void administratorRevokesAnotherAccountsSessions() throws Exception {
        when(accounts.revokeSessions("tenant-a", 1L, 2L)).thenReturn(2);

        mvc.perform(post("/api/admin/users/2/sessions/revoke")
                        .with(user(principal("tenant-a", "ROLE_ADMIN"))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(2));
    }

    private AppUserPrincipal principal(String organizationId, String role) {
        return new AppUserPrincipal(1L, "admin@example.com", "hash", organizationId, true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
