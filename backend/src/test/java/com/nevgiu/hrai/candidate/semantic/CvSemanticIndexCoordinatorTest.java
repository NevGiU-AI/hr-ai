package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CvSemanticIndexCoordinatorTest {
    private final CvSemanticIndexService indexService = mock(CvSemanticIndexService.class);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void doesNothingWhenSemanticIndexingIsDisabled() {
        CvSemanticIndexCoordinator coordinator = new CvSemanticIndexCoordinator(indexService, properties(false));

        coordinator.request(9L, "tenant-a");

        verify(indexService, never()).index(9L, "tenant-a");
    }

    @Test
    void indexesImmediatelyOutsideATransaction() {
        CvSemanticIndexCoordinator coordinator = new CvSemanticIndexCoordinator(indexService, properties(true));

        coordinator.request(9L, "tenant-a");

        verify(indexService).index(9L, "tenant-a");
    }

    @Test
    void indexesOnlyAfterTheOwningTransactionCommits() {
        CvSemanticIndexCoordinator coordinator = new CvSemanticIndexCoordinator(indexService, properties(true));
        TransactionSynchronizationManager.initSynchronization();

        coordinator.request(9L, "tenant-a");
        verify(indexService, never()).index(9L, "tenant-a");
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(indexService).index(9L, "tenant-a");
    }

    @Test
    void discardsARequestWhenTheOwningTransactionRollsBack() {
        CvSemanticIndexCoordinator coordinator = new CvSemanticIndexCoordinator(indexService, properties(true));
        TransactionSynchronizationManager.initSynchronization();

        coordinator.request(9L, "tenant-a");
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(indexService, never()).index(9L, "tenant-a");
    }

    private CvSemanticIndexProperties properties(boolean enabled) {
        return new CvSemanticIndexProperties(enabled, 1200, 200, 100, 32, 50, "0 11 * * * *");
    }
}
