package com.nevgiu.hrai.candidate.semantic;

/** Bounded evidence chunk. Page number remains null for native document-level extraction. */
record CvSemanticChunk(int index, Integer pageNumber, String content) { }
