package com.nevgiu.hrai.candidate;

import java.time.Instant;
import java.util.List;

/**
 * Read model for the candidate workspace. It intentionally omits raw CV text, extraction failures, content hashes,
 * storage keys, and the organization identifier so internal processing and storage data cannot leak through this API.
 */
public record CandidateDetailsResponse(
        Long id,
        String name,
        String email,
        String location,
        String currentTitle,
        Integer yearsExperience,
        Instant createdAt,
        List<Document> documents,
        List<Revision> revisions,
        boolean correctionAllowed
) {
    /**
     * Describes an ingestion attempt and the actions the current principal may be offered. The capability booleans are
     * presentation hints only; the original-file and legal-hold endpoints independently enforce authorization.
     * Extraction confidence is nullable because embedded PDF text has no OCR confidence score; the page count reports
     * available OCR provenance without exposing the page text in this summary response.
     */
    public record Document(
            Long id,
            String originalFilename,
            String contentType,
            long fileSize,
            CvDocumentSource source,
            CvIngestionStatus status,
            int textLength,
            CvExtractionMethod extractionMethod,
            Double extractionConfidence,
            long extractionPageCount,
            Instant importedAt,
            Instant reprocessedAt,
            boolean originalAvailable,
            Instant storedAt,
            Instant retentionUntil,
            boolean legalHold,
            Instant legalHoldAt,
            boolean downloadAllowed,
            boolean administrationAllowed
    ) { }

    /**
     * This snapshot contains personally identifiable information (PII), including the candidate's name, email,
     * location, job title, and experience. It is therefore exposed only through the authenticated, tenant-scoped
     * candidate-details endpoint, which prevents users from reading revision history from another organization.
     */
    public record Revision(
            Long id,
            String actorEmail,
            String name,
            String email,
            String location,
            String currentTitle,
            Integer yearsExperience,
            Instant createdAt
    ) { }
}
