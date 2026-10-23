package com.nevgiu.hrai.chat;

import java.util.List;

/** Grounded answer plus the exact, bounded evidence excerpts referenced by the model. */
public record CvChatResponse(String answer, List<Citation> citations) {
    public record Citation(
            String reference,
            Long candidateId,
            Long documentId,
            Integer pageNumber,
            String excerpt
    ) { }
}
