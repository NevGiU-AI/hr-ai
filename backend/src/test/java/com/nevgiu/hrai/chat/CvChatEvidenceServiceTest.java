package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentPage;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvChatEvidenceServiceTest {
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CvDocumentPageRepository pages = mock(CvDocumentPageRepository.class);
    private final CvSemanticSearchService semanticSearch = mock(CvSemanticSearchService.class);
    private final CvChatEvidenceService service = new CvChatEvidenceService(candidates, documents, pages, semanticSearch,
            new CvChatProperties(10, 1200, false, 6, 0.55));

    @Test
    void buildsBoundedNativeEvidenceForTheAuthorizedCandidate() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCvText("  Java   and Spring Boot engineer  ");
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of());

        List<CvChatEvidence> evidence = service.find(7L, "Java", "tenant-a");

        assertThat(evidence).singleElement().satisfies(item -> {
            assertThat(item.reference()).isEqualTo("CV-CANDIDATE-7");
            assertThat(item.excerpt()).isEqualTo("Java and Spring Boot engineer");
        });
    }

    @Test
    void hidesMissingAndCrossTenantCandidatesBehindTheSameNotFoundResponse() {
        when(candidates.findByIdAndOrganizationId(7L, "tenant-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(7L, "Java", "tenant-b"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(404));
        verify(documents, never()).findAllByCandidateIdAndOrganizationId(7L, "tenant-b");
        verify(pages, never()).findAllByDocumentIdOrderByPageNumber(org.mockito.ArgumentMatchers.anyLong());
        verify(semanticSearch, never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void usesBoundedOcrPageEvidenceWithReviewerVisibleReferences() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCvText("document fallback must not be used");
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(12L);
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(document));

        CvDocumentPage first = page(1, " Java   experience ");
        CvDocumentPage second = page(2, "Spring Boot experience");
        when(pages.findAllByDocumentIdOrderByPageNumber(12L)).thenReturn(List.of(first, second));

        List<CvChatEvidence> evidence = service.find(7L, "Java", "tenant-a");

        assertThat(evidence).extracting(CvChatEvidence::reference)
                .containsExactly("CV-12-P1", "CV-12-P2");
        assertThat(evidence.getFirst().excerpt()).isEqualTo("Java experience");
    }

    @Test
    void retrievesOnlyRelevantCurrentDocumentChunksInsideTheAuthorizedTenantAndCandidate() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        CvDocument current = mock(CvDocument.class);
        when(current.getId()).thenReturn(12L);
        when(current.getImportedAt()).thenReturn(Instant.parse("2026-10-28T00:00:00Z"));
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(current));
        when(semanticSearch.search("tenant-a", "Java experience", List.of(7L), 6)).thenReturn(List.of(
                match(7L, 12L, 2, " Java   and Spring Boot ", 0.91),
                match(7L, 12L, 2, "PostgreSQL", 0.88),
                match(7L, 12L, 3, "weak result", 0.40),
                new CvSemanticMatch(7L, 12L, 4, "stale evidence", 0.99,
                        Instant.parse("2026-10-27T00:00:00Z")),
                match(7L, 11L, 1, "obsolete document", 0.95),
                match(8L, 12L, 1, "wrong candidate", 0.99)));
        CvChatEvidenceService semanticService = new CvChatEvidenceService(candidates, documents, pages, semanticSearch,
                new CvChatProperties(10, 1200, true, 6, 0.55));

        List<CvChatEvidence> evidence = semanticService.find(7L, "Java experience", "tenant-a");

        assertThat(evidence).containsExactly(new CvChatEvidence(
                "CV-12-P2", 7L, 12L, 2, "Java and Spring Boot PostgreSQL"));
        verify(semanticSearch).search("tenant-a", "Java experience", List.of(7L), 6);
        verify(pages, never()).findAllByDocumentIdOrderByPageNumber(12L);
    }

    @Test
    void returnsNoEvidenceWhenSemanticMatchesAreBelowTheReviewedThreshold() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        CvDocument current = mock(CvDocument.class);
        when(current.getId()).thenReturn(12L);
        when(current.getImportedAt()).thenReturn(Instant.parse("2026-10-28T00:00:00Z"));
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(current));
        when(semanticSearch.search("tenant-a", "Kubernetes", List.of(7L), 6))
                .thenReturn(List.of(match(7L, 12L, null, "unrelated", 0.20)));
        CvChatEvidenceService semanticService = new CvChatEvidenceService(candidates, documents, pages, semanticSearch,
                new CvChatProperties(10, 1200, true, 6, 0.55));

        assertThat(semanticService.find(7L, "Kubernetes", "tenant-a")).isEmpty();
    }

    @Test
    void hidesSemanticProviderAndStorageFailuresBehindAStableApiError() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        CvDocument current = mock(CvDocument.class);
        when(current.getId()).thenReturn(12L);
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of(current));
        when(semanticSearch.search("tenant-a", "Java", List.of(7L), 6))
                .thenThrow(new IllegalStateException("sensitive provider or database detail"));
        CvChatEvidenceService semanticService = new CvChatEvidenceService(candidates, documents, pages, semanticSearch,
                new CvChatProperties(10, 1200, true, 6, 0.55));

        assertThatThrownBy(() -> semanticService.find(7L, "Java", "tenant-a"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV semantic retrieval is unavailable")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(502));
    }

    private CvSemanticMatch match(Long candidateId, Long documentId, Integer pageNumber, String content,
                                  double similarity) {
        return new CvSemanticMatch(candidateId, documentId, pageNumber, content, similarity,
                Instant.parse("2026-10-28T00:00:00Z"));
    }

    private CvDocumentPage page(int number, String text) {
        CvDocumentPage page = new CvDocumentPage();
        page.setPageNumber(number);
        page.setExtractedText(text);
        return page;
    }
}
