package com.nevgiu.hrai.candidate.semantic;

import java.time.Instant;
import java.util.List;

/** Immutable tenant-owned extraction snapshot captured before the external embedding call starts. */
record CvSemanticSource(Long candidateId, Long documentId, String organizationId, Instant sourceVersion,
                        List<CvSemanticTextUnit> textUnits) { }
