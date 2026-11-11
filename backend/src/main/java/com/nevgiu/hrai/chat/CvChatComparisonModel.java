package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/** Provider boundary for comparing only the candidates already verified by application authorization. */
interface CvChatComparisonModel {
    /** Adds tenant-authorized approved-job requirements when the recruiter explicitly selected a job. */
    String compare(String question, List<CandidateIdentity> candidates, List<CvChatEvidence> evidence,
                   CvChatComparisonJobContext jobContext);

    /** Adds bounded prior turns as untrusted context without changing the application-confirmed comparison scope. */
    default String compare(String question, List<CandidateIdentity> candidates, List<CvChatEvidence> evidence,
                           CvChatComparisonJobContext jobContext, List<Message> history) {
        return compare(question, candidates, evidence, jobContext);
    }
}
