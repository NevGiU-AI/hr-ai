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
     */
    public record Document(
            Long id,
            String originalFilename,
            String contentType,
            long fileSize,
            CvDocumentSource source,
            CvIngestionStatus status,
            int textLength,
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

    /** PII snapshot is intentionally visible only through the tenant-scoped candidate details endpoint. */
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
