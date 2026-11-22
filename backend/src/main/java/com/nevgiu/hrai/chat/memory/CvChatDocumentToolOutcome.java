package com.nevgiu.hrai.chat.memory;

/** Structured provider decision; Java renders all recruiter-visible operational facts. */
record CvChatDocumentToolOutcome(Kind kind) {
    enum Kind { NOT_APPLICABLE, DOCUMENT_STATUS, INGESTION_WARNINGS, DOCUMENT_STATUS_AND_WARNINGS, EVALUATION_HISTORY }
}
