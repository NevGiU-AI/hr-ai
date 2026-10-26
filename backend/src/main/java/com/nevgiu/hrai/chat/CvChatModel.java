package com.nevgiu.hrai.chat;

import java.util.List;

/**
 * Provider boundary kept separate so authorization and citation behavior can be tested deterministically.
 * Implementations receive only the evidence already authorized and minimized by {@link CvChatEvidenceService}.
 */
interface CvChatModel {
    /** Exact control result for a job-relevant question that the authorized evidence cannot answer. */
    String INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE";

    /** Exact control result for an unrelated request or a request for protected or sensitive characteristics. */
    String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    String answer(String question, List<CvChatEvidence> evidence);
}
