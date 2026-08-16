package com.nevgiu.hrai.security;

import com.nevgiu.hrai.candidate.CandidateController;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.ingestion.CvIngestionService;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidateController.class)
@Import(SecurityConfig.class)
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean CandidateRepository candidates;
    @MockitoBean CvIngestionService ingestion;
    @MockitoBean AppUserDetailsService userDetailsService;

    @Test
    void rejectsAnonymousBusinessApiRequests() throws Exception {
        mvc.perform(get("/api/candidates")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "RECRUITER")
    void rejectsInitialDatasetForRecruiters() throws Exception {
        mvc.perform(post("/api/candidates/import/initial").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "READ_ONLY")
    void rejectsBusinessMutationForReadOnlyUsers() throws Exception {
        mvc.perform(post("/api/candidates").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void allowsInitialDatasetForAdministrators() throws Exception {
        when(ingestion.importInitialArchive("tenant-a")).thenReturn(
                new CvArchiveImportResult(0, 0, 0, 0, 0, 0, List.of()));
        AppUserPrincipal principal = new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mvc.perform(post("/api/candidates/import/initial").with(user(principal)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsStateChangeWithoutCsrfToken() throws Exception {
        mvc.perform(post("/api/candidates/import/initial")).andExpect(status().isForbidden());
    }
}
