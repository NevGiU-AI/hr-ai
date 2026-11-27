package com.nevgiu.hrai.chat;

/** Classifies an untrusted question before any candidate evidence is retrieved or disclosed. */
interface CvChatQuestionPolicy {

    CvChatQuestionPolicyResult classify(String question);

    /** Used only after the application authorizes the selected job; no job text is needed for policy. */
    default CvChatQuestionPolicyResult classifyWithApprovedJob(String question) {
        return classify(question);
    }

    /** Adds only the trusted fact that an approved job was tenant-authorized for this comparison. */
    default CvChatQuestionPolicyResult classifySelectedCandidateComparison(String question,
                                                                            boolean approvedJobSelected) {
        return classify(question);
    }
}
