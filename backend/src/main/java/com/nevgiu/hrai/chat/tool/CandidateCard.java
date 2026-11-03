package com.nevgiu.hrai.chat.tool;

/** Minimal identity shown for explicit human selection; it intentionally excludes contact details and CV content. */
public record CandidateCard(Long id, String name, String currentTitle) { }
