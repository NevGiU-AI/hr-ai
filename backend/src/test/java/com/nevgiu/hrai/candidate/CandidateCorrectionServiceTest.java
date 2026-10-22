package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateCorrectionServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CandidateMetadataRevisionRepository revisions = mock(CandidateMetadataRevisionRepository.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final CandidateCorrectionService service = new CandidateCorrectionService(candidates, revisions, audit);

    @Test
    void updatesTenantCandidateAndStoresRevisionSnapshot() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setOrganizationId("tenant-a");
        when(candidates.findLockedByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));

        service.update(7L, new UpdateCandidateMetadataRequest(
                " Ada Lovelace ", " ada@example.com ", " London ", " Engineer ", 8), principal());

        assertThat(candidate.getName()).isEqualTo("Ada Lovelace");
        assertThat(candidate.getEmail()).isEqualTo("ada@example.com");
        assertThat(candidate.getYearsExperience()).isEqualTo(8);
        verify(candidates).save(candidate);
        verify(revisions).save(any(CandidateMetadataRevision.class));
    }

    private AppUserPrincipal principal() {
        return new AppUserPrincipal(1L, "admin@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
