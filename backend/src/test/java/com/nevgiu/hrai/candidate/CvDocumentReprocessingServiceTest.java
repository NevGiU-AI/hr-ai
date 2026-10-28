package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.ingestion.CvIngestionProperties;
import com.nevgiu.hrai.candidate.ingestion.CvTextExtractor;
import com.nevgiu.hrai.candidate.ingestion.CvTextExtractionPipeline;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanner;
import com.nevgiu.hrai.candidate.ocr.CvOcrEngine;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.semantic.CvSemanticIndexCoordinator;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvDocumentReprocessingServiceTest {
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateEvaluationRepository evaluations = mock(CandidateEvaluationRepository.class);
    private final OriginalCvStorage storage = mock(OriginalCvStorage.class);
    private final CvMalwareScanner scanner = mock(CvMalwareScanner.class);
    private final CvTextExtractor extractor = mock(CvTextExtractor.class);
    private final CvOcrEngine ocrEngine = mock(CvOcrEngine.class);
    private final CvDocumentPageRepository pages = mock(CvDocumentPageRepository.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CvSemanticIndexCoordinator semanticIndexing = mock(CvSemanticIndexCoordinator.class);
    private final CvIngestionProperties properties = new CvIngestionProperties(
            20_000_000, 100_000_000, 100, 200_000_000, 100, 10, "initial.zip", false);
    private final CvOcrProperties ocrProperties = new CvOcrProperties(
            false, "tesseract", "eng+fra+por", 300, 30, Duration.ofSeconds(30), 70);
    private final CvTextExtractionPipeline pipeline = new CvTextExtractionPipeline(
            extractor, ocrEngine, properties, ocrProperties);
    private final CvDocumentReprocessingService service = new CvDocumentReprocessingService(
            documents, candidates, evaluations, storage, scanner, pipeline, pages, properties, ocrProperties, audit,
            semanticIndexing);

    @Test
    void rescansAndRefreshesExistingCandidateWithoutOverwritingCorrectedMetadata() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setOrganizationId("tenant-a");
        candidate.setName("Human corrected name");
        CvDocument document = new CvDocument();
        document.setOrganizationId("tenant-a");
        document.setCandidate(candidate);
        document.setStorageKey("opaque-key");
        when(documents.findByIdAndOrganizationId(9L, "tenant-a")).thenReturn(Optional.of(document));
        when(documents.findLockedByIdAndOrganizationId(9L, "tenant-a")).thenReturn(Optional.of(document));
        when(candidates.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(storage.read("opaque-key")).thenReturn("%PDF-content".getBytes());
        when(scanner.scan(any())).thenReturn(CvMalwareScanResult.CLEAN);
        when(extractor.extract(any())).thenReturn("Long enough refreshed extracted CV text");

        service.reprocess(9L, principal());

        assertThat(candidate.getName()).isEqualTo("Human corrected name");
        assertThat(candidate.getCvText()).contains("refreshed extracted CV text");
        assertThat(document.getReprocessedAt()).isNotNull();
        verify(evaluations).invalidateCurrentByCandidateIdAndOrganizationId(eq(7L), eq("tenant-a"), any());
        verify(semanticIndexing).request(9L, "tenant-a");
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
