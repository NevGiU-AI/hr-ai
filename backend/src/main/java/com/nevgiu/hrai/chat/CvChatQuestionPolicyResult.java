package com.nevgiu.hrai.chat;

/**
 * Pre-retrieval policy decision for one untrusted recruiter question.
 *
 * <p>The distinction between {@link #OUT_OF_SCOPE} and {@link #SENSITIVE} remains internal for evaluation and
 * privacy-safe metrics. Both decisions currently produce the same fixed public refusal.</p>
 */
enum CvChatQuestionPolicyResult {
    JOB_RELEVANT,
    OUT_OF_SCOPE,
    SENSITIVE
}
