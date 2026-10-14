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

    @Transactional
    public boolean update(Long documentId, boolean held, AppUserPrincipal actor) {
        CvDocument document = documents.findLockedByIdAndOrganizationId(documentId, actor.organizationId())
                .orElseThrow(() -> notFound(documentId, actor));
        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw notFound(documentId, actor);
        }

        if (document.isLegalHold() == held) {
            return !held && isExpired(document);
        }

        document.setLegalHold(held);
        document.setLegalHoldAt(held ? Instant.now() : null);
        document.setLegalHoldByUserId(held ? actor.id() : null);
        documents.saveAndFlush(document);
        audit.cvOriginalAction(held ? SecurityEventType.CV_LEGAL_HOLD_APPLIED : SecurityEventType.CV_LEGAL_HOLD_RELEASED,
                SecurityEventOutcome.SUCCESS, actor, documentId, null);
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
