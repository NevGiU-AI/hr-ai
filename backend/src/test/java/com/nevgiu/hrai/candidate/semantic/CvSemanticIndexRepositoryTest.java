package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the Spring AI document contract and mandatory tenant filtering without calling an embedding provider. */
class CvSemanticIndexRepositoryTest {
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CvSemanticIndexRepository repository =
            new CvSemanticIndexRepository(vectorStore, jdbc);

    @Test
    void replacesAStoredDocumentThroughSpringAiWithTenantMetadata() {
        CvSemanticSource source = source();
        List<CvSemanticChunk> chunks = List.of(new CvSemanticChunk(0, 2, "Java evidence"));

        repository.replace(source, chunks);

        var ordered = inOrder(vectorStore);
        // Deleting first and adding second inside one transaction is the repository's atomic replacement contract.
        ordered.verify(vectorStore).delete(any(Filter.Expression.class));
        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        ordered.verify(vectorStore).add(documents.capture());
        Document stored = documents.getValue().getFirst();
        assertThat(stored.getText()).isEqualTo("Java evidence");
        assertThat(stored.getMetadata()).containsEntry("organizationId", "tenant-a")
                .containsEntry("candidateId", "7")
                .containsEntry("documentId", "9")
                .containsEntry("pageNumber", 2)
                .containsEntry("chunkIndex", 0)
                .containsEntry("sourceVersion", "2026-10-28T00:00:00Z");
    }

    @Test
    void alwaysSuppliesTheTenantAndOptionalCandidatesAsSpringAiSearchFilters() {
        Document result = Document.builder().id("6cc6766e-72c6-4e33-968a-6eb82255841d")
                .text("evidence").score(.91)
                .metadata("organizationId", "tenant-a").metadata("candidateId", "7")
                .metadata("documentId", "9").metadata("pageNumber", 2)
                .metadata("sourceVersion", "2026-10-28T00:00:00Z").build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(result));

        List<CvSemanticMatch> matches = repository.search("tenant-a", "Java", List.of(7L), 5);

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        Filter.Expression expected = filters.and(filters.eq("organizationId", "tenant-a"),
                filters.in("candidateId", List.<Object>of("7"))).build();
        assertThat(request.getValue().getFilterExpression()).isEqualTo(expected);
        assertThat(request.getValue().getTopK()).isEqualTo(5);
        assertThat(matches).containsExactly(new CvSemanticMatch(7L, 9L, 2, "evidence", .91,
                Instant.parse("2026-10-28T00:00:00Z")));
    }

    @Test
    void identifiesDeleteAndInsertFailuresWithoutCopyingSensitiveMessages() {
        RuntimeException deleteFailure = new RuntimeException("sensitive delete message");
        doThrow(deleteFailure).when(vectorStore).delete(any(Filter.Expression.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.replace(source(), List.of()))
                .isInstanceOfSatisfying(CvSemanticIndexingException.class, failure -> {
                    assertThat(failure.stage()).isEqualTo(CvSemanticIndexingStage.DELETE_EXISTING);
                    assertThat(failure.getMessage()).doesNotContain("sensitive delete message");
                });

        org.mockito.Mockito.reset(vectorStore);
        RuntimeException insertFailure = new RuntimeException("sensitive embedding message");
        doThrow(insertFailure).when(vectorStore).add(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.replace(source(),
                        List.of(new CvSemanticChunk(0, null, "evidence"))))
                .isInstanceOfSatisfying(CvSemanticIndexingException.class, failure -> {
                    assertThat(failure.stage()).isEqualTo(CvSemanticIndexingStage.INSERT_REPLACEMENT);
                    assertThat(failure.getMessage()).doesNotContain("sensitive embedding message");
                });
    }

    private CvSemanticSource source() {
        return new CvSemanticSource(7L, 9L, "tenant-a", Instant.parse("2026-10-28T00:00:00Z"),
                List.of(new CvSemanticTextUnit(2, "Java evidence")));
    }
}
