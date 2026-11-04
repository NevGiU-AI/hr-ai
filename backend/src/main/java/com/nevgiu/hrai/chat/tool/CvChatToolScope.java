package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Request-local candidate/job scope that application code authorized after explicit human confirmation. */
@Component
@RequestScope
public class CvChatToolScope {
    private Set<Long> confirmedCandidateIds = Set.of();
    private boolean candidatesConfigured;
    private Long confirmedJobId;
    private boolean jobConfigured;

    public void confirm(List<Long> candidateIds) {
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
