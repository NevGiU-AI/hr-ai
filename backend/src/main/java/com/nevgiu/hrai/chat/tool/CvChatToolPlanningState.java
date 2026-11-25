package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Privacy-safe postcondition state for one confirmed-scope planning request.
 *
 * <p>The state records only IDs already authorized by Java. It never records tool results, CV text, recruiter text,
 * names, or job content. Request scope keeps separate HTTP requests from sharing state; it does not serialize access
 * within one request. All reads and writes use the same instance lock, so neither map can change during a coverage
 * check. The maps themselves do not need to be concurrent.</p>
 */
@Component
@RequestScope
public class CvChatToolPlanningState {
    // A set would hide repeated callbacks. Keep counts so the check can distinguish one call from several.
    private final Map<Long, Integer> summarizedCandidateCalls = new LinkedHashMap<>();
    private final Map<Long, Integer> loadedJobCalls = new LinkedHashMap<>();

    public synchronized void recordCandidateSummary(Long candidateId) {
        summarizedCandidateCalls.merge(candidateId, 1, Integer::sum);
    }

    public synchronized void recordJobRequirements(Long jobId) {
        loadedJobCalls.merge(jobId, 1, Integer::sum);
    }

    public synchronized boolean covers(Set<Long> expectedCandidateIds, Long expectedJobId) {
        // Key equality rejects missing and extra IDs; the counts reject duplicates. Insertion order is irrelevant.
        boolean exactCandidateCalls = summarizedCandidateCalls.keySet().equals(expectedCandidateIds)
                && summarizedCandidateCalls.values().stream().allMatch(count -> count == 1);
        boolean exactJobCalls = expectedJobId == null
                ? loadedJobCalls.isEmpty()
                : loadedJobCalls.keySet().equals(Set.of(expectedJobId))
                && loadedJobCalls.get(expectedJobId) == 1;
        return exactCandidateCalls && exactJobCalls;
    }

    /** Counts distinct IDs for diagnostics only; use {@link #covers} to check for duplicate calls. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(summarizedCandidateCalls.size(), loadedJobCalls.size());
    }

    public record Snapshot(int summarizedCandidateCount, int loadedJobCount) { }
}
