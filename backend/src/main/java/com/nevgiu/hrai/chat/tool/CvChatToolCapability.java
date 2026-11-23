package com.nevgiu.hrai.chat.tool;

/** Reviewed per-request call budgets for each model-visible read capability. */
enum CvChatToolCapability {
    CANDIDATE_SEARCH(5),
    ORGANIZATION_DISCOVERY(1),
    CANDIDATE_SUMMARY(5),
    JOB_REQUIREMENTS(1),
    EVALUATION_EVIDENCE(5),
    DOCUMENT_STATUS(5),
    INGESTION_WARNINGS(5);

    private final int maxCallsPerRequest;

    CvChatToolCapability(int maxCallsPerRequest) {
        this.maxCallsPerRequest = maxCallsPerRequest;
    }

    int maxCallsPerRequest() {
        return maxCallsPerRequest;
    }
}
