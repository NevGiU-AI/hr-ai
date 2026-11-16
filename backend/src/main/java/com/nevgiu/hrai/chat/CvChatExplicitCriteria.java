package com.nevgiu.hrai.chat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Extracts explicit professional terms used only to refine organization-wide candidate discovery. */
final class CvChatExplicitCriteria {
    private static final int MAX_CRITERIA = 8;
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}+#.]+");
    private static final Set<String> SEARCH_BOILERPLATE = Set.of(
            "a", "an", "and", "are", "best", "candidate", "candidates", "compare", "developer", "developers",
            "dev", "devs", "engineer", "engineers", "experience", "experiences", "find", "for", "good",
            "great", "in", "most", "of", "on", "organization", "our", "people", "person", "professional",
            "professionals", "show", "skill", "skills", "strong", "strongest", "the", "this", "to", "with");

    private CvChatExplicitCriteria() {
    }

    /**
     * Keeps named technologies, employers, products, and certifications while dropping discovery boilerplate.
     * The values are compared only with already tenant-authorized vector results; they never become SQL or policy.
     */
    static List<String> from(String query) {
        if (query == null || query.isBlank()) return List.of();
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        Arrays.stream(TOKEN_SEPARATOR.split(query.toLowerCase(Locale.ROOT)))
                .map(CvChatExplicitCriteria::normalizeToken)
                .filter(token -> token.length() >= 2)
                .filter(token -> !SEARCH_BOILERPLATE.contains(token))
                .limit(MAX_CRITERIA)
                .forEach(terms::add);
        return List.copyOf(terms);
    }

    /** Counts distinct explicit criteria present as complete, case-insensitive terms in one CV chunk. */
    static int countMatches(String content, List<String> criteria) {
        if (content == null || content.isBlank() || criteria.isEmpty()) return 0;
        Set<String> contentTerms = new LinkedHashSet<>(
                Arrays.stream(TOKEN_SEPARATOR.split(content.toLowerCase(Locale.ROOT)))
                        .map(CvChatExplicitCriteria::normalizeToken)
                        .filter(token -> !token.isEmpty())
                        .toList());
        return (int) criteria.stream().filter(contentTerms::contains).count();
    }

    private static String normalizeToken(String token) {
        // Preserve meaningful leading punctuation in names such as .NET, but remove sentence-ending full stops.
        return token.trim().replaceFirst("\\.+$", "");
    }
}
