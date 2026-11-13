package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Privacy-safe request-local evidence about candidate-search execution.
 *
 * <p>This is not conversation memory and it is not a candidate cache. Spring components normally default to singleton
 * scope, which would incorrectly share these counters between every user. {@link RequestScope} instead creates one
 * state instance per HTTP request and discards it afterward. The {@code search_candidates} tool records what happened
 * while the model was executing, and the orchestration service reads the aggregate snapshot from that same request
 * after the model call returns. Spring's scoped proxy lets the singleton service and request-scoped tools resolve the
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

    public void record(String query, int resultCount) {
        // Count every invocation so orchestration can distinguish "the model never searched" from "the model
        // searched but found nothing". Those situations require different workflow outcomes.
        searchCalls.incrementAndGet();
        if (resultCount <= 0) {
            emptySearches.incrementAndGet();
            return;
        }
        // Keep normalized queries only long enough to avoid treating repeated calls for one person as two resolved
        // candidates. Snapshot deliberately exposes the set size, never its values.
        successfulQueries.add(query == null ? "" : query.strip().toLowerCase(Locale.ROOT));
    }

    public Snapshot snapshot() {
        return new Snapshot(searchCalls.get(), successfulQueries.size(), emptySearches.get());
    }

    public record Snapshot(int searchCalls, int distinctSuccessfulSearches, int emptySearches) {
        public boolean hasCandidateMatches() {
            // This proves that candidate cards exist; it does not confirm any card. Human confirmation remains an
            // Angular/API workflow step after this discovery response.
            return distinctSuccessfulSearches >= 1 && emptySearches == 0;
        }
    }
}
