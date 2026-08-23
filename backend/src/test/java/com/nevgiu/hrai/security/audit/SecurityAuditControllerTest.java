package com.nevgiu.hrai.security.audit;

import com.nevgiu.hrai.security.AppUserDetailsService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityAuditController.class)
@Import(SecurityConfig.class)
class SecurityAuditControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SecurityAuditService audit;
    @MockitoBean AppUserDetailsService userDetailsService;

    @Test
    void administratorReadsOnlyTheOrganizationFromTheirPrincipal() throws Exception {
        AppUserPrincipal admin = principal("ROLE_ADMIN");
        when(audit.findAll("tenant-a", 1, 25))
                .thenReturn(new SecurityAuditPageResponse(List.of(), 1, 25, 0, 0));

        mvc.perform(get("/api/admin/security-events?page=1&size=25").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));

        verify(audit).findAll("tenant-a", 1, 25);
    }

    @Test
    void recruiterCannotReadSecurityEvents() throws Exception {
        AppUserPrincipal recruiter = principal("ROLE_RECRUITER");

        mvc.perform(get("/api/admin/security-events").with(user(recruiter)))
                .andExpect(status().isForbidden());

        verify(audit).administrationDenied(recruiter, "/api/admin/security-events", 403);
    }

    private AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(1L, "person@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
