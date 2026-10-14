package com.nevgiu.hrai.candidate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "app.cv-retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CvRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(CvRetentionScheduler.class);
    private final CvDocumentRepository documents;
    private final CvRetentionService retention;
    private final CvRetentionProperties properties;

    public CvRetentionScheduler(CvDocumentRepository documents, CvRetentionService retention,
                                CvRetentionProperties properties) {
        this.documents = documents;
        this.retention = retention;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.cv-retention.cleanup-cron:0 43 3 * * *}")
    public void expireOriginals() {
        var ids = documents.findExpiredOriginalIds(Instant.now(), PageRequest.of(0, properties.batchSize()));
        for (Long id : ids) {
            try {
                retention.expire(id);
            } catch (RuntimeException exception) {
                // One unavailable object must not prevent the remaining bounded batch from being evaluated.
                log.error("Unable to expire original CV for documentId={}", id);
            }
        }
    }
}
