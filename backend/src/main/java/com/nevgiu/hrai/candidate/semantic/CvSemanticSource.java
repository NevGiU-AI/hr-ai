package com.nevgiu.hrai.candidate.semantic;

import java.time.Instant;
import java.util.List;

/**
 * Immutable tenant-owned extraction snapshot captured before the external embedding call starts.
 * Copying the required values out of JPA entities avoids lazy loading and long database transactions during provider
 * calls.
 */
record CvSemanticSource(Long candidateId, Long documentId, String organizationId, Instant sourceVersion,
                        List<CvSemanticTextUnit> textUnits) { }
