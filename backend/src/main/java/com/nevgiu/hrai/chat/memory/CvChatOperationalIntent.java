package com.nevgiu.hrai.chat.memory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic routing for explicit operational requests; ambiguous language remains model-classified.
 *
 * <p>This class deliberately recognizes only a narrow vocabulary. It is not a general natural-language parser and it
 * does not decide authorization. Its purpose is to prevent an already explicit request such as an OCR-status question
 * from depending on a provider to repeat the same mandatory tool call for every confirmed candidate.</p>
 */
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
        // Recruiter input can contain visually ordinary text represented by different Unicode code points. NFKC
        // converts compatibility forms to stable equivalents before the regular expressions run. For example,
        // full-width "ＯＣＲ" becomes "OCR" and the single-character "ﬁ" ligature becomes "fi". This prevents
        // presentation-oriented Unicode variants from accidentally changing the selected application route.
        String normalized = Normalizer.normalize(message, Normalizer.Form.NFKC)
                // Locale.ROOT makes casing deterministic. Using the server's default locale could produce different
                // lowercase text on different machines, most notably for variants of the letter I.
                .toLowerCase(Locale.ROOT)
                // Folding all whitespace to one ordinary space lets the patterns behave consistently with tabs, line
                // breaks, non-breaking spaces normalized by NFKC, or repeated spaces. trim() removes irrelevant outer
                // whitespace. Punctuation is preserved: this normalizes matching input, not the recruiter's message.
                .replaceAll("\\s+", " ").trim();

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
