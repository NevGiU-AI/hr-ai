package com.nevgiu.hrai.chat;

/** Classifies an untrusted question before any candidate evidence is retrieved or disclosed. */
interface CvChatQuestionPolicy {

    CvChatQuestionPolicyResult classify(String question);

    /** Adds only the trusted fact that an approved job was tenant-authorized for this comparison. */
    default CvChatQuestionPolicyResult classifySelectedCandidateComparison(String question,
                                                                            boolean approvedJobSelected) {
        return classify(question);
    }
}
