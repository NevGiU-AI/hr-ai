package com.nevgiu.hrai.candidate.semantic;

/** Carries privacy-safe failure-stage information across the best-effort indexing boundary. */
final class CvSemanticIndexingException extends RuntimeException {
    private final CvSemanticIndexingStage stage;

    private CvSemanticIndexingException(CvSemanticIndexingStage stage, RuntimeException cause) {
        // The message contains no provider text or CV evidence and is therefore safe for operational diagnostics.
        super("CV semantic indexing failed during " + stage, cause);
        this.stage = stage;
    }

    static CvSemanticIndexingException at(CvSemanticIndexingStage stage, RuntimeException cause) {
        return new CvSemanticIndexingException(stage, cause);
    }

    CvSemanticIndexingStage stage() {
        return stage;
    }
}
