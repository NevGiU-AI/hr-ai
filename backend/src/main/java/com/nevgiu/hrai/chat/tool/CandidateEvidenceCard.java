package com.nevgiu.hrai.chat.tool;

import java.util.List;

/** Minimal model-facing candidate card returned by tenant-wide professional-evidence discovery. */
public record CandidateEvidenceCard(Long id, String name, String currentTitle, List<String> evidenceReferences) { }
