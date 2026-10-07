package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvDocumentSource;
import com.nevgiu.hrai.candidate.ingestion.dto.CvArchiveImportResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanner;
import com.nevgiu.hrai.candidate.storage.CvStorageProperties;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.QuarantinedCv;
import com.nevgiu.hrai.candidate.storage.StoredCv;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({CvIngestionService.class, CvDocumentImportService.class,
        CvArchiveTransactionIntegrationTest.TestBeans.class})
class CvArchiveTransactionIntegrationTest {

    private final CvIngestionService ingestionService;
    private final CandidateRepository candidateRepository;
    private final CvDocumentRepository documentRepository;

    @Autowired
    CvArchiveTransactionIntegrationTest(
            CvIngestionService ingestionService,
            CandidateRepository candidateRepository,
            CvDocumentRepository documentRepository
    ) {
        this.ingestionService = ingestionService;
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
    }

    @Test
    void rollsBackOnlyTheFailedArchiveEntry() throws Exception {
        byte[] archive = zip(
                new Entry("first.pdf", "%PDF-FIRST".getBytes(StandardCharsets.UTF_8)),
                new Entry("fails.pdf", "%PDF-FAIL".getBytes(StandardCharsets.UTF_8)),
                new Entry("third.pdf", "%PDF-THIRD".getBytes(StandardCharsets.UTF_8))
        );

        CvArchiveImportResult result = ingestionService.importArchive(
                new ByteArrayInputStream(archive), archive.length, CvDocumentSource.USER_UPLOAD, "tenant-a");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(candidateRepository.count()).isEqualTo(2);
        assertThat(documentRepository.count()).isEqualTo(2);
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

    private record Entry(String name, byte[] content) {
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        CvIngestionProperties cvIngestionProperties() {
            return new CvIngestionProperties(
                    1_000_000, 2_000_000, 10, 2_000_000, 100, 20,
                    "classpath:intial/CVs.zip", false);
        }

        @Bean
        CvStorageProperties cvStorageProperties() {
            return new CvStorageProperties("build/test-cv-storage", Duration.ofDays(365));
        }

        @Bean
        CvMalwareScanner cvMalwareScanner() {
            return content -> CvMalwareScanResult.CLEAN;
        }

        @Bean
        OriginalCvStorage originalCvStorage() {
            AtomicLong keys = new AtomicLong();
            return new OriginalCvStorage() {
                @Override
                public QuarantinedCv quarantine(String organizationId, byte[] content) {
                    return new QuarantinedCv("_quarantine/" + keys.incrementAndGet() + ".pdf");
                }

                @Override
                public StoredCv promote(String quarantineKey) {
                    return new StoredCv(quarantineKey.replace("_quarantine/", "stored/"), Instant.now());
                }

                @Override
                public void delete(String storageKey) {
                    // No filesystem is needed for this transaction-boundary test.
                }
            };
        }

        @Bean
        CvTextExtractor cvTextExtractor(CandidateRepository candidateRepository) {
            return content -> {
                String marker = new String(content, StandardCharsets.UTF_8);
                if (marker.contains("FAIL")) {
                    Candidate transientCandidate = new Candidate();
                    transientCandidate.setOrganizationId("tenant-a");
                    transientCandidate.setName("Must Roll Back");
                    transientCandidate.setCvText("transaction probe");
                    candidateRepository.saveAndFlush(transientCandidate);
                    throw new IllegalStateException("intentional per-entry failure");
                }
                return "Candidate candidate@example.com with enough experience for the configured minimum length.";
            };
        }
    }
}
