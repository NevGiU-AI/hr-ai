package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserDetailsService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.SecurityConfig;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvChatController.class)
@Import(SecurityConfig.class)
class CvChatSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvChatService chat;
    @MockitoBean AppUserDetailsService userDetailsService;
    @MockitoBean SecurityAuditService audit;

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_RECRUITER", "ROLE_REVIEWER"})
    void permitsRolesThatMayReviewCandidateEvidence(String role) throws Exception {
        when(chat.ask(anyLong(), anyString(), anyString()))
                .thenReturn(new CvChatResponse("Grounded answer [CV-CANDIDATE-7]", List.of()));

        mvc.perform(post("/api/chat/candidates/7")
                        .contentType("application/json")
                        .content("{\"message\":\"Summarize this CV\"}")
                        .with(user(principal(role))).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void deniesReadOnlyUsers() throws Exception {
        mvc.perform(post("/api/chat/candidates/7")
                        .contentType("application/json")
                        .content("{\"message\":\"Summarize this CV\"}")
                        .with(user(principal("ROLE_READ_ONLY"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthenticationAndCsrfProtection() throws Exception {
        mvc.perform(post("/api/chat/candidates/7")
                        .contentType("application/json")
                        .content("{\"message\":\"Summarize this CV\"}").with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/chat/candidates/7")
                        .contentType("application/json")
                        .content("{\"message\":\"Summarize this CV\"}")
                        .with(user(principal("ROLE_RECRUITER"))))
                .andExpect(status().isForbidden());
    }

    private AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(1L, "user@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
