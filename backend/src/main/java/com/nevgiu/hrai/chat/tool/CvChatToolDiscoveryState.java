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
        searchCalls.incrementAndGet();
        if (resultCount <= 0) {
            emptySearches.incrementAndGet();
            return;
        }
        successfulQueries.add(query == null ? "" : query.strip().toLowerCase(Locale.ROOT));
    }

    public Snapshot snapshot() {
        return new Snapshot(searchCalls.get(), successfulQueries.size(), emptySearches.get());
    }

    public record Snapshot(int searchCalls, int distinctSuccessfulSearches, int emptySearches) {
        public boolean hasConfirmedMultiCandidateMatches() {
            return distinctSuccessfulSearches >= 2 && emptySearches == 0;
        }
    }
}
