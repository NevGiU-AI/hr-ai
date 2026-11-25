package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Privacy-safe request-local evidence about candidate-search execution.
 *
 * <p>This is not conversation memory and it is not a candidate cache. Spring components normally default to singleton
 * scope, which would incorrectly share these counters between every user. {@link RequestScope} instead creates one
 * state instance per HTTP request and discards it afterward. The {@code search_candidates} tool records each
 * completed search, and the orchestration service reads the state after its sequential search loop finishes.
 * Spring's scoped proxy lets the singleton service and request-scoped tools resolve the
 * same request-specific state instance.</p>
 *
 * <p>Queries remain only in this short-lived object to distinguish repeated calls; snapshots expose counts only and
 * are safe for decision diagnostics. No candidate name, query, card, or CV content is logged or persisted.</p>
 */
@Component
@RequestScope
public class CvChatToolDiscoveryState {
    private final AtomicInteger searchCalls = new AtomicInteger();
    private final AtomicInteger emptySearches = new AtomicInteger();
    private final Set<String> successfulQueries = ConcurrentHashMap.newKeySet();
    // The map safely creates one counter per query; the counter makes increments atomic. These are defensive:
    // they do not make the full record() operation atomic across all fields.
    private final Map<String, AtomicInteger> queryCalls = new ConcurrentHashMap<>();

    public void record(String query, int resultCount) {
        // Count even empty results: a completed search with no match is not a skipped search.
        searchCalls.incrementAndGet();
        String normalizedQuery = normalize(query);
        queryCalls.computeIfAbsent(normalizedQuery, ignored -> new AtomicInteger()).incrementAndGet();
        if (resultCount <= 0) {
            emptySearches.incrementAndGet();
            return;
        }
        // Keep normalized queries only long enough to avoid treating repeated calls for one person as two resolved
        // candidates. Snapshot deliberately exposes the set size, never its values.
        successfulQueries.add(normalizedQuery);
    }

    /** Proves Java invoked one search for every extracted reference and no additional or repeated search. */
    public boolean coversExactly(List<String> expectedQueries) {
        // Compare the list size before deduplication so two references that normalize to one key cannot pass.
        Set<String> expected = expectedQueries.stream().map(this::normalize)
                .collect(java.util.stream.Collectors.toSet());
        return expected.size() == expectedQueries.size()
                && queryCalls.keySet().equals(expected)
                && queryCalls.values().stream().allMatch(count -> count.get() == 1);
    }

    public Snapshot snapshot() {
        return new Snapshot(searchCalls.get(), successfulQueries.size(), emptySearches.get());
    }

    private String normalize(String query) {
        return query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    }

    public record Snapshot(int searchCalls, int distinctSuccessfulSearches, int emptySearches) {
        public boolean hasConfirmedMultiCandidateMatches() {
            // This proves that candidate cards exist; it does not confirm any card. Human confirmation remains an
            // Angular/API workflow step after this discovery response.
            return distinctSuccessfulSearches >= 2 && emptySearches == 0;
        }
    }
}
