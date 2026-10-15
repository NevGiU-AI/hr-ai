package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.candidate.storage.OriginalCvStorage;
import com.nevgiu.hrai.candidate.storage.StagedCvDeletion;
import com.nevgiu.hrai.evaluation.CandidateEvaluationRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateErasureServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CandidateEvaluationRepository evaluations = mock(CandidateEvaluationRepository.class);
    private final OriginalCvStorage storage = mock(OriginalCvStorage.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CandidateErasureService service =
            new CandidateErasureService(candidates, documents, evaluations, storage, audit);
    private final AppUserPrincipal actor = new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void erasesDependantsAndPurgesOriginalOnlyAfterCommit() {
        Candidate candidate = candidate(7L);
        CvDocument document = document("opaque/original.pdf", false);
        StagedCvDeletion staged = new StagedCvDeletion("opaque/original.pdf", "_deletion/staged.pdf");
        when(candidates.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllLockedByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of(document));
        when(storage.stageDeletion("opaque/original.pdf")).thenReturn(staged);
        TransactionSynchronizationManager.initSynchronization();

        service.erase(7L, actor);

        var databaseOrder = inOrder(evaluations, documents, candidates);
        databaseOrder.verify(evaluations).deleteAllByCandidateIdAndOrganizationId(7L, "tenant-a");
        databaseOrder.verify(documents).deleteAll(List.of(document));
        databaseOrder.verify(documents).flush();
        databaseOrder.verify(candidates).delete(candidate);
        databaseOrder.verify(candidates).flush();
        verify(storage, never()).delete(staged.deletionKey());

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(storage).delete(staged.deletionKey());
        verify(audit).candidateErasure(SecurityEventType.CANDIDATE_ERASED, SecurityEventOutcome.SUCCESS,
                actor, 7L, "storedOriginalsDeleted=1");
    }

    @Test
    void restoresStagedOriginalWhenDatabaseTransactionRollsBack() {
        Candidate candidate = candidate(7L);
        CvDocument document = document("opaque/original.pdf", false);
        StagedCvDeletion staged = new StagedCvDeletion("opaque/original.pdf", "_deletion/staged.pdf");
        when(candidates.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllLockedByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of(document));
        when(storage.stageDeletion("opaque/original.pdf")).thenReturn(staged);
        TransactionSynchronizationManager.initSynchronization();

        service.erase(7L, actor);
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(storage).restoreDeletion(staged);
        verify(storage, never()).delete(staged.deletionKey());
    }

    @Test
    void blocksTheWholeErasureWhenAnyDocumentHasALegalHold() {
        Candidate candidate = candidate(7L);
        when(candidates.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllLockedByCandidateIdAndOrganizationId(7L, "tenant-a"))
                .thenReturn(List.of(document("opaque/original.pdf", true)));

        assertThatThrownBy(() -> service.erase(7L, actor))
                .isInstanceOfSatisfying(CandidateErasureException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));

        verify(evaluations, never()).deleteAllByCandidateIdAndOrganizationId(7L, "tenant-a");
        verify(audit).candidateErasure(SecurityEventType.CANDIDATE_ERASURE_DENIED, SecurityEventOutcome.DENIED,
                actor, 7L, "reason=legal-hold");
    }

    @Test
    void returnsTenantSafeNotFound() {
        when(candidates.findLockedByIdAndOrganizationId(9L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.erase(9L, actor))
                .isInstanceOfSatisfying(CandidateErasureException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(audit).candidateErasure(SecurityEventType.CANDIDATE_ERASURE_DENIED, SecurityEventOutcome.DENIED,
                actor, 9L, "status=404");
    }

    private Candidate candidate(Long id) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setOrganizationId("tenant-a");
        return candidate;
    }

    private CvDocument document(String storageKey, boolean legalHold) {
        CvDocument document = new CvDocument();
        document.setOrganizationId("tenant-a");
        document.setStorageKey(storageKey);
        document.setLegalHold(legalHold);
        return document;
    }
}
