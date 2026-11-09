package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;

import java.util.List;

/** Provider boundary for comparing only the candidates already verified by application authorization. */
interface CvChatComparisonModel {
    String compare(String question, List<CandidateIdentity> candidates, List<CvChatEvidence> evidence);

    /** Adds tenant-authorized approved-job requirements when the recruiter explicitly selected a job. */
    default String compare(String question, List<CandidateIdentity> candidates, List<CvChatEvidence> evidence,
                           CvChatComparisonJobContext jobContext) {
        return compare(question, candidates, evidence);
    }
}
