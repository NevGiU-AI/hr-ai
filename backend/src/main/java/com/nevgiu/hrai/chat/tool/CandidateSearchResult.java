package com.nevgiu.hrai.chat.tool;

import java.util.List;

/** Bounded candidate cards returned by the tenant-scoped search tool. */
public record CandidateSearchResult(List<CandidateCard> candidates, boolean truncated) { }
