package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.chat.tool.CvChatReadToolRegistry;
import com.nevgiu.hrai.chat.tool.CvChatToolScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Internal orchestration boundary. It is intentionally not exposed by a controller until structured outcomes,
 * prompt-injection evaluation, and UI confirmation behavior are accepted.
 */
@Service
public class CvChatToolOrchestrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatToolOrchestrationService.class);

    private final CvChatEvidenceService evidenceService;
    private final CvChatReadToolRegistry registry;
    private final CvChatToolScope scope;
    private final CvChatToolOrchestrationModel model;

    CvChatToolOrchestrationService(CvChatEvidenceService evidenceService, CvChatReadToolRegistry registry,
                                   CvChatToolScope scope, CvChatToolOrchestrationModel model) {
        this.evidenceService = evidenceService;
        this.registry = registry;
        this.scope = scope;
        this.model = model;
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
        List<Long> candidateIds = validateIds(requestedCandidateIds);
        // Authorize the complete set before installing it. Missing and cross-tenant IDs retain the same generic 404.
        candidateIds.forEach(candidateId -> evidenceService.authorizeCandidate(candidateId, organizationId));
        scope.confirm(candidateIds);
        return call(message, CvChatToolOrchestrationMode.CONFIRMED_SCOPE, registry.confirmedScopeCallbacks(),
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
            CvChatToolOrchestrationOutcome outcome = model.respond(message.strip(), mode, callbacks);
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
