package com.nevgiu.hrai.chat.tool;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashSet;
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
    private final Set<Long> summarizedCandidateIds = new LinkedHashSet<>();
    private final Set<Long> loadedJobIds = new LinkedHashSet<>();

    public synchronized void recordCandidateSummary(Long candidateId) {
        summarizedCandidateIds.add(candidateId);
    }

    public synchronized void recordJobRequirements(Long jobId) {
        loadedJobIds.add(jobId);
    }

    public synchronized boolean covers(Set<Long> expectedCandidateIds, Long expectedJobId) {
        return summarizedCandidateIds.containsAll(expectedCandidateIds)
                && (expectedJobId == null || loadedJobIds.contains(expectedJobId));
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(summarizedCandidateIds.size(), loadedJobIds.size());
    }

    public record Snapshot(int summarizedCandidateCount, int loadedJobCount) { }
}
