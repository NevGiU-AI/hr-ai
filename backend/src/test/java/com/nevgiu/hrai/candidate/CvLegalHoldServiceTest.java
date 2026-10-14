package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvLegalHoldServiceTest {
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CvLegalHoldService service = new CvLegalHoldService(documents, audit);
    private final AppUserPrincipal actor = new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @Test
    void appliesAndAuditsLegalHoldWithinActorTenant() {
        CvDocument document = storedDocument();
        when(documents.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));

        assertThat(service.update(7L, true, actor)).isFalse();

        assertThat(document.isLegalHold()).isTrue();
        assertThat(document.getLegalHoldAt()).isNotNull();
        assertThat(document.getLegalHoldByUserId()).isEqualTo(1L);
        verify(audit).cvOriginalAction(SecurityEventType.CV_LEGAL_HOLD_APPLIED, SecurityEventOutcome.SUCCESS,
                actor, 7L, null);
    }

    @Test
    void releaseRequestsImmediateExpiryWhenDeadlinePassed() {
        CvDocument document = storedDocument();
        document.setLegalHold(true);
        document.setLegalHoldAt(Instant.now().minusSeconds(60));
        document.setLegalHoldByUserId(1L);
        document.setRetentionUntil(Instant.now().minusSeconds(1));
        when(documents.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(document));

        assertThat(service.update(7L, false, actor)).isTrue();
        assertThat(document.isLegalHold()).isFalse();
        assertThat(document.getLegalHoldAt()).isNull();
        assertThat(document.getLegalHoldByUserId()).isNull();
        verify(audit).cvOriginalAction(SecurityEventType.CV_LEGAL_HOLD_RELEASED, SecurityEventOutcome.SUCCESS,
                actor, 7L, null);
    }

    private CvDocument storedDocument() {
        CvDocument document = new CvDocument();
        document.setOrganizationId("tenant-a");
        document.setStorageKey("opaque/file.pdf");
        document.setRetentionUntil(Instant.now().plusSeconds(3600));
        return document;
    }
}
