package com.nevgiu.hrai.chat.tool;

import java.util.List;

/** Bounded warning summary for the newest document of one confirmed candidate. */
public record IngestionWarningsResult(
        Long candidateId,
        Long documentId,
        List<IngestionWarning> warnings
) {
    public IngestionWarningsResult {
        warnings = List.copyOf(warnings);
    }
}
