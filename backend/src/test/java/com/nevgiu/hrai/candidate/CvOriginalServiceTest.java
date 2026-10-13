package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.OriginalCvStorageException;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvOriginalServiceTest {
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final OriginalCvStorage storage = mock(OriginalCvStorage.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CvOriginalService service = new CvOriginalService(documents, storage, audit);
    private final AppUserPrincipal actor = new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void downloadsOnlyTheTenantScopedStoredOriginal() {
        CvDocument document = document("opaque/file.pdf");
        when(documents.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));
        when(storage.read("opaque/file.pdf")).thenReturn("pdf".getBytes());

        CvOriginalDownload result = service.download(7L, actor);

        assertThat(result.content()).isEqualTo("pdf".getBytes());
        verify(audit).cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESSED, SecurityEventOutcome.SUCCESS,
                actor, 7L, null);
    }

    @Test
    void returnsNotFoundWithoutFallingBackToAnUnscopedLookup() {
        when(documents.findByIdAndOrganizationId(9L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(9L, actor))
                .isInstanceOfSatisfying(CvOriginalAccessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(audit).cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESS_DENIED, SecurityEventOutcome.DENIED,
                actor, 9L, "status=404");
    }

    @Test
    void auditsAStorageFailureWithoutExposingStorageDetails() {
        CvDocument document = document("opaque/file.pdf");
        when(documents.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));
        when(storage.read("opaque/file.pdf")).thenThrow(new OriginalCvStorageException("private path", null));

        assertThatThrownBy(() -> service.download(7L, actor))
                .isInstanceOfSatisfying(CvOriginalAccessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(audit).cvOriginalAction(SecurityEventType.CV_ORIGINAL_ACCESSED, SecurityEventOutcome.FAILURE,
                actor, 7L, "reason=storage-unavailable");
    }

    @Test
    void purgesStagedBytesOnlyAfterDatabaseCommit() {
        CvDocument document = document("opaque/file.pdf");
        StagedCvDeletion staged = new StagedCvDeletion("opaque/file.pdf", "_deletion/staged.pdf");
        when(documents.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));
        when(storage.stageDeletion("opaque/file.pdf")).thenReturn(staged);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(7L, actor);

        assertThat(document.getStorageKey()).isNull();
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(storage).delete("_deletion/staged.pdf");
        verify(audit).cvOriginalAction(SecurityEventType.CV_ORIGINAL_DELETED, SecurityEventOutcome.SUCCESS,
                actor, 7L, null);
    }

    @Test
    void restoresStagedBytesAfterDatabaseRollback() {
        CvDocument document = document("opaque/file.pdf");
        StagedCvDeletion staged = new StagedCvDeletion("opaque/file.pdf", "_deletion/staged.pdf");
        when(documents.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));
        when(storage.stageDeletion("opaque/file.pdf")).thenReturn(staged);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(7L, actor);

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage).restoreDeletion(staged);
    }

    private CvDocument document(String storageKey) {
        CvDocument document = new CvDocument();
        document.setOrganizationId("tenant-a");
        document.setOriginalFilename("candidate.pdf");
        document.setContentType("application/pdf");
        document.setStorageKey(storageKey);
        document.setStoredAt(Instant.now());
        document.setRetentionUntil(Instant.now().plusSeconds(3600));
        return document;
    }
}
