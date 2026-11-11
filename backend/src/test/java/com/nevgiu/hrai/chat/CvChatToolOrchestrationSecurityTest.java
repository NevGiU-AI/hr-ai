package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserDetailsService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.SecurityConfig;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvChatToolOrchestrationController.class)
@Import(SecurityConfig.class)
class CvChatToolOrchestrationSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvChatToolOrchestrationService orchestration;
    @MockitoBean AppUserDetailsService userDetailsService;
    @MockitoBean SecurityAuditService audit;

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_RECRUITER", "ROLE_REVIEWER"})
    void permitsOnlyCvChatRolesForBothPhases(String role) throws Exception {
        when(orchestration.discover(anyString())).thenReturn(discoveryResponse());
        when(orchestration.planConfirmedScope(anyString(), anyList(), eq(21L), eq("tenant-a")))
                .thenReturn(outcome());

        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}")
                        .with(user(principal(role))).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chat/tools/confirmed-scope")
                        .contentType("application/json")
                        .content("{\"message\":\"Plan\",\"candidateIds\":[7],\"jobId\":21}")
                        .with(user(principal(role))).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void deniesReadOnlyUnauthenticatedAndMissingCsrfRequests() throws Exception {
        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}")
                        .with(user(principal("ROLE_READ_ONLY"))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/tools/confirmed-scope")
                        .contentType("application/json")
                        .content("{\"message\":\"Plan\",\"candidateIds\":[7]}")
                        .with(user(principal("ROLE_READ_ONLY"))).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}")
                        .with(user(principal("ROLE_RECRUITER"))))
                .andExpect(status().isForbidden());
    }

    private CvChatToolOrchestrationOutcome outcome() {
        return new CvChatToolOrchestrationOutcome(
                CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY, "Ready");
    }

    private CvChatToolDiscoveryResponse discoveryResponse() {
        return new CvChatToolDiscoveryResponse(
                CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED, "Clarify", null);
    }

    private AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(1L, "user@example.test", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
