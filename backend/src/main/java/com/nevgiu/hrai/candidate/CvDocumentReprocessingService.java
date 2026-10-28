package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.ingestion.CvIngestionException;
import com.nevgiu.hrai.candidate.ingestion.CvIngestionProperties;
import com.nevgiu.hrai.candidate.ingestion.CvTextExtractionPipeline;
import com.nevgiu.hrai.candidate.ingestion.CvTextExtractionResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanException;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanResult;
import com.nevgiu.hrai.candidate.malware.CvMalwareScanner;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.semantic.CvSemanticIndexCoordinator;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

/** Rebuilds derived text from the existing original while preserving the original object and candidate identity. */
@Service
public class CvDocumentReprocessingService {
    private final CvDocumentRepository documents;
    private final CandidateRepository candidates;
    private final CandidateEvaluationRepository evaluations;
    private final OriginalCvStorage storage;
    private final CvMalwareScanner scanner;
    private final CvTextExtractionPipeline extractionPipeline;
    private final CvDocumentPageRepository pages;
    private final CvIngestionProperties properties;
    private final SecurityAuditService audit;
    private final CvOcrProperties ocrProperties;
    private final CvSemanticIndexCoordinator semanticIndexing;

    public CvDocumentReprocessingService(CvDocumentRepository documents, CandidateRepository candidates,
                                         CandidateEvaluationRepository evaluations, OriginalCvStorage storage,
                                         CvMalwareScanner scanner, CvTextExtractionPipeline extractionPipeline,
                                         CvDocumentPageRepository pages, CvIngestionProperties properties,
                                         CvOcrProperties ocrProperties, SecurityAuditService audit,
                                         CvSemanticIndexCoordinator semanticIndexing) {
        this.documents = documents;
        this.candidates = candidates;
        this.evaluations = evaluations;
        this.storage = storage;
        this.scanner = scanner;
        this.extractionPipeline = extractionPipeline;
        this.pages = pages;
        this.properties = properties;
        this.audit = audit;
        this.ocrProperties = ocrProperties;
        this.semanticIndexing = semanticIndexing;
    }

    /**
     * Reprocesses a tenant-owned stored original as one database transaction. Storage reads and ClamAV cannot
     * participate in the transaction, so no database mutation occurs until both external operations succeed.
     */
    @Transactional
    public void reprocess(Long documentId, AppUserPrincipal actor) {
        // The initial unlocked read discovers the candidate ID needed to preserve the global mutation lock order.
        CvDocument reference = documents.findByIdAndOrganizationId(documentId, actor.organizationId())
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "CV document not found"));
        if (reference.getStorageKey() == null || reference.getCandidate() == null) {
            throw failure(actor, reference, HttpStatus.CONFLICT,
                    "A stored original associated with a candidate is required");
        }

        // Candidate erasure acquires candidate then document locks. Use the same order to avoid lock inversion across
        // concurrent requests, while the second tenant-scoped lookup safely handles deletion after the initial read.
        Candidate candidate = candidates.findLockedByIdAndOrganizationId(
                        reference.getCandidate().getId(), actor.organizationId())
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Candidate not found"));
        CvDocument document = documents.findLockedByIdAndOrganizationId(documentId, actor.organizationId())
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "CV document not found"));
        if (document.getStorageKey() == null || document.getCandidate() == null
                || !candidate.getId().equals(document.getCandidate().getId())) {
            throw failure(actor, document, HttpStatus.CONFLICT,
                    "A stored original associated with a candidate is required");
        }
        byte[] content;
        try {
            content = storage.read(document.getStorageKey());
        } catch (OriginalCvStorageException exception) {
            throw failure(actor, document, HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }

        // Always rescan: older originals may predate scan provenance and malware signatures evolve over time.
        try {
            if (scanner.scan(content) == CvMalwareScanResult.INFECTED) {
                throw failure(actor, document, HttpStatus.UNPROCESSABLE_ENTITY, "CV failed security validation");
            }
        } catch (CvMalwareScanException exception) {
            throw failure(actor, document, HttpStatus.SERVICE_UNAVAILABLE, "CV security scanning is unavailable");
        }

        try {
            CvTextExtractionResult extraction = extractionPipeline.extract(content);
            String text = extraction.text();
            // Confidence is null for native PDF text, so the OCR threshold applies only when an
            // OCR provider produced a measurable score.
            boolean uncertainOcr = extraction.confidence() != null
                    && extraction.confidence() < ocrProperties.minimumConfidence();
            if (text.length() < properties.minimumTextLength() || uncertainOcr) {
                // Keep the last useful extraction untouched; OCR/manual review can replace it in a later workflow.
                throw failure(actor, document, HttpStatus.UNPROCESSABLE_ENTITY,
                        "Reprocessing produced too little text; manual review or OCR is required");
            }
            Instant now = Instant.now();
            document.setExtractedText(text);
            document.setTextLength(text.length());
            document.setExtractionMethod(extraction.method());
            document.setExtractionConfidence(extraction.confidence());
            document.setStatus(CvIngestionStatus.IMPORTED);
            document.setIngestionError(null);
            document.setReprocessedAt(now);
            // Human-corrected profile fields are preserved; only source-derived CV text is refreshed.
            candidate.setCvText(text);
            // Historical evaluations remain available for audit, but must no longer be treated as current evidence.
            int invalidated = evaluations.invalidateCurrentByCandidateIdAndOrganizationId(
                    candidate.getId(), actor.organizationId(), now);
            documents.save(document);
            candidates.save(candidate);
            // Replace provenance only after the new extraction passes quality checks. Any later
            // persistence failure rolls both this delete and the inserts back with the transaction.
            pages.deleteAllByDocumentId(documentId);
            extraction.pages().forEach(page -> {
                CvDocumentPage entity = new CvDocumentPage();
                entity.setDocument(document);
                entity.setPageNumber(page.pageNumber());
                entity.setExtractedText(page.text());
                entity.setConfidence(page.confidence());
                pages.save(entity);
            });
            // The coordinator observes this transaction and replaces vectors only after the refreshed evidence commits.
            semanticIndexing.request(documentId, actor.organizationId());
            audit.candidateAction(SecurityEventType.CV_DOCUMENT_REPROCESSED, SecurityEventOutcome.SUCCESS,
                    actor, candidate.getId(), "documentId=" + documentId + ";evaluationsInvalidated=" + invalidated);
        } catch (IOException exception) {
            throw failure(actor, document, HttpStatus.UNPROCESSABLE_ENTITY, "PDF text extraction failed");
        }
    }

    private CvIngestionException failure(AppUserPrincipal actor, CvDocument document,
                                         HttpStatus status, String message) {
        Long candidateId = document.getCandidate() == null ? null : document.getCandidate().getId();
        // Audit only safe IDs and status; never copy filenames, storage keys, signatures, or extracted text.
        audit.candidateAction(SecurityEventType.CV_DOCUMENT_REPROCESS_FAILED, SecurityEventOutcome.FAILURE,
                actor, candidateId, "documentId=" + document.getId() + ";status=" + status.value());
        return new CvIngestionException(status, message);
    }
}
