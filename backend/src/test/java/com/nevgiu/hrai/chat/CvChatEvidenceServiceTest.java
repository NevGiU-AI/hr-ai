package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentPage;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

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
    private final CvChatEvidenceService service = new CvChatEvidenceService(candidates, documents, pages,
            new CvChatProperties(10, 1200));

    @Test
    void buildsBoundedNativeEvidenceForTheAuthorizedCandidate() {
        Candidate candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCvText("  Java   and Spring Boot engineer  ");
        when(candidates.findByIdAndOrganizationId(7L, "tenant-a")).thenReturn(Optional.of(candidate));
        when(documents.findAllByCandidateIdAndOrganizationId(7L, "tenant-a")).thenReturn(List.of());

        List<CvChatEvidence> evidence = service.find(7L, "tenant-a");

        assertThat(evidence).singleElement().satisfies(item -> {
            assertThat(item.reference()).isEqualTo("CV-CANDIDATE-7");
            assertThat(item.excerpt()).isEqualTo("Java and Spring Boot engineer");
        });
    }

    @Test
    void hidesMissingAndCrossTenantCandidatesBehindTheSameNotFoundResponse() {
        when(candidates.findByIdAndOrganizationId(7L, "tenant-b")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(7L, "tenant-b"))
                .isInstanceOf(CvChatException.class)
                .hasMessage("Candidate not found")
                .satisfies(error -> assertThat(((CvChatException) error).status().value()).isEqualTo(404));
        verify(documents, never()).findAllByCandidateIdAndOrganizationId(7L, "tenant-b");
        verify(pages, never()).findAllByDocumentIdOrderByPageNumber(org.mockito.ArgumentMatchers.anyLong());
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

        List<CvChatEvidence> evidence = service.find(7L, "tenant-a");

        assertThat(evidence).extracting(CvChatEvidence::reference)
                .containsExactly("CV-12-P1", "CV-12-P2");
        assertThat(evidence.getFirst().excerpt()).isEqualTo("Java experience");
    }

    private CvDocumentPage page(int number, String text) {
        CvDocumentPage page = new CvDocumentPage();
        page.setPageNumber(number);
        page.setExtractedText(text);
        return page;
    }
}
