package com.nevgiu.hrai.chat.tool;

/**
 * Minimized processing state for the newest document belonging to one confirmed candidate.
 *
 * <p>{@code currentDocument} is {@code null} when the candidate exists but has no document. The result deliberately
 * omits filenames, raw extraction failures, content hashes, storage keys, CV text, and tenant identifiers.</p>
 */
public record DocumentStatusResult(
        Long candidateId,
        CurrentDocumentStatus currentDocument
) { }
