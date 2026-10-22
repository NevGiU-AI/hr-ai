package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserDetailsService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.SecurityConfig;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CvOriginalController.class)
@Import(SecurityConfig.class)
class CvOriginalSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CvOriginalService originals;
    @MockitoBean CvLegalHoldService legalHolds;
    @MockitoBean CvRetentionService retention;
    @MockitoBean CvDocumentReprocessingService reprocessing;
    @MockitoBean AppUserDetailsService userDetailsService;
    @MockitoBean SecurityAuditService audit;

    @Test
    void allowsRecruiterToDownloadOriginal() throws Exception {
        AppUserPrincipal recruiter = principal("ROLE_RECRUITER");
        when(originals.download(7L, recruiter)).thenReturn(
                new CvOriginalDownload("candidate.pdf", "application/pdf", 3, "pdf".getBytes()));

        mvc.perform(get("/api/cv-documents/7/original").with(user(recruiter)))
                .andExpect(status().isOk());
    }

    @Test
    void deniesReadOnlyOriginalDownload() throws Exception {
        mvc.perform(get("/api/cv-documents/7/original").with(user(principal("ROLE_READ_ONLY"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesRecruiterOriginalDeletion() throws Exception {
        mvc.perform(delete("/api/cv-documents/7/original")
                        .with(user(principal("ROLE_RECRUITER"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdministratorOriginalDeletion() throws Exception {
        mvc.perform(delete("/api/cv-documents/7/original")
                        .with(user(principal("ROLE_ADMIN"))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void allowsRecruiterToReprocessOriginal() throws Exception {
        mvc.perform(post("/api/cv-documents/7/reprocess")
                        .with(user(principal("ROLE_RECRUITER"))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deniesReadOnlyReprocessing() throws Exception {
        mvc.perform(post("/api/cv-documents/7/reprocess")
                        .with(user(principal("ROLE_READ_ONLY"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOnlyAdministratorToManageLegalHold() throws Exception {
        mvc.perform(put("/api/cv-documents/7/legal-hold")
                        .contentType("application/json").content("{\"held\":true}")
                        .with(user(principal("ROLE_ADMIN"))).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(put("/api/cv-documents/7/legal-hold")
                        .contentType("application/json").content("{\"held\":true}")
                        .with(user(principal("ROLE_RECRUITER"))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsLegalHoldRequestWithoutExplicitState() throws Exception {
        mvc.perform(put("/api/cv-documents/7/legal-hold")
                        .contentType("application/json").content("{}")
                        .with(user(principal("ROLE_ADMIN"))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(1L, "user@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
