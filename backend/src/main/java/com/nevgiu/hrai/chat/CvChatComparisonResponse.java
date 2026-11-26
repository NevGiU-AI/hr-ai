package com.nevgiu.hrai.chat;

import java.util.List;

/** Grounded comparison plus the verified minimal identities and authorized citations used to support it. */
public record CvChatComparisonResponse(
        CvChatAnswerKind kind,
        String answer,
        List<CandidateOption> candidates,
        List<CvChatResponse.Citation> citations,
        String responseLanguage
) {
    public CvChatComparisonResponse(CvChatAnswerKind kind, String answer, List<CandidateOption> candidates,
                                    List<CvChatResponse.Citation> citations) {
        this(kind, answer, candidates, citations, null);
    }

    public record CandidateOption(Long id, String name, String currentTitle) { }
}
