package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
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

@Service
public class CvOriginalService {
    private static final Logger log = LoggerFactory.getLogger(CvOriginalService.class);

    private final CvDocumentRepository documents;
    private final OriginalCvStorage storage;
    private final SecurityAuditService audit;

    public CvOriginalService(CvDocumentRepository documents, OriginalCvStorage storage, SecurityAuditService audit) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public CvOriginalDownload download(Long documentId, AppUserPrincipal actor) {
        // Authorization supplies the role boundary; this lookup independently enforces the tenant boundary.
        CvDocument document = findStored(documentId, actor);
        try {
            byte[] content = storage.read(document.getStorageKey());
            audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESSED, SecurityEventOutcome.SUCCESS,
                    actor, documentId, null);
            return new CvOriginalDownload(document.getOriginalFilename(), document.getContentType(), content.length, content);
        } catch (OriginalCvStorageException exception) {
            audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESSED, SecurityEventOutcome.FAILURE,
                    actor, documentId, "reason=storage-unavailable");
            throw new CvOriginalAccessException(HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }
    }

    @Transactional
    public void delete(Long documentId, AppUserPrincipal actor) {
        CvDocument document = findStored(documentId, actor);
        StagedCvDeletion staged;
        try {
            // Move first instead of deleting immediately so a later database failure can restore the bytes.
            staged = storage.stageDeletion(document.getStorageKey());
        } catch (OriginalCvStorageException exception) {
            audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_DELETED, SecurityEventOutcome.FAILURE,
                    actor, documentId, "reason=storage-unavailable");
            throw new CvOriginalAccessException(HttpStatus.SERVICE_UNAVAILABLE, "Original CV storage is unavailable");
        }

        document.setStorageKey(null);
        document.setStoredAt(null);
        document.setRetentionUntil(null);
        // Flush while the file is still recoverable so a constraint/database failure triggers rollback compensation.
        documents.saveAndFlush(document);

        // The filesystem cannot participate in the database transaction. Keep the bytes recoverable until commit,
        // then purge them; on rollback, move them back to the original opaque key.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    try {
                        // The database no longer references the original, so the staged copy may now be purged.
                        storage.delete(staged.deletionKey());
                        audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_DELETED, SecurityEventOutcome.SUCCESS,
                                actor, documentId, null);
                    } catch (RuntimeException exception) {
                        log.error("Unable to purge staged original CV for documentId={}", documentId);
                        audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_DELETED, SecurityEventOutcome.FAILURE,
                                actor, documentId, "reason=purge-failed");
                    }
                } else {
                    try {
                        // Preserve the pre-request state when the metadata update did not commit.
                        storage.restoreDeletion(staged);
                    } catch (RuntimeException exception) {
                        log.error("Unable to restore staged original CV after rollback for documentId={}", documentId);
                        audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_DELETED, SecurityEventOutcome.FAILURE,
                                actor, documentId, "reason=rollback-restore-failed");
                    }
                }
            }
        });
    }

    private CvDocument findStored(Long documentId, AppUserPrincipal actor) {
        // Never load by document ID alone: the organization is derived from the authenticated server-side principal.
        CvDocument document = documents.findByIdAndOrganizationId(documentId, actor.organizationId()).orElse(null);
        if (document == null || document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            // A uniform 404 prevents callers from discovering another tenant's document IDs or storage state.
            audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESS_DENIED, SecurityEventOutcome.DENIED,
                    actor, documentId, "status=404");
            throw new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Original CV not found");
        }
        return document;
    }
}
