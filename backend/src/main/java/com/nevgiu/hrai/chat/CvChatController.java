package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/candidates")
public class CvChatController {
    private final CvChatService service;
    private final CvChatCandidateResolutionService candidateResolution;
    private final CvChatComparisonService comparisonService;
    private final CvChatLanguageScopeService languageScopeService;

    public CvChatController(CvChatService service, CvChatCandidateResolutionService candidateResolution,
                            CvChatComparisonService comparisonService,
                            CvChatLanguageScopeService languageScopeService) {
        this.service = service;
        this.candidateResolution = candidateResolution;
        this.comparisonService = comparisonService;
        this.languageScopeService = languageScopeService;
    }

    /**
     * Converts an exact human-readable candidate name into tenant-verified candidate IDs before later retrieval.
     * The authenticated organization is server-owned and never accepted from the request or an AI-generated argument.
     */
    @PostMapping("/resolve")
    public CvChatCandidateResolutionResponse resolve(@Valid @RequestBody CvChatCandidateResolutionRequest request,
                                                     @AuthenticationPrincipal AppUserPrincipal actor) {
        return candidateResolution.resolve(request.name(), actor.organizationId());
    }

    /** Returns tenant-scoped partial-name choices; the caller must explicitly select an ID before CV retrieval. */
    @PostMapping("/search")
    public CvChatCandidateSearchResponse search(@Valid @RequestBody CvChatCandidateSearchRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal actor) {
        return candidateResolution.search(request.query(), actor.organizationId());
    }

    /** Proposes tenant-scoped candidate cards from natural language; no CV evidence is accessed before confirmation. */
    @PostMapping("/scope")
    public CvChatLanguageScopeResponse proposeScope(@Valid @RequestBody CvChatLanguageScopeRequest request,
                                                    @AuthenticationPrincipal AppUserPrincipal actor) {
        return languageScopeService.propose(request.message(), actor.organizationId());
    }

    /** Compares only the explicit candidate IDs after the service verifies every ID in the authenticated tenant. */
    @PostMapping("/compare")
    public CvChatComparisonResponse compare(@Valid @RequestBody CvChatComparisonRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal actor) {
        return comparisonService.compare(
                request.candidateIds(), request.message(), request.jobId(), actor.organizationId());
    }

    /**
     * Answers one question about an explicitly selected candidate.
     *
     * <p>The organization is always taken from the authenticated server-side principal. It is intentionally absent
     * from the request body so a caller or the model cannot select a different tenant.</p>
     */
    @PostMapping("/{candidateId}")
    public CvChatResponse ask(@PathVariable Long candidateId, @Valid @RequestBody CvChatRequest request,
                              @AuthenticationPrincipal AppUserPrincipal actor) {
        return service.ask(candidateId, request.message(), actor.organizationId());
    }
}
