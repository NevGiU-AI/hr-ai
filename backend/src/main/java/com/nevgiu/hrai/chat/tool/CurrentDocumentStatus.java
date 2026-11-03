package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.CvIngestionStatus;

import java.time.Instant;

/** Operational state of the current CV document without filenames, storage keys, hashes, or extracted text. */
public record CurrentDocumentStatus(
        Long documentId,
        CvIngestionStatus status,
        CvExtractionMethod extractionMethod,
        Instant importedAt,
        Instant reprocessedAt
) { }
