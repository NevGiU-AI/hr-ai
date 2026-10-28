package com.nevgiu.hrai.candidate.semantic;

/**
 * Minimal work item containing no personally identifiable information (PII), returned by reconciliation.
 * Extracted text is loaded only when this item is actually processed.
 */
record CvSemanticIndexTarget(Long documentId, String organizationId) { }
