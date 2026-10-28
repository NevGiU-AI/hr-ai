package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies orchestration between source loading, deterministic chunking, and vector replacement. */
class CvSemanticIndexServiceTest {
    private final CvSemanticSourceLoader sourceLoader = mock(CvSemanticSourceLoader.class);
    private final CvSemanticChunker chunker = mock(CvSemanticChunker.class);
    private final CvSemanticIndexRepository repository = mock(CvSemanticIndexRepository.class);
    private final CvSemanticIndexService service = new CvSemanticIndexService(sourceLoader, chunker, repository);

    @Test
    void delegatesChunkEmbeddingAndAtomicReplacementToSpringAiVectorStorage() {
        CvSemanticSource source = source();
        List<CvSemanticChunk> chunks = List.of(
                new CvSemanticChunk(0, 1, "first"), new CvSemanticChunk(1, 1, "second"),
                new CvSemanticChunk(2, 2, "third"));
        when(sourceLoader.load(9L, "tenant-a")).thenReturn(Optional.of(source));
        when(chunker.chunk(source)).thenReturn(chunks);

        service.index(9L, "tenant-a");

        verify(repository).replace(source, chunks);
    }

    @Test
    void ignoresDocumentsThatAreMissingOrNoLongerEligible() {
        when(sourceLoader.load(9L, "tenant-a")).thenReturn(Optional.empty());

        service.index(9L, "tenant-a");

        verify(repository, never()).replace(org.mockito.ArgumentMatchers.any(), anyList());
    }

    private CvSemanticSource source() {
        return new CvSemanticSource(7L, 9L, "tenant-a", Instant.parse("2026-10-28T00:00:00Z"),
                List.of(new CvSemanticTextUnit(null, "source")));
    }
}
