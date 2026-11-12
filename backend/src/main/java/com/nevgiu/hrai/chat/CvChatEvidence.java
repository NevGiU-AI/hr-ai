package com.nevgiu.hrai.chat;

/**
 * Minimized authorized evidence plus the server-confirmed display name used only to make answers readable.
 * Its content is personal data and must never be written to logs or telemetry.
 */
record CvChatEvidence(String reference, Long candidateId, Long documentId, Integer pageNumber,
                      String candidateName, String excerpt) {
    /** Compatibility constructor for synthetic fixtures that do not need a display label. */
    CvChatEvidence(String reference, Long candidateId, Long documentId, Integer pageNumber, String excerpt) {
        this(reference, candidateId, documentId, pageNumber, null, excerpt);
    }
}
