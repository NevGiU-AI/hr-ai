package com.nevgiu.hrai.chat;

import java.util.List;

/**
 * Grounded answer plus only the bounded evidence excerpts actually referenced by the model.
 * Returning structured citations lets the frontend render links without parsing model-generated prose for identifiers.
 */
public record CvChatResponse(String answer, List<Citation> citations) {
    /** Page number is absent for native document-level evidence and present for page-preserving OCR evidence. */
    public record Citation(
            String reference,
            Long candidateId,
            Long documentId,
            Integer pageNumber,
            String excerpt
    ) { }
}
