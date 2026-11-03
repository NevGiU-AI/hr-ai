package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.chat.CvChatCandidateResolutionService;
import com.nevgiu.hrai.chat.CvChatCandidateSearchResponse;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Set;

/**
 * Read-only Spring AI tools whose tenant boundary comes exclusively from the authenticated server-side principal.
 * Tool arguments deliberately contain no organization identifier, role, or authorization flag that a model could
 * invent or override.
 *
 * <p>{@link RequestScope} gives each HTTP request its own tool object. Spring injects a scoped proxy into longer-lived
 * collaborators and resolves that proxy to the current request's instance only when a tool is used. Consequently no
 * mutable tool lifecycle or authenticated context can be shared between concurrent users or organizations.</p>
 */
@Component
@RequestScope
public class CvChatReadTools {
    private static final Set<String> CHAT_ROLES = Set.of("ROLE_ADMIN", "ROLE_RECRUITER", "ROLE_REVIEWER");
    private static final int MAX_SEARCH_RESULTS = 10;

    private final CvChatCandidateResolutionService candidateResolution;
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;
    private final CvChatToolExecutionGuard executionGuard;
    private final CvChatToolScope scope;

    public CvChatReadTools(CvChatCandidateResolutionService candidateResolution, CandidateRepository candidates,
                           CvDocumentRepository documents, CvChatToolExecutionGuard executionGuard,
                           CvChatToolScope scope) {
        this.candidateResolution = candidateResolution;
        this.candidates = candidates;
        this.documents = documents;
        this.executionGuard = executionGuard;
        this.scope = scope;
    }

    @Tool(name = "search_candidates", description = "Search the authenticated organization's candidates by a "
            + "partial person name. Returns bounded minimal cards for explicit recruiter selection; never select a "
            + "candidate automatically when multiple cards are returned.")
    public CandidateSearchResult searchCandidates(
            @ToolParam(description = "At least two characters from the candidate's name") String query) {
        // Resolve and authorize the principal on the HTTP request thread. Spring Security context is thread-local and
        // must not be looked up later inside the executor's virtual thread.
        AppUserPrincipal actor = currentChatActor();
        CvChatCandidateSearchResponse response = executionGuard.execute(
                () -> candidateResolution.search(query, actor.organizationId()));
        List<CandidateCard> cards = response.candidates().stream().limit(MAX_SEARCH_RESULTS)
                .map(candidate -> new CandidateCard(candidate.id(), candidate.name(), candidate.currentTitle()))
                .toList();
        return new CandidateSearchResult(cards,
                response.truncated() || response.candidates().size() > MAX_SEARCH_RESULTS);
    }

    @Tool(name = "get_candidate_summary", description = "Return a minimized, tenant-authorized summary for one "
            + "candidate ID that the recruiter already selected. Never returns raw CV text, contact details, "
            + "storage identifiers, content hashes, or metadata revision history.")
    public CandidateSummary getCandidateSummary(
            @ToolParam(description = "Explicitly selected candidate database ID") Long candidateId) {
        // Capture only the trusted tenant identifier before crossing the virtual-thread execution boundary. The model
        // supplies candidateId, but can never supply or replace actor.organizationId().
        AppUserPrincipal actor = currentChatActor();
        // Prompt instructions are not an authorization boundary. Reject any model-supplied ID that was not explicitly
        // authorized and installed into this request's confirmed scope by the orchestrator.
        scope.requireConfirmed(candidateId);
        return executionGuard.execute(() -> {
            Candidate candidate = candidates.findByIdAndOrganizationId(candidateId, actor.organizationId())
                    // Missing and cross-tenant IDs intentionally share one response to prevent tenant enumeration.
                    .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
            CvDocument currentDocument = documents
                    .findAllByCandidateIdAndOrganizationId(candidateId, actor.organizationId())
                    .stream().findFirst().orElse(null);
            CurrentDocumentStatus documentStatus = currentDocument == null ? null : new CurrentDocumentStatus(
                    currentDocument.getId(), currentDocument.getStatus(), currentDocument.getExtractionMethod(),
                    currentDocument.getImportedAt(), currentDocument.getReprocessedAt());
            return new CandidateSummary(candidate.getId(), candidate.getName(), candidate.getCurrentTitle(),
                    candidate.getYearsExperience(), documentStatus);
        });
    }

    private AppUserPrincipal currentChatActor() {
        // Endpoint authorization remains the outer boundary; this independent check protects future non-controller
        // callers and prevents a tool callback from becoming a role-policy bypass.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)) {
            throw new CvChatException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean permitted = actor.getAuthorities().stream()
                .anyMatch(authority -> CHAT_ROLES.contains(authority.getAuthority()));
        if (!permitted) {
            throw new CvChatException(HttpStatus.FORBIDDEN, "CV chat access denied");
        }
        return actor;
    }
}
