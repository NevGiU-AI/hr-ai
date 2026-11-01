package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.candidate.CandidateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private String normalize(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Candidate name is required");
        }
        // Collapse user-interface whitespace only; do not perform fuzzy matching or strip meaningful Unicode marks.
        return requestedName.strip().replaceAll("\\s+", " ");
    }
}
