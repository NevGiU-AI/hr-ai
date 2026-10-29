package com.nevgiu.hrai.candidate.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;

/** Defers indexing until commit and prevents an embedding outage from invalidating a successful CV operation. */
@Service
public class CvSemanticIndexCoordinator {
    private static final Logger log = LoggerFactory.getLogger(CvSemanticIndexCoordinator.class);

    private final CvSemanticIndexService indexService;
    private final CvSemanticIndexProperties properties;

    CvSemanticIndexCoordinator(CvSemanticIndexService indexService, CvSemanticIndexProperties properties) {
        this.indexService = indexService;
        this.properties = properties;
    }

    /**
     * Requests best-effort indexing for a committed, tenant-owned CV document.
     * When called inside a transaction, the callback is deliberately discarded on rollback so vector evidence can
     * never describe business data that was not committed.
     */
    public void request(Long documentId, String organizationId) {
        if (!properties.enabled() || documentId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // Wait until commit is fully completed; the loader and writer then use their own transactions.
                    if (status == STATUS_COMMITTED) indexSafely(documentId, organizationId);
                }
            });
        } else {
            // Reconciliation and other background callers have no owning transaction and can index immediately.
            indexSafely(documentId, organizationId);
        }
    }

    private void indexSafely(Long documentId, String organizationId) {
        try {
            indexService.index(documentId, organizationId);
        } catch (RuntimeException exception) {
            // Log only opaque identifiers, the bounded operation stage, and exception class names. Exception/provider
            // messages may echo personally identifiable information (PII) from CV chunks sent for embedding.
            CvSemanticIndexingStage stage = exception instanceof CvSemanticIndexingException indexingException
                    ? indexingException.stage() : null;
            log.error("CV semantic indexing failed for organizationId={} documentId={} stage={} failureType={} "
                            + "rootCauseType={} sqlState={}", organizationId, documentId, stage,
                    exception.getClass().getSimpleName(), rootCauseType(exception), sqlState(exception));
        }
    }

    private String rootCauseType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName();
    }

    /**
     * Extracts only PostgreSQL's standardized five-character failure category. Unlike an exception message or SQL
     * parameter dump, the SQL state cannot contain CV evidence, embedding values, or other personally identifiable
     * information (PII).
     */
    private String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) return sqlException.getSQLState();
            current = current.getCause();
        }
        return null;
    }
}
