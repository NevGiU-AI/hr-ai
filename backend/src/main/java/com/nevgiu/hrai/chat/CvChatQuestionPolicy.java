package com.nevgiu.hrai.chat;

/** Classifies an untrusted question before any candidate evidence is retrieved or disclosed. */
interface CvChatQuestionPolicy {

    CvChatQuestionPolicyResult classify(String question);

    /**
     * Classifies a question after the application has already established a bounded, authorized comparison scope.
     *
     * <p>The default preserves compatibility for non-model implementations. The Spring AI implementation overrides
     * this method so trusted comparison context is placed in the system prompt rather than mixed with recruiter text.</p>
     */
    default CvChatQuestionPolicyResult classifySelectedCandidateComparison(String question) {
        return classify(question);
    }

    /** Adds only the trusted fact that an approved job was tenant-authorized for this comparison. */
    default CvChatQuestionPolicyResult classifySelectedCandidateComparison(String question,
                                                                            boolean approvedJobSelected) {
        return classifySelectedCandidateComparison(question);
    }
}
