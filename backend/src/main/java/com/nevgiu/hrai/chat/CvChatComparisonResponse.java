package com.nevgiu.hrai.chat;

import java.util.List;

/** Grounded comparison plus the verified minimal identities and authorized citations used to support it. */
public record CvChatComparisonResponse(
        String answer,
        List<CandidateOption> candidates,
        List<CvChatResponse.Citation> citations
) {
    public record CandidateOption(Long id, String name, String currentTitle) { }
}
