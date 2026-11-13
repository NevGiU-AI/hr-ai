package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CandidateCard;
import com.nevgiu.hrai.chat.tool.CandidateSearchResult;
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
 * Angular language-first flow receives the deterministic cards from this boundary and calls it again after card
 * confirmation. Discovery deliberately performs only one provider extraction and one authorized search per reference.
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

    public CvChatToolDiscoveryResponse discover(String message) {
        String normalizedMessage = requireMessage(message);
        CvChatToolOrchestrationOutcome blocked = blockedOutcome(normalizedMessage, 0);
        return blocked != null ? CvChatToolDiscoveryResponse.from(blocked, null)
                : discoverCandidates(normalizedMessage);
    }

    /**
     * Runs the reviewed tenant-wide evidence-search tool deterministically, then returns bounded cards for human
     * confirmation. The provider may normalize the retrieval query but cannot choose a tenant or candidate ID.
     */
    public CvChatOrganizationDiscoveryResponse discoverOrganization(String message) {
        String normalizedMessage = requireMessage(message);
        CvChatQuestionPolicyResult policyResult;
        try {
            policyResult = questionPolicy.classify(normalizedMessage);
        } catch (RuntimeException failure) {
            LOGGER.warn("CV organization discovery policy failed failureType={}",
                    failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat question policy is unavailable");
        }
        if (policyResult != CvChatQuestionPolicyResult.JOB_RELEVANT) {
            return new CvChatOrganizationDiscoveryResponse(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                    REFUSAL_MESSAGE, List.of(), false);
        }
        var result = readTools.searchCandidatesByEvidence(normalizedMessage);
        if (result.candidates().isEmpty()) {
            return new CvChatOrganizationDiscoveryResponse(
                    CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED,
                    "No indexed candidates matched those professional criteria. Refine the request.",
                    List.of(), result.truncated());
        }
        List<CvChatOrganizationDiscoveryResponse.CandidateOption> cards = result.candidates().stream()
                .map(candidate -> new CvChatOrganizationDiscoveryResponse.CandidateOption(
                        candidate.id(), candidate.name(), candidate.currentTitle(), candidate.evidenceReferences()))
                .toList();
        return new CvChatOrganizationDiscoveryResponse(
                CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED,
                "Evidence-backed candidate matches were found. Confirm up to five candidates before continuing.",
                cards, result.truncated());
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
        CvChatToolOrchestrationOutcome blocked = blockedOutcome(normalizedMessage, candidateIds.size());
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
            LOGGER.warn("CV chat advisory tool preflight incomplete; using application fallback candidateCount={} "
                            + "jobPresent={} summarizedCandidateCount={} loadedJobCount={} providerOutcome={} "
                            + "stage=TOOL_PREFLIGHT_INCOMPLETE",
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
    private CvChatToolOrchestrationOutcome blockedOutcome(String normalizedMessage, int candidateCount) {
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
            if (policyResult == CvChatQuestionPolicyResult.SENSITIVE) {
                // This phase resolves or validates scope; it does not answer the recruiter. OUT_OF_SCOPE is therefore
                // handled later by the grounded chat/comparison boundary. Only sensitive intent blocks all reads here.
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
    private CvChatToolDiscoveryResponse discoverCandidates(String message) {
        List<String> references;
        try {
            references = referenceExtractor.extract(message);
        } catch (RuntimeException failure) {
            LOGGER.warn("CV chat discovery reference extraction failed failureType={}",
                    failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "Candidate reference interpretation is unavailable");
        }
        if (references.isEmpty()) {
            return CvChatToolDiscoveryResponse.from(
                    CvChatToolDiscoveryDecision.decide(discoveryState.snapshot()), null);
        }
        // Invoke the same reviewed tool exposed to Spring AI, but do so deterministically. The tool still derives the
        // tenant and roles from the authenticated SecurityContext and records privacy-safe result counts. Retain these
        // exact minimized results so Angular does not trigger a second provider extraction and potentially receive a
        // different set of candidate references.
        List<CvChatLanguageScopeResponse.Reference> candidateReferences = references.stream()
                .map(reference -> candidateReference(reference, readTools.searchCandidates(reference)))
                .toList();
        CvChatToolDiscoveryState.Snapshot searches = discoveryState.snapshot();
        CvChatToolOrchestrationOutcome outcome = CvChatToolDiscoveryDecision.decide(searches);
        LOGGER.info("CV chat deterministic discovery completed searchCalls={} distinctSuccessfulSearches={} "
                        + "emptySearches={} outcome={}", searches.searchCalls(), searches.distinctSuccessfulSearches(),
                searches.emptySearches(), outcome.kind());
        CvChatLanguageScopeResponse candidateScope =
                outcome.kind() == CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED
                        ? new CvChatLanguageScopeResponse(message, List.copyOf(candidateReferences))
                        : null;
        return CvChatToolDiscoveryResponse.from(outcome, candidateScope);
    }

    private CvChatLanguageScopeResponse.Reference candidateReference(String requestedName,
                                                                      CandidateSearchResult searchResult) {
        List<CvChatLanguageScopeResponse.CandidateOption> candidates = searchResult.candidates().stream()
                .map(this::candidateOption)
                .toList();
        CvChatLanguageScopeResponse.Status status = candidates.isEmpty()
                ? CvChatLanguageScopeResponse.Status.NOT_FOUND
                : CvChatLanguageScopeResponse.Status.CHOICES;
        // search_candidates is deliberately a partial-name operation. Even one returned card remains a choice until
        // the recruiter explicitly selects and confirms it; current or future tenant data must not change that rule.
        return new CvChatLanguageScopeResponse.Reference(
                requestedName, status, List.copyOf(candidates), searchResult.truncated());
    }

    private CvChatLanguageScopeResponse.CandidateOption candidateOption(CandidateCard candidate) {
        return new CvChatLanguageScopeResponse.CandidateOption(
                candidate.id(), candidate.name(), candidate.currentTitle());
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
