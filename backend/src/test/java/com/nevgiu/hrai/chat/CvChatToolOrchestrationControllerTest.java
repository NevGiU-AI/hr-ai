package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
    void exposesOnlyValidatedDiscoveryOutcomeAndTenantScopedCards() throws Exception {
        CvChatLanguageScopeResponse candidateScope = new CvChatLanguageScopeResponse(
                "Find Ada and Grace", List.of(
                new CvChatLanguageScopeResponse.Reference("Ada", CvChatLanguageScopeResponse.Status.CHOICES,
                        List.of(new CvChatLanguageScopeResponse.CandidateOption(7L, "Ada Lovelace", "Engineer")),
                        false),
                new CvChatLanguageScopeResponse.Reference("Grace", CvChatLanguageScopeResponse.Status.CHOICES,
                        List.of(new CvChatLanguageScopeResponse.CandidateOption(8L, "Grace Hopper", null)),
                        false)));
        when(orchestration.discover("Find Ada and Grace")).thenReturn(new CvChatToolDiscoveryResponse(
                CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED,
                "Confirm the candidate cards.", candidateScope));

        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json")
                        .content("{\"message\":\"Find Ada and Grace\",\"organizationId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CANDIDATE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Confirm the candidate cards."))
                .andExpect(jsonPath("$.candidateScope.message").value("Find Ada and Grace"))
                .andExpect(jsonPath("$.candidateScope.references[0].requestedName").value("Ada"))
                .andExpect(jsonPath("$.candidateScope.references[0].status").value("CHOICES"))
                .andExpect(jsonPath("$.candidateScope.references[0].candidates[0].id").value(7))
                .andExpect(jsonPath("$.candidateScope.references[0].candidates[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.candidateIds").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.candidateScope.references[0].candidates[0].email").doesNotExist())
                .andExpect(jsonPath("$.candidateScope.references[0].candidates[0].cvText").doesNotExist());

        verify(orchestration).discover("Find Ada and Grace");
    }

    @Test
    void returnsNoCandidateScopeWhenDiscoveryDoesNotPermitConfirmation() throws Exception {
        when(orchestration.discover("Find Ada")).thenReturn(new CvChatToolDiscoveryResponse(
                CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED,
                "Please clarify the candidate names before continuing.", null));

        mvc.perform(post("/api/chat/tools/discover")
                        .contentType("application/json").content("{\"message\":\"Find Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CLARIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.candidateScope").doesNotExist());
    }

    @Test
    void returnsBoundedOrganizationEvidenceCardsWithoutAcceptingTenantContext() throws Exception {
        when(orchestration.discoverOrganization("Find AI developers")).thenReturn(
                new CvChatOrganizationDiscoveryResponse(
                        CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED,
                        "Confirm the shortlist.",
                        List.of(new CvChatOrganizationDiscoveryResponse.CandidateOption(
                                7L, "Ada", "AI Engineer", List.of("CV-17-P2"))), false));

        mvc.perform(post("/api/chat/tools/organization-discover")
                        .contentType("application/json")
                        .content("{\"message\":\"Find AI developers\",\"organizationId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CANDIDATE_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.candidates[0].id").value(7))
                .andExpect(jsonPath("$.candidates[0].evidenceReferences[0]").value("CV-17-P2"))
                .andExpect(jsonPath("$.organizationId").doesNotExist());

        verify(orchestration).discoverOrganization("Find AI developers");
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
