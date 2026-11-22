package com.nevgiu.hrai.chat.memory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic routing for explicit operational requests; ambiguous language remains model-classified. */
final class CvChatOperationalIntent {
    enum Kind { AMBIGUOUS, DOCUMENT_STATUS, INGESTION_WARNINGS, DOCUMENT_STATUS_AND_WARNINGS, EVALUATION_HISTORY }

    private static final Pattern EVALUATION = Pattern.compile(
            "\\b(evaluation history|evaluation scores?|overall fit|skills match|ai confidence)\\b|"
                    + "\\b(latest|previous|current|stale|persisted|stored|recent|prior)\\s+"
                    + "(?:persisted\\s+)?(?:ai-assisted\\s+)?evaluations?\\b|"
                    + "\\bevaluations?\\s+(?:was\\s+)?invalidated\\b|"
                    + "\\b(show|list)\\s+(?:the\\s+)?evaluations?\\b");
    private static final Pattern DOCUMENT = Pattern.compile(
            "\\b(ocr|extraction method|text extraction|processed|processing status|document status|"
                    + "imported|import status|ingestion status|reprocessed|reprocessing)\\b");
    private static final Pattern WARNING = Pattern.compile(
            "\\b(ingestion warning|ingestion warnings|processing warning|processing warnings|manual review|"
                    + "ingestion failed|ingestion failure|failed ingestion)\\b");
    private static final Pattern UNSAFE_MIXED = Pattern.compile(
            "\\b(religions?|religious|ethnicities|ethnicity|ethnic|races?|racial|genders?|sex|sexual orientations?|"
                    + "age|disabilities|disability|"
                    + "marital status|pregnan\\w*|ignore|bypass|override|disable|skip authorization|candidate 999)\\b");

    private CvChatOperationalIntent() { }

    static Kind classify(String message) {
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        // Mixed sensitive or workflow-override requests must continue through the existing policy path. Routing them
        // deterministically would disclose operational data before that policy can refuse the unsafe part.
        if (UNSAFE_MIXED.matcher(normalized).find()) return Kind.AMBIGUOUS;
        boolean evaluation = EVALUATION.matcher(normalized).find();
        boolean document = DOCUMENT.matcher(normalized).find();
        boolean warning = WARNING.matcher(normalized).find();
        // Mixed evaluation/document requests need conversational clarification and are intentionally left ambiguous.
        if (evaluation && (document || warning)) return Kind.AMBIGUOUS;
        if (evaluation) return Kind.EVALUATION_HISTORY;
        if (document && warning) return Kind.DOCUMENT_STATUS_AND_WARNINGS;
        if (document) return Kind.DOCUMENT_STATUS;
        if (warning) return Kind.INGESTION_WARNINGS;
        return Kind.AMBIGUOUS;
    }
}
