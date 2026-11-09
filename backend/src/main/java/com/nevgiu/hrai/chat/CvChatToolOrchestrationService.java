package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatReadTools;
import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolDiscoveryState;
import com.nevgiu.hrai.chat.tool.CvChatToolPlanningState;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import com.nevgiu.hrai.job.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Policy-controlled orchestration boundary behind the reviewed discovery and confirmed-scope HTTP endpoints.
 * Structured outcomes, prompt-injection evaluation, and explicit application-owned scope remain mandatory. The
 * Angular language-first flow calls this boundary before deterministic card lookup and again after card confirmation.
 */
@Service
public class CvChatToolOrchestrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatToolOrchestrationService.class);
    private static final String REFUSAL_MESSAGE =
            "I can only help plan job-relevant candidate review within the authorized confirmed scope. "
                    + "I cannot provide or infer protected or sensitive personal characteristics.";

    private static final String CONFIRMED_SCOPE_READY_MESSAGE =
            "Confirmed candidate scope is ready for grounded comparison.";

    private final CvChatEvidenceService evidenceService;
    private final CvChatQuestionPolicy questionPolicy;
    private final CvChatCandidateReferenceExtractor referenceExtractor;
    private final CvChatReadTools readTools;
    private final JobRepository jobs;
    private final CvChatToolDiscoveryState discoveryState;
    private final CvChatReadToolRegistry toolRegistry;
    private final CvChatToolScope toolScope;
    private final CvChatToolPlanningState planningState;
    private final CvChatToolOrchestrationModel model;

    CvChatToolOrchestrationService(CvChatEvidenceService evidenceService,
                                   CvChatQuestionPolicy questionPolicy, JobRepository jobs,
                                   CvChatToolDiscoveryState discoveryState,
                                   CvChatCandidateReferenceExtractor referenceExtractor,
                                   CvChatReadTools readTools, CvChatReadToolRegistry toolRegistry,
                                   CvChatToolScope toolScope, CvChatToolPlanningState planningState,
                                   CvChatToolOrchestrationModel model) {
        this.evidenceService = evidenceService;
        this.questionPolicy = questionPolicy;
        this.referenceExtractor = referenceExtractor;
        this.readTools = readTools;
        this.jobs = jobs;
        this.discoveryState = discoveryState;
        this.toolRegistry = toolRegistry;
        this.toolScope = toolScope;
        this.planningState = planningState;
        this.model = model;
    }

    public CvChatToolOrchestrationOutcome discover(String message) {
        String normalizedMessage = requireMessage(message);
        CvChatToolOrchestrationOutcome blocked = blockedOutcome(normalizedMessage, 0, false);
        return blocked != null ? blocked : discoverCandidates(normalizedMessage);
    }

    public CvChatToolOrchestrationOutcome planConfirmedScope(String message, List<Long> requestedCandidateIds,
                                                              String organizationId) {
        return planConfirmedScope(message, requestedCandidateIds, null, organizationId);
    }

    /**
     * Plans inside an application-confirmed candidate scope and, when supplied, one explicitly selected approved job.
     * Authorization and the ready transition are application-owned; the provider cannot reinterpret trusted IDs.
     */
    public CvChatToolOrchestrationOutcome planConfirmedScope(String message, List<Long> requestedCandidateIds,
                                                              Long requestedJobId, String organizationId) {
        List<Long> candidateIds = validateIds(requestedCandidateIds);
        if (requestedJobId != null && requestedJobId <= 0) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "A valid approved job must be confirmed");
        }
        String normalizedMessage = requireMessage(message);
        CvChatToolOrchestrationOutcome blocked = blockedOutcome(normalizedMessage, candidateIds.size(), true);
        if (blocked != null) {
            return blocked;
        }
        // Authorize the complete set before installing it. Missing and cross-tenant IDs retain the same generic 404.
        candidateIds.forEach(candidateId -> evidenceService.authorizeCandidate(candidateId, organizationId));
        if (requestedJobId != null) {
            jobs.findByIdAndOrganizationId(requestedJobId, organizationId)
                    .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Approved job not found"));
        }
        // Install only IDs already authorized above. Every model-generated tool argument must pass this scope again.
        toolScope.confirm(candidateIds);
        if (requestedJobId != null) {
            toolScope.confirmJob(requestedJobId);
        }
        return advisoryToolPlan(normalizedMessage, candidateIds, requestedJobId);
    }

    /**
     * Runs a real Spring AI tool-planning call without delegating workflow authority to the provider.
     *
     * <p>The application has already authenticated, authorized, and confirmed every ID. The model may now call the
     * minimized read tools as a bounded orchestration preflight. Request-scoped postconditions prove that every
     * expected summary (and optional job) was actually read. Provider-written presentation text is deliberately
     * discarded. Any refusal, malformed outcome, missing call, or provider failure falls back to the same
     * application-owned ready response; it cannot revoke an authorized scope or inject unsupported claims.</p>
     */
    private CvChatToolOrchestrationOutcome advisoryToolPlan(String message, List<Long> candidateIds, Long jobId) {
        try {
            CvChatToolOrchestrationOutcome proposed = model.respond(message,
                    CvChatToolOrchestrationMode.CONFIRMED_SCOPE,
                    jobId == null ? toolRegistry.confirmedScopeCallbacks()
                            : toolRegistry.confirmedScopeCallbacksWithJob(),
                    candidateIds, jobId);
            CvChatToolPlanningState.Snapshot snapshot = planningState.snapshot();
            boolean complete = planningState.covers(Set.copyOf(candidateIds), jobId);
            if (complete && proposed != null
                    && proposed.kind() == CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY) {
                LOGGER.info("CV chat advisory tool preflight completed candidateCount={} jobPresent={} "
                                + "summarizedCandidateCount={} loadedJobCount={}",
                        candidateIds.size(), jobId != null, snapshot.summarizedCandidateCount(),
                        snapshot.loadedJobCount());
                return readyOutcome();
            }
            LOGGER.info("CV chat advisory tool plan used application fallback candidateCount={} jobPresent={} "
                            + "summarizedCandidateCount={} loadedJobCount={} providerOutcome={}",
                    candidateIds.size(), jobId != null, snapshot.summarizedCandidateCount(), snapshot.loadedJobCount(),
                    proposed == null ? "NONE" : proposed.kind());
        } catch (RuntimeException failure) {
            // This plan improves presentation only. Never expose provider/tool details or turn an authorized scope
            // into a 5xx response because the optional advisory step was unavailable.
            CvChatToolPlanningState.Snapshot snapshot = planningState.snapshot();
            LOGGER.warn("CV chat advisory tool plan failed; using application fallback candidateCount={} "
                            + "jobPresent={} summarizedCandidateCount={} loadedJobCount={} failureType={}",
                    candidateIds.size(), jobId != null, snapshot.summarizedCandidateCount(), snapshot.loadedJobCount(),
                    failure.getClass().getSimpleName());
        }
        return readyOutcome();
    }

    private CvChatToolOrchestrationOutcome readyOutcome() {
        return new CvChatToolOrchestrationOutcome(
                CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY, CONFIRMED_SCOPE_READY_MESSAGE);
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Chat message is required");
        }
        return message.strip();
    }

    /** Returns an application-owned refusal or {@code null} when deterministic processing may continue. */
    private CvChatToolOrchestrationOutcome blockedOutcome(String normalizedMessage, int candidateCount,
                                                           boolean rejectOutOfScope) {
        try {
            if (CvChatToolRequestPolicy.requestsWorkflowOverride(normalizedMessage)) {
                // Confirmation, authorization, tenant, role, and tool boundaries are application policy. Explicit
                // attempts to weaken them stop before any provider call or tool execution.
                LOGGER.info("CV chat tool orchestration rejected application workflow override for candidateCount={}",
                        candidateCount);
                return new CvChatToolOrchestrationOutcome(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                        REFUSAL_MESSAGE);
            }
            CvChatQuestionPolicyResult policyResult = questionPolicy.classify(normalizedMessage);
            if (policyResult == CvChatQuestionPolicyResult.SENSITIVE
                    || (rejectOutOfScope && policyResult == CvChatQuestionPolicyResult.OUT_OF_SCOPE)) {
                // Discovery tolerates evidence-oriented OUT_OF_SCOPE because name resolution is not a CV question.
                // Once IDs are confirmed, however, unrelated or sensitive text must not advance to comparison.
                LOGGER.info("CV chat tool orchestration rejected at pre-tool policy for candidateCount={} policyResult={}",
                        candidateCount, policyResult);
                return new CvChatToolOrchestrationOutcome(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                        REFUSAL_MESSAGE);
            }
            return null;
        } catch (CvChatException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // Log no recruiter message, tool arguments, result payload, or provider detail.
            LOGGER.warn("CV chat tool orchestration failed for candidateCount={} failureType={}",
                    candidateCount, failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat tool orchestration is unavailable");
        }
    }

    /**
     * Runs mandatory candidate discovery under application control.
     *
     * <p>The provider extracts only bounded human-readable references. Java then invokes the authenticated,
     * tenant-scoped search tool once for each reference. Unlike the previous model-directed tool path, a provider
     * cannot return {@code REFUSED} before the required searches execute. Sensitive and workflow-bypass requests have
     * already returned above, before either extraction or search.</p>
     */
    private CvChatToolOrchestrationOutcome discoverCandidates(String message) {
        List<String> references;
        try {
            references = referenceExtractor.extract(message);
        } catch (RuntimeException failure) {
            LOGGER.warn("CV chat discovery reference extraction failed failureType={}",
                    failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "Candidate reference interpretation is unavailable");
        }
        if (references.size() < 2) {
            return CvChatToolDiscoveryDecision.decide(discoveryState.snapshot());
        }
        // Invoke the same reviewed tool exposed to Spring AI, but do so deterministically. The tool still derives the
        // tenant and roles from the authenticated SecurityContext and records privacy-safe result counts.
        references.forEach(readTools::searchCandidates);
        CvChatToolDiscoveryState.Snapshot searches = discoveryState.snapshot();
        CvChatToolOrchestrationOutcome outcome = CvChatToolDiscoveryDecision.decide(searches);
        LOGGER.info("CV chat deterministic discovery completed searchCalls={} distinctSuccessfulSearches={} "
                        + "emptySearches={} outcome={}", searches.searchCalls(), searches.distinctSuccessfulSearches(),
                searches.emptySearches(), outcome.kind());
        return outcome;
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
