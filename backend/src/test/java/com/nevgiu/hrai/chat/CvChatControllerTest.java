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
    @MockitoBean SecurityAuditService securityAuditService;

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
