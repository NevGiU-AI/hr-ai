package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPage;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvDocumentSource;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import com.nevgiu.hrai.candidate.ingestion.dto.CvImportResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanException;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanner;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.storage.CvStorageProperties;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.storage.QuarantinedCv;
import com.nevgiu.hrai.candidate.storage.StoredCv;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CvDocumentImportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private final CandidateRepository candidateRepository;
    private final CvDocumentRepository documentRepository;
    private final CvTextExtractionPipeline extractionPipeline;
    private final CvDocumentPageRepository pageRepository;
    private final CvIngestionProperties properties;
    private final CvStorageProperties storageProperties;
    private final OriginalCvStorage originalCvStorage;
    private final CvMalwareScanner malwareScanner;
    private final CvOcrProperties ocrProperties;

    public CvDocumentImportService(
            CandidateRepository candidateRepository,
            CvDocumentRepository documentRepository,
            CvDocumentPageRepository pageRepository,
            CvTextExtractionPipeline extractionPipeline,
            CvIngestionProperties properties,
            CvStorageProperties storageProperties,
            OriginalCvStorage originalCvStorage,
            CvMalwareScanner malwareScanner,
            CvOcrProperties ocrProperties
    ) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.extractionPipeline = extractionPipeline;
        this.properties = properties;
        this.storageProperties = storageProperties;
        this.originalCvStorage = originalCvStorage;
        this.malwareScanner = malwareScanner;
        this.ocrProperties = ocrProperties;
    }

    /**
     * Imports one PDF in an independent transaction. Archive orchestration calls this separate Spring bean, ensuring
     * proxy-based transaction advice is applied and one failed entry cannot roll back successful entries.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CvImportResult importPdf(String filename, String contentType, byte[] content, CvDocumentSource source,
                                    String organizationId) {
        String safeFilename = validatePdf(filename, contentType, content);
        String hash = sha256(content);
        Optional<CvDocument> existing = documentRepository.findByOrganizationIdAndSha256(organizationId, hash);
        if (existing.isPresent()) {
            CvDocument document = existing.get();
            Long candidateId = document.getCandidate() == null ? null : document.getCandidate().getId();
            return new CvImportResult(candidateId, document.getId(), safeFilename, CvIngestionStatus.DUPLICATE,
                    PDF_CONTENT_TYPE, document.getTextLength(), List.of("Document content was already imported"));
        }

        CvDocument document = new CvDocument();
        document.setOrganizationId(organizationId);
        document.setOriginalFilename(safeFilename);
        document.setContentType(PDF_CONTENT_TYPE);
        document.setFileSize(content.length);
        document.setSha256(hash);
        document.setSource(source);

        QuarantinedCv quarantinedCv;
        try {
            quarantinedCv = originalCvStorage.quarantine(organizationId, content);
        } catch (OriginalCvStorageException e) {
            throw new CvIngestionException(HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }

        CvMalwareScanResult scanResult;
        try {
            scanResult = malwareScanner.scan(content);
        } catch (CvMalwareScanException e) {
            deleteQuarantined(quarantinedCv.storageKey());
            throw new CvIngestionException(HttpStatus.SERVICE_UNAVAILABLE, "CV security scanning is unavailable");
        }
        if (scanResult == CvMalwareScanResult.INFECTED) {
            deleteQuarantined(quarantinedCv.storageKey());
            throw new CvIngestionException(HttpStatus.UNPROCESSABLE_ENTITY, "CV failed security validation");
        }

        StoredCv storedCv;
        try {
            storedCv = originalCvStorage.promote(quarantinedCv.storageKey());
        } catch (OriginalCvStorageException e) {
            deleteQuarantined(quarantinedCv.storageKey());
            throw new CvIngestionException(HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }
        document.setStorageKey(storedCv.storageKey());
        document.setStoredAt(storedCv.storedAt());
        document.setRetentionUntil(storedCv.storedAt().plus(storageProperties.retention()));
        deleteStoredFileOnRollback(storedCv.storageKey());

        try {
            CvTextExtractionResult extraction = extractionPipeline.extract(content);
            String text = extraction.text();
            // Store how the current text was obtained. Native extraction has no confidence value;
            // OCR carries both document-level confidence and page-level provenance.
            document.setExtractedText(text);
            document.setTextLength(text.length());
            document.setExtractionMethod(extraction.method());
            document.setExtractionConfidence(extraction.confidence());

            List<String> warnings = new ArrayList<>();
            if (extraction.ocrAttemptFailed()) {
                // OCR infrastructure failure is non-destructive: the original remains governed and
                // the document is visible for retry or manual review.
                warnings.add("OCR is temporarily unavailable; manual review is required");
            }
            // A non-null confidence distinguishes OCR from native extraction. The configured
            // threshold is a workflow gate, not a claim that higher-scoring text is always correct.
            boolean uncertainOcr = extraction.confidence() != null
                    && extraction.confidence() < ocrProperties.minimumConfidence();
            if (text.length() < properties.minimumTextLength() || uncertainOcr) {
                document.setStatus(CvIngestionStatus.NEEDS_REVIEW);
                warnings.add(extraction.method() == com.nevgiu.hrai.candidate.CvExtractionMethod.OCR
                        ? "OCR output requires human review"
                        : "Little or no text was extracted; OCR or manual review may be required");
                CvDocument saved = documentRepository.save(document);
                saveOcrPages(saved, extraction);
                return new CvImportResult(null, saved.getId(), safeFilename, saved.getStatus(), PDF_CONTENT_TYPE,
                        saved.getTextLength(), warnings);
            }

            Candidate candidate = new Candidate();
            candidate.setOrganizationId(organizationId);
            candidate.setName(deriveName(safeFilename));
            candidate.setEmail(extractEmail(text));
            candidate.setCvText(text);
            candidate = candidateRepository.save(candidate);

            document.setCandidate(candidate);
            document.setStatus(CvIngestionStatus.IMPORTED);
            CvDocument saved = documentRepository.save(document);
            saveOcrPages(saved, extraction);
            return new CvImportResult(candidate.getId(), saved.getId(), safeFilename, saved.getStatus(), PDF_CONTENT_TYPE,
                    saved.getTextLength(), warnings);
        } catch (IOException e) {
            document.setStatus(CvIngestionStatus.FAILED);
            document.setIngestionError(e.getMessage());
            CvDocument saved = documentRepository.save(document);
            return new CvImportResult(null, saved.getId(), safeFilename, saved.getStatus(), PDF_CONTENT_TYPE, 0,
                    List.of("PDF text extraction failed"));
        } catch (RuntimeException e) {
            try {
                originalCvStorage.delete(storedCv.storageKey());
            } catch (OriginalCvStorageException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private void saveOcrPages(CvDocument document, CvTextExtractionResult extraction) {
        // Native extraction and failed OCR attempts provide an empty list, making this method a
        // no-op for documents without OCR provenance.
        extraction.pages().forEach(page -> {
            CvDocumentPage entity = new CvDocumentPage();
            entity.setDocument(document);
            entity.setPageNumber(page.pageNumber());
            entity.setExtractedText(page.text());
            entity.setConfidence(page.confidence());
            pageRepository.save(entity);
        });
    }

    private String validatePdf(String filename, String contentType, byte[] content) {
        String safeFilename = leafFilename(filename == null ? "cv.pdf" : filename);
        if (!safeFilename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new CvIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF files are supported");
        }
        if (content.length == 0) {
            throw new CvIngestionException(HttpStatus.BAD_REQUEST, "The PDF file is empty");
        }
        if (content.length > properties.maxPdfSize()) {
            throw new CvIngestionException(HttpStatus.PAYLOAD_TOO_LARGE, "PDF exceeds the configured size limit");
        }
        if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new CvIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content is not a PDF");
        }
        if (contentType != null && !contentType.isBlank()
                && !PDF_CONTENT_TYPE.equalsIgnoreCase(contentType)
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new CvIngestionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Uploaded content type is not PDF");
        }
        return safeFilename;
    }

    private void deleteQuarantined(String storageKey) {
        try {
            originalCvStorage.delete(storageKey);
        } catch (OriginalCvStorageException ignored) {
            // Preserve the scanner or promotion failure returned to the caller.
        }
    }

    private void deleteStoredFileOnRollback(String storageKey) {
        // PostgreSQL can roll back document metadata, but it cannot roll back a file already promoted on disk.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // There is no Spring transaction completion event to attach to outside an active synchronization context.
            return;
        }
        // Register a compensating filesystem action that runs only if the surrounding database transaction rolls back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    // delete() is idempotent for the filesystem provider, so explicit failure cleanup may safely overlap.
                    originalCvStorage.delete(storageKey);
                }
            }
        });
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String deriveName(String filename) {
        String leaf = leafFilename(filename);
        String base = leaf.substring(0, leaf.length() - 4)
                .replaceAll("(?i)([-_ ]?(resume|curriculum[-_ ]?vitae|cv))+$", "")
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (base.isBlank()) {
            return "Unknown Candidate";
        }
        StringBuilder result = new StringBuilder();
        for (String part : base.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String leafFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }
}
