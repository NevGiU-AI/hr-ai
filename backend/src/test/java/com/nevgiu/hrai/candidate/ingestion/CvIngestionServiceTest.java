package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentPage;
import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvDocumentSource;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import com.nevgiu.hrai.candidate.ingestion.dto.CvImportResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanException;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanner;
import com.nevgiu.hrai.candidate.ocr.CvOcrEngine;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;
import com.nevgiu.hrai.candidate.storage.CvStorageProperties;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.QuarantinedCv;
import com.nevgiu.hrai.candidate.storage.StoredCv;
import com.nevgiu.hrai.candidate.semantic.CvSemanticIndexCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvIngestionServiceTest {

    private CandidateRepository candidateRepository;
    private CvDocumentRepository documentRepository;
    private OriginalCvStorage originalCvStorage;
    private CvMalwareScanner malwareScanner;
    private CvDocumentPageRepository pageRepository;
    private CvDocumentImportService documentImportService;
    private CvSemanticIndexCoordinator semanticIndexing;
    private CvIngestionService service;
    private final AtomicLong ids = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateRepository.class);
        documentRepository = mock(CvDocumentRepository.class);
        originalCvStorage = mock(OriginalCvStorage.class);
        malwareScanner = mock(CvMalwareScanner.class);
        pageRepository = mock(CvDocumentPageRepository.class);
        semanticIndexing = mock(CvSemanticIndexCoordinator.class);
        CvTextExtractor extractor = content -> "Ada Lovelace\nada@example.com\nSoftware engineer with extensive analytical experience.";
        CvIngestionProperties properties = new CvIngestionProperties(
                1_000_000, 2_000_000, 10, 2_000_000, 100, 50,
                "classpath:intial/CVs.zip", true);
        service = service(extractor, properties);

        when(documentRepository.findByOrganizationIdAndSha256(any(), any())).thenReturn(Optional.empty());
        when(originalCvStorage.quarantine(any(), any())).thenReturn(new QuarantinedCv("_quarantine/tenant-key/document.pdf"));
        when(originalCvStorage.promote(any())).thenReturn(
                new StoredCv("tenant-key/document.pdf", Instant.parse("2026-09-19T10:00:00Z")));
        when(malwareScanner.scan(any())).thenReturn(CvMalwareScanResult.CLEAN);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate candidate = invocation.getArgument(0);
            candidate.setId(ids.getAndIncrement());
            return candidate;
        });
        when(documentRepository.save(any(CvDocument.class))).thenAnswer(invocation -> {
            CvDocument document = invocation.getArgument(0);
            // Repository mocks do not generate database IDs; indexing needs the persisted document identity.
            if (document.getId() == null) ReflectionTestUtils.setField(document, "id", ids.getAndIncrement());
            return document;
        });
    }

    @Test
    void importsPdfAndCreatesCandidate() {
        CvImportResult result = service.importPdf(
                "ada-lovelace-resume.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a");

        assertThat(result.status()).isEqualTo(CvIngestionStatus.IMPORTED);
        assertThat(result.candidateId()).isNotNull();
        assertThat(result.textLength()).isGreaterThan(50);
        verify(originalCvStorage).quarantine(org.mockito.ArgumentMatchers.eq("tenant-a"), any(byte[].class));
        verify(malwareScanner).scan(any(byte[].class));
        verify(originalCvStorage).promote("_quarantine/tenant-key/document.pdf");
        ArgumentCaptor<CvDocument> document = ArgumentCaptor.forClass(CvDocument.class);
        verify(documentRepository).save(document.capture());
        assertThat(document.getValue().getStorageKey()).isEqualTo("tenant-key/document.pdf");
        assertThat(document.getValue().getStoredAt()).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
        assertThat(document.getValue().getRetentionUntil()).isEqualTo(Instant.parse("2027-09-19T10:00:00Z"));
        verify(semanticIndexing).request(result.documentId(), "tenant-a");
    }

    @Test
    void rejectsNonPdfContent() {
        assertThatThrownBy(() -> service.importPdf(
                "candidate.pdf", "application/pdf", "not-pdf".getBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .satisfies(error -> assertThat(((CvIngestionException) error).getStatus())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void archiveContinuesPastUnsupportedEntry() throws Exception {
        byte[] archive = zip(
                new Entry("CVs/candidate.pdf", minimalPdfBytes()),
                new Entry("CVs/readme.txt", "ignore".getBytes())
        );

        CvArchiveImportResult result = service.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a");

        assertThat(result.totalFiles()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void archiveRejectsPathTraversal() throws Exception {
        byte[] archive = zip(new Entry("../candidate.pdf", minimalPdfBytes()));

        assertThatThrownBy(() -> service.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .hasMessage("ZIP archive contains path traversal");
    }

    @Test
    void archiveRejectsWindowsAbsolutePath() throws Exception {
        byte[] archive = zip(new Entry("C:/private/candidate.pdf", minimalPdfBytes()));

        assertThatThrownBy(() -> service.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .hasMessage("ZIP archive contains an absolute path");
    }

    @Test
    void archiveRejectsTooManyEntries() throws Exception {
        Entry[] entries = new Entry[11];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Entry("CVs/" + i + ".txt", "ignored".getBytes());
        }
        byte[] archive = zip(entries);

        assertThatThrownBy(() -> service.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .hasMessage("ZIP archive contains too many files");
    }

    @Test
    void rejectsArchiveLargerThanConfiguredLimitBeforeReading() {
        assertThatThrownBy(() -> service.importArchive(
                new ByteArrayInputStream(new byte[0]), 2_000_001, CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .satisfies(error -> assertThat(((CvIngestionException) error).getStatus())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void lowTextPdfIsStoredForReviewWithoutCreatingCandidate() {
        CvTextExtractor shortExtractor = content -> "short";
        CvIngestionProperties properties = new CvIngestionProperties(
                1_000_000, 2_000_000, 10, 2_000_000, 100, 50,
                "classpath:intial/CVs.zip", true);
        CvIngestionService reviewService = service(shortExtractor, properties);

        CvImportResult result = reviewService.importPdf(
                "scan.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a");

        assertThat(result.status()).isEqualTo(CvIngestionStatus.NEEDS_REVIEW);
        assertThat(result.candidateId()).isNull();
        assertThat(result.warnings()).isNotEmpty();
        verify(semanticIndexing, org.mockito.Mockito.never()).request(any(), any());
    }

    @Test
    void importsConfidentOcrTextAndPersistsPageProvenance() {
        CvIngestionProperties properties = new CvIngestionProperties(
                1_000_000, 2_000_000, 10, 2_000_000, 100, 50,
                "classpath:intial/CVs.zip", true);
        CvOcrEngine ocr = mock(CvOcrEngine.class);
        when(ocr.extract(any())).thenReturn(new CvOcrResult(
                "Ada Lovelace ada@example.com experienced analytical software engineer", 91,
                java.util.List.of(new CvOcrResult.Page(1, "Ada Lovelace", 91))));
        CvIngestionService ocrService = service(content -> "", properties, ocr, true);

        CvImportResult result = ocrService.importPdf(
                "scan.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a");

        assertThat(result.status()).isEqualTo(CvIngestionStatus.IMPORTED);
        ArgumentCaptor<CvDocument> document = ArgumentCaptor.forClass(CvDocument.class);
        verify(documentRepository).save(document.capture());
        assertThat(document.getValue().getExtractionMethod()).isEqualTo(CvExtractionMethod.OCR);
        assertThat(document.getValue().getExtractionConfidence()).isEqualTo(91);
        ArgumentCaptor<CvDocumentPage> page = ArgumentCaptor.forClass(CvDocumentPage.class);
        verify(pageRepository).save(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void rejectsPdfEntryLargerThanConfiguredLimit() throws Exception {
        CvIngestionProperties properties = new CvIngestionProperties(
                8, 2_000_000, 10, 2_000_000, 100, 1,
                "classpath:intial/CVs.zip", true);
        CvIngestionService limitedService = service(content -> "valid extracted text", properties);
        byte[] archive = zip(new Entry("candidate.pdf", minimalPdfBytes()));

        assertThatThrownBy(() -> limitedService.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .satisfies(error -> assertThat(((CvIngestionException) error).getStatus())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsInfectedPdfAndDeletesQuarantinedCopy() {
        when(malwareScanner.scan(any())).thenReturn(CvMalwareScanResult.INFECTED);

        assertThatThrownBy(() -> service.importPdf(
                "candidate.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .satisfies(error -> assertThat(((CvIngestionException) error).getStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(originalCvStorage).delete("_quarantine/tenant-key/document.pdf");
    }

    @Test
    void failsClosedAndDeletesQuarantinedCopyWhenScannerIsUnavailable() {
        when(malwareScanner.scan(any())).thenThrow(new CvMalwareScanException("unavailable"));

        assertThatThrownBy(() -> service.importPdf(
                "candidate.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(CvIngestionException.class)
                .satisfies(error -> assertThat(((CvIngestionException) error).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verify(originalCvStorage).delete("_quarantine/tenant-key/document.pdf");
    }

    @Test
    void deletesStoredOriginalWhenUnexpectedProcessingFails() {
        CvIngestionService failingService = service(content -> {
            throw new IllegalStateException("processing failed");
        }, new CvIngestionProperties(
                1_000_000, 2_000_000, 10, 2_000_000, 100, 50,
                "classpath:intial/CVs.zip", true));

        assertThatThrownBy(() -> failingService.importPdf(
                "candidate.pdf", "application/pdf", minimalPdfBytes(), CvDocumentSource.USER_UPLOAD, "tenant-a"))
                .isInstanceOf(IllegalStateException.class);

        verify(originalCvStorage).delete("tenant-key/document.pdf");
    }

    private byte[] minimalPdfBytes() {
        return "%PDF-1.4\n%%EOF".getBytes();
    }

    private CvIngestionService service(CvTextExtractor extractor, CvIngestionProperties properties) {
        return service(extractor, properties, mock(CvOcrEngine.class), false);
    }

    private CvIngestionService service(CvTextExtractor extractor, CvIngestionProperties properties,
                                       CvOcrEngine ocrEngine, boolean ocrEnabled) {
        CvOcrProperties ocrProperties = new CvOcrProperties(
                ocrEnabled, "tesseract", "eng+fra+por", 300, 30, Duration.ofSeconds(30), 70);
        CvTextExtractionPipeline pipeline = new CvTextExtractionPipeline(
                extractor, ocrEngine, properties, ocrProperties);
        documentImportService = new CvDocumentImportService(candidateRepository, documentRepository, pageRepository,
                pipeline, properties,
                new CvStorageProperties("build/test-cv-storage", Duration.ofDays(365)),
                originalCvStorage, malwareScanner, ocrProperties);
        return new CvIngestionService(properties, documentImportService, new DefaultResourceLoader(), semanticIndexing);
    }

    private byte[] zip(Entry... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record Entry(String name, byte[] content) {}
}
