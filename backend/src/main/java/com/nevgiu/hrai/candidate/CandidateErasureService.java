package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateErasureService {
    private static final Logger log = LoggerFactory.getLogger(CandidateErasureService.class);

    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;
    private final CandidateEvaluationRepository evaluations;
    private final OriginalCvStorage storage;
    private final SecurityAuditService audit;

    public CandidateErasureService(CandidateRepository candidates, CvDocumentRepository documents,
                                   CandidateEvaluationRepository evaluations, OriginalCvStorage storage,
                                   SecurityAuditService audit) {
        this.candidates = candidates;
        this.documents = documents;
        this.evaluations = evaluations;
        this.storage = storage;
        this.audit = audit;
    }

    /**
     * Removes the complete candidate aggregate inside one database transaction. Files are moved to a recoverable
     * deletion namespace first because the filesystem cannot participate in the PostgreSQL transaction.
     */
    @Transactional
    public void erase(Long candidateId, AppUserPrincipal actor) {
        Candidate candidate = candidates.findLockedByIdAndOrganizationId(candidateId, actor.organizationId())
                .orElseThrow(() -> notFound(candidateId, actor));
        List<CvDocument> candidateDocuments = documents
                .findAllLockedByCandidateIdAndOrganizationId(candidateId, actor.organizationId());

        if (candidateDocuments.stream().anyMatch(CvDocument::isLegalHold)) {
            audit.candidateErasure(SecurityEventType.CANDIDATE_ERASURE_DENIED, SecurityEventOutcome.DENIED,
                    actor, candidateId, "reason=legal-hold");
            throw new CandidateErasureException(HttpStatus.CONFLICT, "Candidate is under legal hold");
        }

        List<StagedCvDeletion> stagedOriginals = new ArrayList<>();
        registerFileCompletion(candidateId, actor, stagedOriginals);
        try {
            for (CvDocument document : candidateDocuments) {
                if (document.getStorageKey() != null && !document.getStorageKey().isBlank()) {
                    stagedOriginals.add(storage.stageDeletion(document.getStorageKey()));
                }
            }
        } catch (OriginalCvStorageException exception) {
            audit.candidateErasure(SecurityEventType.CANDIDATE_ERASURE_FAILED, SecurityEventOutcome.FAILURE,
                    actor, candidateId, "reason=storage-unavailable");
            throw new CandidateErasureException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Original CV storage is unavailable");
        }

        // Delete dependants explicitly before their candidate so foreign keys remain valid throughout the transaction.
        evaluations.deleteAllByCandidateIdAndOrganizationId(candidateId, actor.organizationId());
        documents.deleteAll(candidateDocuments);
        documents.flush();
        candidates.delete(candidate);
        candidates.flush();
    }

    private void registerFileCompletion(Long candidateId, AppUserPrincipal actor,
                                        List<StagedCvDeletion> stagedOriginals) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    purge(candidateId, actor, stagedOriginals);
                } else {
                    restore(candidateId, actor, stagedOriginals);
                }
            }
        });
    }

    private void purge(Long candidateId, AppUserPrincipal actor, List<StagedCvDeletion> stagedOriginals) {
        try {
            for (StagedCvDeletion staged : stagedOriginals) {
                storage.delete(staged.deletionKey());
            }
            audit.candidateErasure(SecurityEventType.CANDIDATE_ERASED, SecurityEventOutcome.SUCCESS,
                    actor, candidateId, "originals=" + stagedOriginals.size());
        } catch (RuntimeException exception) {
            log.error("Unable to purge staged originals after candidate erasure for candidateId={}", candidateId);
            audit.candidateErasure(SecurityEventType.CANDIDATE_ERASURE_FAILED, SecurityEventOutcome.FAILURE,
                    actor, candidateId, "reason=purge-failed");
        }
    }

    private void restore(Long candidateId, AppUserPrincipal actor, List<StagedCvDeletion> stagedOriginals) {
        for (StagedCvDeletion staged : stagedOriginals) {
            try {
                storage.restoreDeletion(staged);
            } catch (RuntimeException exception) {
                log.error("Unable to restore staged original after candidate-erasure rollback for candidateId={}",
                        candidateId);
                audit.candidateErasure(SecurityEventType.CANDIDATE_ERASURE_FAILED, SecurityEventOutcome.FAILURE,
                        actor, candidateId, "reason=rollback-restore-failed");
            }
        }
    }

    private CandidateErasureException notFound(Long candidateId, AppUserPrincipal actor) {
        // A uniform 404 hides whether the identifier exists in another organization.
        audit.candidateErasure(SecurityEventType.CANDIDATE_ERASURE_DENIED, SecurityEventOutcome.DENIED,
                actor, candidateId, "status=404");
        return new CandidateErasureException(HttpStatus.NOT_FOUND, "Candidate not found");
    }
}
