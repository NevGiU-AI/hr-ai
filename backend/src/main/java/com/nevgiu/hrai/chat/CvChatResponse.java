package com.nevgiu.hrai.chat;

import java.util.List;

/**
 * Grounded answer plus only the bounded evidence excerpts actually referenced by the model.
 * Returning structured citations lets the frontend render links without parsing model-generated prose for identifiers.
 */
public record CvChatResponse(CvChatAnswerKind kind, String answer, List<Citation> citations, String responseLanguage) {
    public CvChatResponse(CvChatAnswerKind kind, String answer, List<Citation> citations) {
        this(kind, answer, citations, null);
    }

    static CvChatResponse controlled(CvChatAnswerKind kind, String english) {
        var localized = CvChatControlledText.resolve(kind, false, english);
        return new CvChatResponse(kind, localized.text(), List.of(), localized.language());
    }
    /** Page number is absent for native document-level evidence and present for page-preserving OCR evidence. */
    public record Citation(
            String reference,
            Long candidateId,
            Long documentId,
            Integer pageNumber,
            String excerpt
    ) { }
}
