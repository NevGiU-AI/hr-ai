package com.nevgiu.hrai.candidate.semantic;

/** Minimal ownership data for one missing or stale semantic index. */
record CvSemanticIndexTarget(Long documentId, String organizationId) { }
