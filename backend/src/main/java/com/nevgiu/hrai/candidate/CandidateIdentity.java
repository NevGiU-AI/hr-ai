package com.nevgiu.hrai.candidate;

/** Minimal candidate identity used for tenant-scoped selection without loading email or CV content. */
public record CandidateIdentity(Long id, String name, String currentTitle) { }
