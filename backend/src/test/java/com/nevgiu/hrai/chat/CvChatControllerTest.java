package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CvChatController.class, ApiExceptionHandler.class})
class CvChatControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvChatService service;
    @MockitoBean CvChatCandidateResolutionService candidateResolution;
    @MockitoBean CvChatComparisonService comparisonService;
    @MockitoBean CvChatLanguageScopeService languageScopeService;
    @MockitoBean SecurityAuditService securityAuditService;

    @Test
    void proposesCandidateScopeWithoutRetrievingEvidence() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(2L, "recruiter@example.com", "", "tenant-a", true,
                java.util.List.of());
        var response = new CvChatLanguageScopeResponse("Compare Ada and Grace", List.of(
                new CvChatLanguageScopeResponse.Reference("Ada", CvChatLanguageScopeResponse.Status.CHOICES,
                        List.of(new CvChatLanguageScopeResponse.CandidateOption(7L, "Ada Lovelace", "Engineer")),
                        false)));
        when(languageScopeService.propose("Compare Ada and Grace", "tenant-a")).thenReturn(response);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/candidates/scope")
                            .contentType("application/json")
                            .content("{\"message\":\"Compare Ada and Grace\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.references[0].requestedName").value("Ada"))
                    .andExpect(jsonPath("$.references[0].candidates[0].id").value(7));
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(languageScopeService).propose("Compare Ada and Grace", "tenant-a");
    }

    @Test
    void resolvesCandidateNamesInsideTheAuthenticatedOrganization() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(2L, "recruiter@example.com", "", "tenant-a", true,
                java.util.List.of());
        var response = new CvChatCandidateResolutionResponse(CvChatCandidateResolutionResponse.Status.RESOLVED,
                List.of(new CvChatCandidateResolutionResponse.CandidateOption(7L, "Ada Lovelace", "Engineer")), false);
        when(candidateResolution.resolve("Ada Lovelace", "tenant-a")).thenReturn(response);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/candidates/resolve")
                            .contentType("application/json")
                            .content("{\"name\":\"Ada Lovelace\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"))
                    .andExpect(jsonPath("$.candidates[0].id").value(7))
                    .andExpect(jsonPath("$.candidates[0].name").value("Ada Lovelace"))
                    .andExpect(jsonPath("$.candidates[0].currentTitle").value("Engineer"))
                    .andExpect(jsonPath("$.truncated").value(false))
                    .andExpect(jsonPath("$.candidates[0].email").doesNotExist())
                    .andExpect(jsonPath("$.candidates[0].organizationId").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(candidateResolution).resolve("Ada Lovelace", "tenant-a");
    }

    @Test
    void validatesCandidateResolutionName() throws Exception {
        mvc.perform(post("/api/chat/candidates/resolve")
                        .contentType("application/json")
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void searchesCandidateNamesInsideTheAuthenticatedOrganization() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(2L, "recruiter@example.com", "", "tenant-a", true,
                java.util.List.of());
        var response = new CvChatCandidateSearchResponse(List.of(
                new CvChatCandidateSearchResponse.CandidateOption(7L, "Fanon Jupkwo", "Engineer")), false);
        when(candidateResolution.search("Fanon", "tenant-a")).thenReturn(response);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/candidates/search")
                            .contentType("application/json")
                            .content("{\"query\":\"Fanon\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidates[0].id").value(7))
                    .andExpect(jsonPath("$.candidates[0].name").value("Fanon Jupkwo"))
                    .andExpect(jsonPath("$.candidates[0].email").doesNotExist())
                    .andExpect(jsonPath("$.truncated").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(candidateResolution).search("Fanon", "tenant-a");
    }

    @Test
    void validatesCandidateSearchQuery() throws Exception {
        mvc.perform(post("/api/chat/candidates/search")
                        .contentType("application/json")
                        .content("{\"query\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.query").exists());
    }

    @Test
    void comparesExplicitCandidatesInsideTheAuthenticatedOrganization() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(2L, "recruiter@example.com", "", "tenant-a", true,
                java.util.List.of());
        var response = new CvChatComparisonResponse("Comparison [CV-10] [CV-11]", List.of(
                new CvChatComparisonResponse.CandidateOption(7L, "Ada", "Engineer"),
                new CvChatComparisonResponse.CandidateOption(8L, "Grace", "Architect")), List.of());
        when(comparisonService.compare(List.of(7L, 8L), "Compare", "tenant-a")).thenReturn(response);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/candidates/compare")
                            .contentType("application/json")
                            .content("{\"candidateIds\":[7,8],\"message\":\"Compare\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.candidates[0].id").value(7))
                    .andExpect(jsonPath("$.candidates[1].id").value(8))
                    .andExpect(jsonPath("$.candidates[0].email").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
        verify(comparisonService).compare(List.of(7L, 8L), "Compare", "tenant-a");
    }

    @Test
    void validatesCandidateComparisonBounds() throws Exception {
        mvc.perform(post("/api/chat/candidates/compare")
                        .contentType("application/json")
                        .content("{\"candidateIds\":[7],\"message\":\"Compare\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.candidateIds").exists());
    }

    @Test
    void passesAuthenticatedOrganizationToTheChatService() throws Exception {
        AppUserPrincipal principal = new AppUserPrincipal(2L, "recruiter@example.com", "", "tenant-a", true,
                java.util.List.of());
        CvChatResponse response = new CvChatResponse("Answer [CV-3]",
                List.of(new CvChatResponse.Citation("CV-3", 7L, 3L, null, "Evidence")));
        when(service.ask(7L, "Question", "tenant-a")).thenReturn(response);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/candidates/7")
                            .contentType("application/json")
                            .content("{\"message\":\"Question\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("Answer [CV-3]"))
                    .andExpect(jsonPath("$.citations[0].reference").value("CV-3"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(service).ask(7L, "Question", "tenant-a");
    }

    @Test
    void validatesMessageLengthAndPresence() throws Exception {
        mvc.perform(post("/api/chat/candidates/7")
                        .contentType("application/json")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.message").exists());
    }
}
