package com.nevgiu.hrai.candidate.semantic;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Retries failed indexing and gradually backfills eligible documents created before indexing was enabled. */
@Service
class CvSemanticIndexReconciler {
    private final CvSemanticIndexRepository repository;
    private final CvSemanticIndexCoordinator coordinator;
    private final CvSemanticIndexProperties properties;

    CvSemanticIndexReconciler(CvSemanticIndexRepository repository, CvSemanticIndexCoordinator coordinator,
                              CvSemanticIndexProperties properties) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.properties = properties;
    }

    /**
     * Processes one bounded batch per schedule rather than attempting an unbounded startup backfill.
     * Failed provider calls remain pending and are naturally retried by a later run.
     */
    @Scheduled(cron = "${app.cv-semantic-index.reconciliation-cron:0 11 * * * *}")
    void reconcile() {
        if (!properties.enabled()) return;
        repository.findPending(properties.reconciliationBatchSize())
                .forEach(target -> coordinator.request(target.documentId(), target.organizationId()));
    }
}
