package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidateDetailsService {
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;

    public CandidateDetailsService(CandidateRepository candidates, CvDocumentRepository documents) {
        this.candidates = candidates;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public CandidateDetailsResponse find(Long candidateId, AppUserPrincipal actor) {
        // The organization predicate deliberately makes missing and cross-tenant identifiers indistinguishable.
        Candidate candidate = candidates.findByIdAndOrganizationId(candidateId, actor.organizationId())
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Candidate not found"));

        // Capabilities keep the UI aligned with role policy, but are never a substitute for endpoint authorization.
        boolean administrator = hasRole(actor, "ROLE_ADMIN");
        boolean downloadAllowed = administrator || hasRole(actor, "ROLE_RECRUITER");
        List<CandidateDetailsResponse.Document> history = documents
                .findAllByCandidateIdAndOrganizationId(candidateId, actor.organizationId())
                .stream()
                .map(document -> documentResponse(document, downloadAllowed, administrator))
                .toList();
        return new CandidateDetailsResponse(candidate.getId(), candidate.getName(), candidate.getEmail(),
                candidate.getLocation(), candidate.getCurrentTitle(), candidate.getYearsExperience(),
                candidate.getCreatedAt(), history);
    }

    private CandidateDetailsResponse.Document documentResponse(CvDocument document, boolean downloadAllowed,
                                                                 boolean administrator) {
        // Storage existence is reduced to a boolean; the provider-specific storage key must remain server-side.
        boolean originalAvailable = document.getStorageKey() != null;
        return new CandidateDetailsResponse.Document(document.getId(), document.getOriginalFilename(),
                document.getContentType(), document.getFileSize(), document.getSource(), document.getStatus(),
                document.getTextLength(), document.getImportedAt(), originalAvailable, document.getStoredAt(),
                document.getRetentionUntil(), document.isLegalHold(), document.getLegalHoldAt(),
                originalAvailable && downloadAllowed, administrator);
    }

    private boolean hasRole(AppUserPrincipal actor, String role) {
        return actor.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
