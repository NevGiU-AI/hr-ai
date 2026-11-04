package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.job.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Policy-controlled orchestration boundary behind the reviewed discovery and confirmed-scope HTTP endpoints.
 * Structured outcomes, prompt-injection evaluation, and explicit application-owned scope remain mandatory even though
 * the Angular application has not yet activated this API.
 */
@Service
public class CvChatToolOrchestrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatToolOrchestrationService.class);
    private static final String REFUSAL_MESSAGE =
            "I can only help plan job-relevant candidate review within the authorized confirmed scope. "
                    + "I cannot provide or infer protected or sensitive personal characteristics.";

    private final CvChatEvidenceService evidenceService;
    private final CvChatReadToolRegistry registry;
    private final CvChatToolScope scope;
    private final CvChatQuestionPolicy questionPolicy;
    private final CvChatToolOrchestrationModel model;
    private final JobRepository jobs;

    CvChatToolOrchestrationService(CvChatEvidenceService evidenceService, CvChatReadToolRegistry registry,
                                   CvChatToolScope scope, CvChatQuestionPolicy questionPolicy,
                                   CvChatToolOrchestrationModel model, JobRepository jobs) {
        this.evidenceService = evidenceService;
        this.registry = registry;
        this.scope = scope;
        this.questionPolicy = questionPolicy;
        this.model = model;
        this.jobs = jobs;
    }

    private static final Set<CvChatToolOrchestrationOutcome.Kind> DISCOVERY_OUTCOMES = EnumSet.of(
            CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED,
            CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED,
            CvChatToolOrchestrationOutcome.Kind.REFUSED);
    private static final Set<CvChatToolOrchestrationOutcome.Kind> CONFIRMED_OUTCOMES = EnumSet.of(
            CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY,
            CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED,
            CvChatToolOrchestrationOutcome.Kind.REFUSED);

    public CvChatToolOrchestrationOutcome discover(String message) {
        return call(message, CvChatToolOrchestrationMode.DISCOVERY, registry.discoveryCallbacks(),
                DISCOVERY_OUTCOMES, 0);
    }

    public CvChatToolOrchestrationOutcome planConfirmedScope(String message, List<Long> requestedCandidateIds,
                                                              String organizationId) {
        return planConfirmedScope(message, requestedCandidateIds, null, organizationId);
    }

    /**
     * Plans inside an application-confirmed candidate scope and, when supplied, one explicitly selected approved job.
     * The approved-job tool is not registered at all when no job was confirmed.
     */
    public CvChatToolOrchestrationOutcome planConfirmedScope(String message, List<Long> requestedCandidateIds,
                                                              Long requestedJobId, String organizationId) {
        List<Long> candidateIds = validateIds(requestedCandidateIds);
        if (requestedJobId != null && requestedJobId <= 0) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "A valid approved job must be confirmed");
        }
        // Authorize the complete set before installing it. Missing and cross-tenant IDs retain the same generic 404.
        candidateIds.forEach(candidateId -> evidenceService.authorizeCandidate(candidateId, organizationId));
        if (requestedJobId != null) {
            jobs.findByIdAndOrganizationId(requestedJobId, organizationId)
                    .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Approved job not found"));
        }
        scope.confirm(candidateIds);
        if (requestedJobId != null) {
            scope.confirmJob(requestedJobId);
        }
        List<org.springframework.ai.tool.ToolCallback> callbacks = requestedJobId == null
                ? registry.confirmedScopeCallbacks()
                : registry.confirmedScopeCallbacksWithJob();
        return call(message, CvChatToolOrchestrationMode.CONFIRMED_SCOPE, callbacks,
                CONFIRMED_OUTCOMES, candidateIds.size());
    }

    private CvChatToolOrchestrationOutcome call(String message,
                                                 CvChatToolOrchestrationMode mode,
                                                 List<org.springframework.ai.tool.ToolCallback> callbacks,
                                                 Set<CvChatToolOrchestrationOutcome.Kind> allowedOutcomes,
                                                 int candidateCount) {
        if (message == null || message.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Chat message is required");
        }
        try {
            String normalizedMessage = message.strip();
            CvChatQuestionPolicyResult policyResult = questionPolicy.classify(normalizedMessage);
            if (policyResult == CvChatQuestionPolicyResult.SENSITIVE) {
                // The existing policy's OUT_OF_SCOPE label is evidence-question-specific: legitimate scope-planning
                // requests may receive it because they ask to find or confirm candidates rather than inspect a CV.
                // Its SENSITIVE decision is reusable as a hard pre-tool boundary and prevents any candidate lookup.
                LOGGER.info("CV chat tool orchestration rejected at pre-tool policy for candidateCount={} policyResult={}",
                        candidateCount, policyResult);
                return new CvChatToolOrchestrationOutcome(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                        REFUSAL_MESSAGE);
            }
            CvChatToolOrchestrationOutcome outcome = model.respond(normalizedMessage, mode, callbacks);
            if (outcome == null || outcome.kind() == null || outcome.message() == null
                    || outcome.message().isBlank()) {
                throw new CvChatException(HttpStatus.BAD_GATEWAY,
                        "CV chat provider returned an invalid orchestration response");
            }
            if (!allowedOutcomes.contains(outcome.kind())) {
                // A model cannot advance the workflow past an application-owned confirmation gate.
                LOGGER.warn("CV chat tool orchestration rejected invalid transition for candidateCount={} outcome={}",
                        candidateCount, outcome.kind());
                throw new CvChatException(HttpStatus.BAD_GATEWAY,
                        "CV chat provider returned an invalid orchestration response");
            }
            if (outcome.kind() == CvChatToolOrchestrationOutcome.Kind.REFUSED) {
                // Provider wording is untrusted; refusals always use application-owned text.
                return new CvChatToolOrchestrationOutcome(outcome.kind(), REFUSAL_MESSAGE);
            }
            return new CvChatToolOrchestrationOutcome(outcome.kind(), outcome.message().trim());
        } catch (CvChatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Log no recruiter message, tool arguments, result payload, or provider detail.
            LOGGER.warn("CV chat tool orchestration failed for candidateCount={} failureType={}",
                    candidateCount, failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat tool orchestration is unavailable");
        }
    }

    private List<Long> validateIds(List<Long> candidateIds) {
        if (candidateIds == null) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Confirm between 1 and 5 distinct candidates");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(candidateIds);
        if (candidateIds.isEmpty() || candidateIds.size() > 5 || distinct.size() != candidateIds.size()
                || distinct.contains(null)) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Confirm between 1 and 5 distinct candidates");
        }
        return List.copyOf(distinct);
    }
}
