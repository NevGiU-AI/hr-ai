package com.nevgiu.hrai.candidate.semantic;

import java.time.Instant;

/**
 * Tenant-scoped semantic evidence returned to later chat/retrieval services.
 *
 * @param candidateId verified owning candidate used for authorization and result grouping
 * @param documentId source document used to build a citation
 * @param pageNumber OCR page number, or {@code null} for native document-level extraction
 * @param content retrieved CV excerpt; sensitive personal data that must not be logged
 * @param similarity provider/vector-store similarity score
 * @param sourceVersion import or reprocessing timestamp used to detect stale indexes
 */
public record CvSemanticMatch(Long candidateId, Long documentId, Integer pageNumber, String content,
                              double similarity, Instant sourceVersion) { }
