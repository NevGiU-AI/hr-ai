package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies recruiter corrections without modifying the submitted CV or its extracted source text. */
@Service
public class CandidateCorrectionService {
    private final CandidateRepository candidates;
    private final CandidateMetadataRevisionRepository revisions;
    private final SecurityAuditService audit;

    public CandidateCorrectionService(CandidateRepository candidates,
                                      CandidateMetadataRevisionRepository revisions,
                                      SecurityAuditService audit) {
        this.candidates = candidates;
        this.revisions = revisions;
        this.audit = audit;
    }

    /**
     * Updates the tenant-owned candidate and its immutable revision snapshot atomically. The organization comes only
     * from the authenticated principal; returning 404 for a foreign identifier avoids revealing cross-tenant records.
     */
    @Transactional
    public void update(Long candidateId, UpdateCandidateMetadataRequest request, AppUserPrincipal actor) {
        // Locking prevents two editors from silently producing snapshots from the same candidate state.
        Candidate candidate = candidates.findLockedByIdAndOrganizationId(candidateId, actor.organizationId())
                .orElseThrow(() -> new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Candidate not found"));
        candidate.setName(request.name().trim());
        candidate.setEmail(normalize(request.email()));
        candidate.setLocation(normalize(request.location()));
        candidate.setCurrentTitle(normalize(request.currentTitle()));
        candidate.setYearsExperience(request.yearsExperience());
        candidates.save(candidate);
        // Persist the resulting state rather than a diff so each revision remains independently understandable.
        revisions.save(new CandidateMetadataRevision(candidate, actor));
        audit.candidateAction(SecurityEventType.CANDIDATE_METADATA_CORRECTED, SecurityEventOutcome.SUCCESS,
                actor, candidateId, null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
