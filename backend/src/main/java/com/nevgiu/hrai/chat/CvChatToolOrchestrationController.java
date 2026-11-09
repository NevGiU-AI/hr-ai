package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviewed HTTP boundary for the internal read-tool orchestrator.
 *
 * <p>The response contains an application-validated workflow kind and presentation text. Successful discovery also
 * carries the minimal tenant-authorized cards returned by the exact searches that produced that decision. Those cards
 * are application data, never model output. The organization always comes from the authenticated session.</p>
 */
@RestController
@RequestMapping("/api/chat/tools")
public class CvChatToolOrchestrationController {
    private final CvChatToolOrchestrationService orchestration;

    public CvChatToolOrchestrationController(CvChatToolOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    /** Candidate discovery can register only the bounded tenant-scoped search callback. */
    @PostMapping("/discover")
    public CvChatToolDiscoveryResponse discover(@Valid @RequestBody CvChatToolDiscoveryRequest request) {
        return orchestration.discover(request.message());
    }

    /**
     * Planning begins only after the UI explicitly confirms candidate IDs and, optionally, one approved job ID.
     */
    @PostMapping("/confirmed-scope")
    public CvChatToolOrchestrationOutcome confirmedScope(
            @Valid @RequestBody CvChatToolConfirmedScopeRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {
        return orchestration.planConfirmedScope(
                request.message(), request.candidateIds(), request.jobId(), actor.organizationId());
    }
}
