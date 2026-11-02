package com.nevgiu.hrai.chat;

import java.util.List;

/** Bounded minimal identities returned for explicit recruiter selection; never an automatic resolution decision. */
public record CvChatCandidateSearchResponse(List<CandidateOption> candidates, boolean truncated) {
    public record CandidateOption(Long id, String name, String currentTitle) { }
}
