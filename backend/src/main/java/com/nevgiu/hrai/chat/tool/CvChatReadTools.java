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
 * invent or override. The request scope also prevents authenticated context from leaking between HTTP requests.
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

    public CvChatReadTools(CvChatCandidateResolutionService candidateResolution, CandidateRepository candidates,
                           CvDocumentRepository documents, CvChatToolExecutionGuard executionGuard) {
        this.candidateResolution = candidateResolution;
        this.candidates = candidates;
        this.documents = documents;
        this.executionGuard = executionGuard;
    }

    @Tool(name = "search_candidates", description = "Search the authenticated organization's candidates by a "
            + "partial person name. Returns bounded minimal cards for explicit recruiter selection; never select a "
            + "candidate automatically when multiple cards are returned.")
    public CandidateSearchResult searchCandidates(
            @ToolParam(description = "At least two characters from the candidate's name") String query) {
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
        AppUserPrincipal actor = currentChatActor();
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
