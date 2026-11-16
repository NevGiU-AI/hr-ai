package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvChatOrganizationDiscoveryServiceTest {
    private final CvSemanticSearchService semantic = mock(CvSemanticSearchService.class);
    private final CvChatEvidenceService evidence = mock(CvChatEvidenceService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final CvDocumentRepository documents = mock(CvDocumentRepository.class);
    private final CvChatProperties properties = new CvChatProperties(10, 1200, true, 6, 0.35);
    private final CvChatOrganizationDiscoveryService service = new CvChatOrganizationDiscoveryService(
            semantic, evidence, properties, candidates, documents);

    @Test
    void returnsOnlyCurrentTenantCandidatesOrderedByStrongestEvidence() {
        Instant sourceVersion = Instant.parse("2026-11-12T00:00:00Z");
        when(evidence.prepareRetrievalQueries("Find AI developers", "tenant-a"))
                .thenReturn(List.of("Find AI developers"));
        when(semantic.search("tenant-a", "Find AI developers", List.of(), 50)).thenReturn(List.of(
                new CvSemanticMatch(8L, 18L, null, "AI evidence", 0.82, sourceVersion),
                new CvSemanticMatch(7L, 17L, 2, "ML evidence", 0.74, sourceVersion),
                new CvSemanticMatch(9L, 19L, null, "stale evidence", 0.90, sourceVersion.minusSeconds(1))));
        candidate(8L, "Grace", "AI Engineer");
        candidate(7L, "Ada", "Engineer");
        candidate(9L, "Stale", "Engineer");
        document(8L, 18L, sourceVersion);
        document(7L, 17L, sourceVersion);
        document(9L, 19L, sourceVersion);

        var result = service.discover("Find AI developers", "tenant-a");

        assertThat(result.candidates()).extracting(card -> card.id()).containsExactly(8L, 7L);
        assertThat(result.candidates().getFirst().evidenceReferences()).containsExactly("CV-18");
        assertThat(result.candidates().get(1).evidenceReferences()).containsExactly("CV-17-P2");
        verify(semantic).search("tenant-a", "Find AI developers", List.of(), 50);
    }

    @Test
    void prioritizesExplicitTechnologyEvidenceWithoutLoweringTheGlobalSemanticThreshold() {
        Instant sourceVersion = Instant.parse("2026-11-12T00:00:00Z");
        when(evidence.prepareRetrievalQueries("Find the best Python devs", "tenant-a"))
                .thenReturn(List.of("Find the best Python devs", "Python development skills"));
        when(semantic.search("tenant-a", "Find the best Python devs", List.of(), 50)).thenReturn(List.of(
                // A broad semantic match must not outrank chunks that contain the recruiter's explicit technology.
                new CvSemanticMatch(1L, 11L, null, "Java and Spring backend engineering", 0.79, sourceVersion),
                new CvSemanticMatch(2L, 12L, null, "Built production services with Python", 0.29, sourceVersion)));
        when(semantic.search("tenant-a", "Python development skills", List.of(), 50)).thenReturn(List.of(
                new CvSemanticMatch(3L, 13L, null, "Python machine learning developer", 0.31, sourceVersion)));
        when(semantic.searchExplicitTerms("tenant-a", List.of("python"), 200)).thenReturn(List.of(
                // Candidate 4 was absent from both vector top-K result sets but is present in the indexed corpus.
                new CvSemanticMatch(4L, 14L, null, "Python data platform", 0, sourceVersion)));
        candidate(1L, "Generic semantic match", "Java Engineer");
        candidate(2L, "Python candidate A", "Engineer");
        candidate(3L, "Python candidate B", "Engineer");
        candidate(4L, "Python candidate C", "Engineer");
        document(1L, 11L, sourceVersion);
        document(2L, 12L, sourceVersion);
        document(3L, 13L, sourceVersion);
        document(4L, 14L, sourceVersion);

        var result = service.discover("Find the best Python devs", "tenant-a");

        assertThat(result.candidates()).extracting(card -> card.id())
                .startsWith(3L, 2L, 4L)
                .endsWith(1L);
        assertThat(properties.semanticMinSimilarity()).isEqualTo(0.35);
    }

    private void candidate(long id, String name, String title) {
        Candidate candidate = new Candidate();
        candidate.setId(id);
        candidate.setName(name);
        candidate.setCurrentTitle(title);
        when(candidates.findByIdAndOrganizationId(id, "tenant-a")).thenReturn(Optional.of(candidate));
    }

    private void document(long candidateId, long documentId, Instant importedAt) {
        CvDocument document = mock(CvDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getImportedAt()).thenReturn(importedAt);
        when(documents.findAllByCandidateIdAndOrganizationId(candidateId, "tenant-a"))
                .thenReturn(List.of(document));
    }
}
