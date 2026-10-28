package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvSemanticIndexServiceTest {
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CvSemanticSourceLoader sourceLoader = mock(CvSemanticSourceLoader.class);
    private final CvSemanticChunker chunker = mock(CvSemanticChunker.class);
    private final CvSemanticIndexRepository repository = mock(CvSemanticIndexRepository.class);
    private final CvSemanticIndexProperties properties = new CvSemanticIndexProperties(
            true, 1200, 200, 100, 2, 50, "0 11 * * * *");
    private final CvSemanticIndexService service = new CvSemanticIndexService(
            embeddingModel, sourceLoader, chunker, repository, properties);

    @Test
    void embedsInBoundedBatchesAndAtomicallyReplacesTheDocumentIndex() {
        CvSemanticSource source = source();
        List<CvSemanticChunk> chunks = List.of(
                new CvSemanticChunk(0, 1, "first"), new CvSemanticChunk(1, 1, "second"),
                new CvSemanticChunk(2, 2, "third"));
        float[] first = vector();
        float[] second = vector();
        float[] third = vector();
        when(sourceLoader.load(9L, "tenant-a")).thenReturn(Optional.of(source));
        when(chunker.chunk(source)).thenReturn(chunks);
        when(embeddingModel.embed(List.of("first", "second"))).thenReturn(List.of(first, second));
        when(embeddingModel.embed(List.of("third"))).thenReturn(List.of(third));

        service.index(9L, "tenant-a");

        verify(embeddingModel).embed(List.of("first", "second"));
        verify(embeddingModel).embed(List.of("third"));
        verify(repository).replace(source, chunks, List.of(first, second, third));
    }

    @Test
    void ignoresDocumentsThatAreMissingOrNoLongerEligible() {
        when(sourceLoader.load(9L, "tenant-a")).thenReturn(Optional.empty());

        service.index(9L, "tenant-a");

        verify(embeddingModel, never()).embed(anyList());
        verify(repository, never()).replace(org.mockito.ArgumentMatchers.any(), anyList(), anyList());
    }

    @Test
    void rejectsAProviderVectorThatCannotFitTheFlywaySchema() {
        CvSemanticSource source = source();
        List<CvSemanticChunk> chunks = List.of(new CvSemanticChunk(0, null, "evidence"));
        when(sourceLoader.load(9L, "tenant-a")).thenReturn(Optional.of(source));
        when(chunker.chunk(source)).thenReturn(chunks);
        when(embeddingModel.embed(List.of("evidence"))).thenReturn(List.of(new float[12]));

        assertThatThrownBy(() -> service.index(9L, "tenant-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dimensions");
        verify(repository, never()).replace(org.mockito.ArgumentMatchers.any(), anyList(), anyList());
    }

    private CvSemanticSource source() {
        return new CvSemanticSource(7L, 9L, "tenant-a", Instant.parse("2026-10-28T00:00:00Z"),
                List.of(new CvSemanticTextUnit(null, "source")));
    }

    private float[] vector() {
        return new float[CvSemanticIndexService.EMBEDDING_DIMENSIONS];
    }
}
