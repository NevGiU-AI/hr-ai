package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvChatToolOrchestrationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CvChatToolOrchestrationController.class, ApiExceptionHandler.class})
class CvChatToolOrchestrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvChatToolOrchestrationService orchestration;
    @MockitoBean SecurityAuditService securityAuditService;

    @Test
    void exposesOnlyValidatedDiscoveryOutcomeFields() throws Exception {
        when(orchestration.discover("Find Ada and Grace")).thenReturn(new CvChatToolOrchestrationOutcome(
                CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED,
                "Confirm the candidate cards."));

        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json")
                        .content("{\"message\":\"Find Ada and Grace\",\"organizationId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CANDIDATE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Confirm the candidate cards."))
                .andExpect(jsonPath("$.candidateIds").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        verify(orchestration).discover("Find Ada and Grace");
    }

    @Test
    void usesAuthenticatedOrganizationForConfirmedScopeAndIgnoresInjectedContext() throws Exception {
        AppUserPrincipal principal = principal();
        when(orchestration.planConfirmedScope("Plan review", List.of(7L, 9L), 21L, "tenant-a"))
                .thenReturn(new CvChatToolOrchestrationOutcome(
                        CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY, "Scope is ready."));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/tools/confirmed-scope")
                            .contentType("application/json")
                            .content("""
                                    {"message":"Plan review","candidateIds":[7,9],"jobId":21,
                                     "organizationId":"attacker","roles":["ADMIN"]}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kind").value("CONFIRMED_SCOPE_READY"))
                    .andExpect(jsonPath("$.message").value("Scope is ready."))
                    .andExpect(jsonPath("$.candidateIds").doesNotExist())
                    .andExpect(jsonPath("$.jobId").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(orchestration).planConfirmedScope("Plan review", List.of(7L, 9L), 21L, "tenant-a");
    }

    @Test
    void validatesDiscoveryAndConfirmedScopeBeforeOrchestration() throws Exception {
        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.message").exists());

        mvc.perform(post("/api/chat/tools/confirmed-scope")
                        .contentType("application/json")
                        .content("{\"message\":\"Plan\",\"candidateIds\":[7,7],\"jobId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.jobId").exists());

        verifyNoInteractions(orchestration);
    }

    @Test
    void preservesStructuredProviderFailureStatus() throws Exception {
        when(orchestration.discover("Find Ada")).thenThrow(
                new CvChatException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "CV chat tool orchestration is unavailable"));

        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("CV chat tool orchestration is unavailable"));
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(1L, "reviewer@example.test", "", "tenant-a", true, List.of());
    }
}
