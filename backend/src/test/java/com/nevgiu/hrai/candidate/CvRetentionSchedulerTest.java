package com.nevgiu.hrai.candidate;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvRetentionSchedulerTest {
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CvRetentionService retention = mock(CvRetentionService.class);
    private final CvRetentionScheduler scheduler = new CvRetentionScheduler(
            documents, retention, new CvRetentionProperties(true, 2));

    @Test
    void processesOnlyTheConfiguredBoundedBatch() {
        when(documents.findExpiredOriginalIds(any(Instant.class), any(PageRequest.class)))
                .thenReturn(List.of(7L, 8L));

        scheduler.expireOriginals();

        verify(documents).findExpiredOriginalIds(any(Instant.class), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 2)));
        verify(retention).expire(7L);
        verify(retention).expire(8L);
    }
}
