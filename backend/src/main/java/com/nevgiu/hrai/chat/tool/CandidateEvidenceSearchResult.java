package com.nevgiu.hrai.chat.tool;

import java.util.List;

/** Bounded organization discovery result; cards remain choices and are never an automatic hiring decision. */
public record CandidateEvidenceSearchResult(List<CandidateEvidenceCard> candidates, boolean truncated) { }
