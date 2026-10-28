package com.nevgiu.hrai.candidate.semantic;

import java.time.Instant;

/** Tenant-scoped semantic evidence returned to later chat/retrieval services. */
public record CvSemanticMatch(Long candidateId, Long documentId, Integer pageNumber, String content,
                              double similarity, Instant sourceVersion) { }
