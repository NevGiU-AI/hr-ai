package com.nevgiu.hrai.chat.tool;

/** Application-owned warning codes that reveal processing state without disclosing raw provider or parser errors. */
public enum IngestionWarning {
    NO_DOCUMENT_AVAILABLE,
    MANUAL_REVIEW_REQUIRED,
    INGESTION_FAILED,
    PROCESSING_WARNING_RECORDED
}
