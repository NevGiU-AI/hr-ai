package com.nevgiu.hrai.chat.tool;

/** Minimized job-relevant candidate data; CV evidence remains available only through grounded retrieval services. */
public record CandidateSummary(
        Long id,
        String name,
        String currentTitle,
        Integer yearsExperience,
        CurrentDocumentStatus currentDocument
) { }
