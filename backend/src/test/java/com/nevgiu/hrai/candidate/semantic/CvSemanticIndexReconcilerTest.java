package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvSemanticIndexReconcilerTest {
    private final CvSemanticIndexRepository repository = mock(CvSemanticIndexRepository.class);
    private final CvSemanticIndexCoordinator coordinator = mock(CvSemanticIndexCoordinator.class);

    @Test
    void backfillsOnlyTheBoundedPendingBatchWhenEnabled() {
        CvSemanticIndexProperties properties = properties(true);
        when(repository.findPending(50)).thenReturn(List.of(
                new CvSemanticIndexTarget(9L, "tenant-a"), new CvSemanticIndexTarget(12L, "tenant-b")));

        new CvSemanticIndexReconciler(repository, coordinator, properties).reconcile();

        verify(coordinator).request(9L, "tenant-a");
        verify(coordinator).request(12L, "tenant-b");
    }

    @Test
    void doesNotReadTheIndexQueueWhenDisabled() {
        new CvSemanticIndexReconciler(repository, coordinator, properties(false)).reconcile();

        verify(repository, never()).findPending(50);
    }

    private CvSemanticIndexProperties properties(boolean enabled) {
        return new CvSemanticIndexProperties(enabled, 1200, 200, 100, 32, 50, "0 11 * * * *");
    }
}
