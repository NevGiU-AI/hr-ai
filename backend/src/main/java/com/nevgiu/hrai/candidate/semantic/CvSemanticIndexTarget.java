package com.nevgiu.hrai.candidate.semantic;

/**
 * Minimal, non-PII work item returned by reconciliation.
 * Extracted text is loaded only when this item is actually processed.
 */
record CvSemanticIndexTarget(Long documentId, String organizationId) { }
