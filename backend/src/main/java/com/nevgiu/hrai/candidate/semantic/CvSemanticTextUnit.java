package com.nevgiu.hrai.candidate.semantic;

/** One document-level native extraction or one reviewer-visible OCR page. */
record CvSemanticTextUnit(Integer pageNumber, String text) { }
