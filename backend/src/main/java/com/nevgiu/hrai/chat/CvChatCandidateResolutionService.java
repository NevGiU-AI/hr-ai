package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.candidate.CandidateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Resolves candidate names only inside the authenticated organization before any evidence retrieval occurs.
 *
 * <p>This deterministic boundary—not the language model—owns candidate identity. Exact-name ambiguity is returned to
 * the caller for explicit selection, while missing and cross-tenant names share the same generic 404 response.</p>
 */
@Service
public class CvChatCandidateResolutionService {
    private static final int MAX_OPTIONS = 20;
    private final CandidateRepository candidates;

    public CvChatCandidateResolutionService(CandidateRepository candidates) {
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public CvChatCandidateResolutionResponse resolve(String requestedName, String organizationId) {
        String name = normalize(requestedName);
        Slice<CandidateIdentity> matches = candidates.findAllByExactNameAndOrganizationId(
                name, organizationId, PageRequest.of(0, MAX_OPTIONS));
        if (matches.isEmpty()) {
            // A generic result prevents callers from using this endpoint to discover another tenant's candidate names.
            throw new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found");
        }

        List<CvChatCandidateResolutionResponse.CandidateOption> options = matches.getContent().stream()
                .map(candidate -> new CvChatCandidateResolutionResponse.CandidateOption(
                        candidate.id(), candidate.name(), candidate.currentTitle()))
                .toList();
        CvChatCandidateResolutionResponse.Status status = options.size() == 1
                ? CvChatCandidateResolutionResponse.Status.RESOLVED
                : CvChatCandidateResolutionResponse.Status.AMBIGUOUS;
        return new CvChatCandidateResolutionResponse(status, List.copyOf(options), matches.hasNext());
    }

    /**
     * Returns bounded partial-name matches for explicit recruiter selection. A partial result is never automatically
     * resolved, even when only one candidate currently matches, because later tenant data could make it ambiguous.
     */
    @Transactional(readOnly = true)
    public CvChatCandidateSearchResponse search(String requestedQuery, String organizationId) {
        String query = normalize(requestedQuery);
        if (query.length() < 2) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Candidate search requires at least 2 characters");
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .distinct()
                .toList();
        // Candidate names often contain prefixes or use a different token order than a recruiter's reference
        // (for example, "Boris Zok" versus "Resume ZOK Boris"). Query by the longest literal token to keep the
        // database search selective, then require every supplied token in memory before returning any minimal card.
        String anchor = tokens.stream().max(java.util.Comparator.comparingInt(String::length)).orElse(normalized);
        Slice<CandidateIdentity> matches = candidates.searchByNameAndOrganizationId(
                '%' + escapeLike(anchor) + '%', normalized, organizationId, PageRequest.of(0, MAX_OPTIONS));
        List<CvChatCandidateSearchResponse.CandidateOption> options = matches.getContent().stream()
                .filter(candidate -> containsEveryToken(candidate.name(), tokens))
                .map(candidate -> new CvChatCandidateSearchResponse.CandidateOption(
                        candidate.id(), candidate.name(), candidate.currentTitle()))
                .toList();
        return new CvChatCandidateSearchResponse(List.copyOf(options), matches.hasNext());
    }

    private String normalize(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Candidate name is required");
        }
        // Collapse user-interface whitespace only; do not perform fuzzy matching or strip meaningful Unicode marks.
        return requestedName.strip().replaceAll("\\s+", " ");
    }

    private String escapeLike(String value) {
        // '!' is the explicit JPQL escape character; escape it before treating SQL wildcard characters literally.
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private boolean containsEveryToken(String candidateName, List<String> tokens) {
        String normalizedName = candidateName.toLowerCase(Locale.ROOT);
        return tokens.stream().allMatch(normalizedName::contains);
    }
}
