package com.nevgiu.hrai.candidate.semantic;

/**
 * Bounded evidence unit sent to the embedding model and stored as one Spring AI document.
 *
 * @param index zero-based position within the source document; it also participates in the deterministic vector ID
 * @param pageNumber reviewer-visible OCR page, or {@code null} when native extraction has document-level provenance
 * @param content normalized text embedded and later returned as cited retrieval evidence
 */
record CvSemanticChunk(int index, Integer pageNumber, String content) { }
