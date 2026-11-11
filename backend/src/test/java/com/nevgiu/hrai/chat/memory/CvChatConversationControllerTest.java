package com.nevgiu.hrai.chat.memory;

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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvChatConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CvChatConversationController.class, ApiExceptionHandler.class})
class CvChatConversationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvChatConversationService service;
    @MockitoBean CvChatConversationTurnService turns;
    @MockitoBean SecurityAuditService securityAuditService;

    @Test
    void createsConversationFromValidatedIdsWithoutAcceptingTenantOrOwnerFields() throws Exception {
        var actor = actor();
        Instant now = Instant.parse("2026-11-11T05:00:00Z");
        when(service.create(any(), any())).thenReturn(new CvChatConversationResponse(
                "00000000-0000-0000-0000-000000000001", 0,
                List.of(new CvChatConversationResponse.CandidateCard(7L, "Ada", "Engineer")),
                null, now, now, now.plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/conversations")
                            .contentType("application/json")
                            .content("{\"candidateIds\":[7],\"jobId\":null," +
                                    "\"organizationId\":\"attacker\",\"ownerUserId\":999}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
                    .andExpect(jsonPath("$.candidates[0].id").value(7))
                    .andExpect(jsonPath("$.organizationId").doesNotExist())
                    .andExpect(jsonPath("$.ownerUserId").doesNotExist());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rejectsMoreThanFiveCandidates() throws Exception {
        mvc.perform(post("/api/chat/conversations")
                        .contentType("application/json")
                        .content("{\"candidateIds\":[1,2,3,4,5,6]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.candidateIds").exists());
    }

    @Test
    void executesFollowUpWithoutAcceptingCandidateJobOrTenantOverrides() throws Exception {
        var actor = actor();
        Instant expiry = Instant.parse("2026-11-12T05:00:00Z");
        when(turns.ask(any(), any(), any())).thenReturn(new CvChatConversationTurnResponse(
                "conversation-1", 2, "Grounded answer [CV-7].",
                List.of(new CvChatConversationResponse.CandidateCard(7L, "Ada", "Engineer")),
                null, List.of(new com.nevgiu.hrai.chat.CvChatResponse.Citation(
                        "CV-7", 7L, 7L, null, "Evidence")), expiry));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
        try {
            mvc.perform(post("/api/chat/conversations/conversation-1/messages")
                            .contentType("application/json")
                            .content("{\"message\":\"What about Spring?\",\"candidateIds\":[999],"
                                    + "\"jobId\":999,\"organizationId\":\"attacker\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conversationId").value("conversation-1"))
                    .andExpect(jsonPath("$.answer").value("Grounded answer [CV-7]."))
                    .andExpect(jsonPath("$.candidates[0].id").value(7))
                    .andExpect(jsonPath("$.citations[0].candidateId").value(7));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private AppUserPrincipal actor() {
        return new AppUserPrincipal(1L, "owner@example.com", "hash", "tenant-a", true, List.of());
    }
}
