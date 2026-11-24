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
 * names, or job content. Request scope prevents concurrent users or tenants from sharing planning state.</p>
 */
@Component
@RequestScope
public class CvChatToolPlanningState {
    private final Map<Long, Integer> summarizedCandidateCalls = new LinkedHashMap<>();
    private final Map<Long, Integer> loadedJobCalls = new LinkedHashMap<>();

    public synchronized void recordCandidateSummary(Long candidateId) {
        summarizedCandidateCalls.merge(candidateId, 1, Integer::sum);
    }

    public synchronized void recordJobRequirements(Long jobId) {
        loadedJobCalls.merge(jobId, 1, Integer::sum);
    }

    public synchronized boolean covers(Set<Long> expectedCandidateIds, Long expectedJobId) {
        boolean exactCandidateCalls = summarizedCandidateCalls.keySet().equals(expectedCandidateIds)
                && summarizedCandidateCalls.values().stream().allMatch(count -> count == 1);
        boolean exactJobCalls = expectedJobId == null
                ? loadedJobCalls.isEmpty()
                : loadedJobCalls.keySet().equals(Set.of(expectedJobId))
                && loadedJobCalls.get(expectedJobId) == 1;
        return exactCandidateCalls && exactJobCalls;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(summarizedCandidateCalls.size(), loadedJobCalls.size());
    }

    public record Snapshot(int summarizedCandidateCount, int loadedJobCount) { }
}
