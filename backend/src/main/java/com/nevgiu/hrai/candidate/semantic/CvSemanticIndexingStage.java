package com.nevgiu.hrai.candidate.semantic;

/** Identifies the bounded semantic-index operation that failed without exposing CV or provider content. */
enum CvSemanticIndexingStage {
    DELETE_EXISTING,
    INSERT_REPLACEMENT
}
