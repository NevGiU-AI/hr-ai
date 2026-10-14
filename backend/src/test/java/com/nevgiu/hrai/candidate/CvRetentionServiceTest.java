package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvRetentionServiceTest {
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final OriginalCvStorage storage = mock(OriginalCvStorage.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CvRetentionService service = new CvRetentionService(documents, storage, audit);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void expiresAndPurgesOriginalOnlyAfterCommit() {
        CvDocument document = expiredDocument();
        when(documents.findLockedById(7L)).thenReturn(Optional.of(document));
        when(storage.stageDeletion("opaque/file.pdf"))
                .thenReturn(new StagedCvDeletion("opaque/file.pdf", "_deletion/staged.pdf"));
        TransactionSynchronizationManager.initSynchronization();

        assertThat(service.expire(7L)).isTrue();
        assertThat(document.getStorageKey()).isNull();

        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).delete("_deletion/staged.pdf");
        verify(audit).cvOriginalSystemAction(SecurityEventType.CV_ORIGINAL_EXPIRED,
                SecurityEventOutcome.SUCCESS, "tenant-a", 7L, null);
    }

    @Test
    void skipsOriginalUnderLegalHold() {
        CvDocument document = expiredDocument();
        document.setLegalHold(true);
        when(documents.findLockedById(7L)).thenReturn(Optional.of(document));

        assertThat(service.expire(7L)).isFalse();
        verify(storage, never()).stageDeletion("opaque/file.pdf");
    }

    private CvDocument expiredDocument() {
        CvDocument document = new CvDocument();
        document.setOrganizationId("tenant-a");
        document.setStorageKey("opaque/file.pdf");
        document.setStoredAt(Instant.now().minusSeconds(7200));
        document.setRetentionUntil(Instant.now().minusSeconds(3600));
        return document;
    }
}
