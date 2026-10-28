package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvSemanticSearchServiceTest {
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CvSemanticIndexRepository repository = mock(CvSemanticIndexRepository.class);
    private final CvSemanticSearchService service = new CvSemanticSearchService(embeddingModel, repository);

    @Test
    void alwaysPassesTheAuthenticatedOrganizationAndOptionalCandidateFilterToStorage() {
        float[] vector = new float[CvSemanticIndexService.EMBEDDING_DIMENSIONS];
        List<CvSemanticMatch> matches = List.of(
                new CvSemanticMatch(7L, 9L, 2, "evidence", .91, Instant.parse("2026-10-28T00:00:00Z")));
        when(embeddingModel.embed("Java skills")).thenReturn(vector);
        when(repository.search("tenant-a", vector, List.of(7L), 5)).thenReturn(matches);

        assertThat(service.search("tenant-a", "  Java skills  ", List.of(7L), 5)).isEqualTo(matches);
        verify(repository).search("tenant-a", vector, List.of(7L), 5);
    }

    @Test
    void rejectsRetrievalWithoutATenantBoundary() {
        assertThatThrownBy(() -> service.search(" ", "Java", List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization");
    }
}
