package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Request-local candidate/job scope that application code authorized after explicit human confirmation.
 *
 * <p>A Spring component normally has singleton scope. Keeping confirmed IDs in a singleton would let concurrent users
 * overwrite or observe one another's chat authorization state. {@link RequestScope} instead creates a new scope for
 * each HTTP request and discards it after the response. The singleton orchestration service and request-scoped tools
 * receive scoped proxies that resolve to this same request-specific instance.</p>
 *
 * <p>This scope lasts for only one API call; it is not an HTTP session, conversation memory, or persistent permission.
 * Every later request must independently authenticate, authorize, and confirm its candidate and optional job IDs.</p>
 */
@Component
@RequestScope
public class CvChatToolScope {
    private Set<Long> confirmedCandidateIds = Set.of();
    private boolean candidatesConfigured;
    private Long confirmedJobId;
    private boolean jobConfigured;

    public void confirm(List<Long> candidateIds) {
        // Only trusted application code calls this after loading every ID inside the authenticated organization. The
        // model cannot install scope through a tool argument or reuse confirmation from another HTTP request.
        if (candidateIds == null) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Confirm between 1 and 5 distinct candidates");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(candidateIds);
        if (candidateIds.isEmpty() || candidateIds.size() > 5 || distinct.size() != candidateIds.size()
                || distinct.contains(null)) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Confirm between 1 and 5 distinct candidates");
        }
        Set<Long> requested = Set.copyOf(distinct);
        if (candidatesConfigured && !confirmedCandidateIds.equals(requested)) {
            // One HTTP request cannot replace its confirmed scope after callbacks have been created or used.
            throw new CvChatException(HttpStatus.CONFLICT, "Confirmed candidate scope cannot change during a request");
        }
        confirmedCandidateIds = requested;
        candidatesConfigured = true;
    }

    public void requireConfirmed(Long candidateId) {
        // Every detail-reading tool performs this check; merely knowing a database ID does not authorize model access.
        if (!candidatesConfigured || !confirmedCandidateIds.contains(candidateId)) {
            throw new CvChatException(HttpStatus.FORBIDDEN, "Candidate is outside the confirmed chat scope");
        }
    }

    /** Installs one application-authorized approved job; the model cannot replace it later in the request. */
    public void confirmJob(Long jobId) {
        if (jobId == null || jobId <= 0) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "A valid approved job must be confirmed");
        }
        if (jobConfigured && !confirmedJobId.equals(jobId)) {
            throw new CvChatException(HttpStatus.CONFLICT, "Confirmed job scope cannot change during a request");
        }
        confirmedJobId = jobId;
        jobConfigured = true;
    }

    public void requireConfirmedJob(Long jobId) {
        if (!jobConfigured || !confirmedJobId.equals(jobId)) {
            throw new CvChatException(HttpStatus.FORBIDDEN, "Approved job is outside the confirmed chat scope");
        }
    }
}
