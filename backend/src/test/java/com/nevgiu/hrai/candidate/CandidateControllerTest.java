package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.ingestion.CvIngestionException;
import com.nevgiu.hrai.candidate.ingestion.CvIngestionService;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import com.nevgiu.hrai.candidate.ingestion.dto.CvImportResult;
import com.nevgiu.hrai.web.ApiExceptionHandler;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CandidateController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CandidateController.class, ApiExceptionHandler.class})
class CandidateControllerTest {

    private static final AppUserPrincipal PRINCIPAL = new AppUserPrincipal(1L, "admin@example.com", "hash",
            "tenant-a", true, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @Autowired MockMvc mvc;
    @MockitoBean CandidateRepository candidateRepository;
    @MockitoBean CvIngestionService ingestionService;
    @MockitoBean SecurityAuditService securityAuditService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importsMultipartPdfWithCreatedStatus() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ada.pdf", "application/pdf", "%PDF-test".getBytes());
        when(ingestionService.importPdf(any(), anyString())).thenReturn(new CvImportResult(
                3L, 8L, "ada.pdf", CvIngestionStatus.IMPORTED, "application/pdf", 120, List.of()));

        mvc.perform(multipart("/api/candidates/import").file(file).with(user(PRINCIPAL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(3))
                .andExpect(jsonPath("$.status").value("IMPORTED"));
    }

    @Test
    void rejectsImportWithoutFile() throws Exception {
        mvc.perform(multipart("/api/candidates/import").with(user(PRINCIPAL)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsTypedIngestionError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "bad".getBytes());
        when(ingestionService.importPdf(any(), anyString())).thenThrow(
                new CvIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content is not a PDF"));

        mvc.perform(multipart("/api/candidates/import").file(file).with(user(PRINCIPAL)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("File content is not a PDF"));
    }

    @Test
    void loadsInitialArchive() throws Exception {
        when(ingestionService.importInitialArchive("tenant-a")).thenReturn(
                new CvArchiveImportResult(2, 1, 1, 0, 0, 0, List.of()));

        mvc.perform(post("/api/candidates/import/initial").with(user(PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiles").value(2))
                .andExpect(jsonPath("$.duplicates").value(1));
    }
}
