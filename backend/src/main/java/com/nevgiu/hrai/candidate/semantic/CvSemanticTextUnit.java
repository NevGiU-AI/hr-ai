package com.nevgiu.hrai.candidate.semantic;

/**
 * One extraction boundary supplied to the chunker.
 * Keeping OCR pages separate preserves page citations; native PDF extraction uses a {@code null} page number.
 */
record CvSemanticTextUnit(Integer pageNumber, String text) { }
