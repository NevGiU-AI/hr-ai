package com.nevgiu.hrai.candidate;

import com.nevgiu.hrai.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateDetailsServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CandidateMetadataRevisionRepository revisions = mock(CandidateMetadataRevisionRepository.class);
    private final CvDocumentPageRepository pages = mock(CvDocumentPageRepository.class);
    private final CandidateDetailsService service = new CandidateDetailsService(candidates, documents, revisions, pages);

    @Test
    void returnsSafeDocumentHistoryAndRoleCapabilities() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setName("Ada");
        CvDocument document = new CvDocument();
        document.setCandidate(candidate);
        document.setOriginalFilename("ada.pdf");
        document.setContentType("application/pdf");
        document.setSource(CvDocumentSource.USER_UPLOAD);
        document.setStatus(CvIngestionStatus.IMPORTED);
        document.setStorageKey("opaque/private-key");
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(document));
        when(revisions.findAllByCandidateIdAndOrganizationIdOrderByCreatedAtDescIdDesc(7L, "tenant-a"))
                .thenReturn(List.of());

        CandidateDetailsResponse response = service.find(7L, principal("ROLE_RECRUITER"));

        assertThat(response.documents()).singleElement().satisfies(item -> {
            assertThat(item.originalAvailable()).isTrue();
            assertThat(item.downloadAllowed()).isTrue();
            assertThat(item.administrationAllowed()).isFalse();
        });
    }

    @Test
    void hidesCrossTenantCandidateAsNotFound() {
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(7L, principal("ROLE_ADMIN")))
                .isInstanceOfSatisfying(CvOriginalAccessException.class,
                        exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(candidates).findByIdAndOrganizationId(7L, "tenant-a");
    }

    private AppUserPrincipal principal(String role) {
        return new AppUserPrincipal(1L, "user@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
