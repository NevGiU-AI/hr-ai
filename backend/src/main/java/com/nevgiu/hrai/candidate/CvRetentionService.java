package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

@Service
public class CvRetentionService {
    private static final Logger log = LoggerFactory.getLogger(CvRetentionService.class);
    private final CvDocumentRepository documents;
    private final OriginalCvStorage storage;
    private final SecurityAuditService audit;

    public CvRetentionService(CvDocumentRepository documents, OriginalCvStorage storage, SecurityAuditService audit) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    /*
     * REQUIRES_NEW gives every document an independent commit/rollback boundary. A failed file therefore rolls back
     * only its own metadata change and the scheduler can continue with the remaining batch. It also guarantees that
     * the pessimistic lock obtained below belongs to this short transaction and is released after this item finishes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long documentId) {
        // Every candidate is processed in its own transaction. The database lock coordinates concurrent schedulers,
        // administrator deletion, and legal-hold changes across all backend replicas.
        CvDocument document = documents.findLockedById(documentId).orElse(null);
        // Re-evaluate every eligibility condition under the lock because the scheduler's initial ID query is only a
        // candidate scan and may be stale by the time this transaction starts.
        if (document == null || document.getStorageKey() == null || document.isLegalHold()
                || document.getRetentionUntil() == null || document.getRetentionUntil().isAfter(Instant.now())) {
            return false;
        }

        StagedCvDeletion staged;
        try {
            staged = storage.stageDeletion(document.getStorageKey());
        } catch (OriginalCvStorageException exception) {
            audit.cvOriginalSystemAction(SecurityEventType.CV_ORIGINAL_EXPIRED, SecurityEventOutcome.FAILURE,
                    document.getOrganizationId(), documentId, "reason=storage-unavailable");
            throw new CvOriginalAccessException(HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }

        String organizationId = document.getOrganizationId();
        // This slice expires the submitted binary only. Candidate data, extracted text, and evaluations belong to the
        // separately confirmed full-erasure workflow and must not be silently removed by this scheduler.
        document.setStorageKey(null);
        document.setStoredAt(null);
        document.setRetentionUntil(null);
        documents.saveAndFlush(document);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // The filesystem cannot join the PostgreSQL transaction. Keep the staged bytes recoverable until the
                // metadata commit is known, then either purge them or compensate by restoring the original key.
                if (status == STATUS_COMMITTED) {
                    purgeExpired(staged, organizationId, documentId);
                } else {
                    restoreExpired(staged, organizationId, documentId);
                }
            }
        });
        return true;
    }

    private void purgeExpired(StagedCvDeletion staged, String organizationId, Long documentId) {
        try {
            storage.delete(staged.deletionKey());
            audit.cvOriginalSystemAction(SecurityEventType.CV_ORIGINAL_EXPIRED, SecurityEventOutcome.SUCCESS,
                    organizationId, documentId, null);
        } catch (RuntimeException exception) {
            log.error("Unable to purge expired original CV for documentId={}", documentId);
            audit.cvOriginalSystemAction(SecurityEventType.CV_ORIGINAL_EXPIRED, SecurityEventOutcome.FAILURE,
                    organizationId, documentId, "reason=purge-failed");
        }
    }

    private void restoreExpired(StagedCvDeletion staged, String organizationId, Long documentId) {
        try {
            storage.restoreDeletion(staged);
        } catch (RuntimeException exception) {
            log.error("Unable to restore expired original CV after rollback for documentId={}", documentId);
            audit.cvOriginalSystemAction(SecurityEventType.CV_ORIGINAL_EXPIRED, SecurityEventOutcome.FAILURE,
                    organizationId, documentId, "reason=rollback-restore-failed");
        }
    }
}
