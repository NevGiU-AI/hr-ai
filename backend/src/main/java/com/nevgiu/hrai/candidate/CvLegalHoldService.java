package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CvLegalHoldService {
    private final CvDocumentRepository documents;
    private final SecurityAuditService audit;

    public CvLegalHoldService(CvDocumentRepository documents, SecurityAuditService audit) {
        this.documents = documents;
        this.audit = audit;
    }

    /*
     * The transaction keeps the pessimistic row lock until the hold metadata is committed or rolled back. Returning
     * the expiry decision lets the controller start expiry afterward, in a new transaction that can see the committed
     * hold release; invoking expiry before this transaction ends would still observe the old locked database state.
     */
    @Transactional
    public boolean update(Long documentId, boolean held, AppUserPrincipal actor) {
        // Serialize hold changes with manual deletion and scheduled expiry. The organization predicate also prevents
        // an administrator from learning whether another tenant owns the supplied identifier.
        CvDocument document = documents.findLockedByIdAndOrganizationId(documentId, actor.organizationId())
                .orElseThrow(() -> notFound(documentId, actor));
        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw notFound(documentId, actor);
        }

        if (document.isLegalHold() == held) {
            // Keep this operation idempotent, but still tell the controller to expire an already-released overdue file.
            return !held && isExpired(document);
        }

        document.setLegalHold(held);
        document.setLegalHoldAt(held ? Instant.now() : null);
        document.setLegalHoldByUserId(held ? actor.id() : null);
        documents.saveAndFlush(document);
        audit.cvOriginalAction(held ? SecurityEventType.CV_LEGAL_HOLD_APPLIED : SecurityEventType.CV_LEGAL_HOLD_RELEASED,
                SecurityEventOutcome.SUCCESS, actor, documentId, null);
        // Expiry runs only after this transaction returns and commits, so it can observe the released hold.
        return !held && isExpired(document);
    }

    private boolean isExpired(CvDocument document) {
        return document.getRetentionUntil() != null && !document.getRetentionUntil().isAfter(Instant.now());
    }

    private CvOriginalAccessException notFound(Long documentId, AppUserPrincipal actor) {
        audit.cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESS_DENIED, SecurityEventOutcome.DENIED,
                actor, documentId, "status=404");
        return new CvOriginalAccessException(HttpStatus.NOT_FOUND, "Original CV not found");
    }
}
