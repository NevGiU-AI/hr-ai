package com.nevgiu.hrai.candidate.semantic;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvSemanticChunkerTest {

    @Test
    void createsBoundedOverlappingChunksWithoutMixingOcrPages() {
        CvSemanticChunker chunker = new CvSemanticChunker(properties(400, 50, 10));
        String firstPage = "Java Spring PostgreSQL ".repeat(35);
        String secondPage = "Angular accessibility ".repeat(30);
        CvSemanticSource source = source(List.of(
                new CvSemanticTextUnit(1, firstPage), new CvSemanticTextUnit(2, secondPage)));

        List<CvSemanticChunk> chunks = chunker.chunk(source);

        assertThat(chunks).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.content()).doesNotContain("  ");
            assertThat(chunk.content().length()).isLessThanOrEqualTo(400);
        });
        assertThat(chunks).extracting(CvSemanticChunk::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).extracting(CvSemanticChunk::pageNumber).contains(1, 2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content()).doesNotContain("PostgreSQL Angular"));
    }

    @Test
    void enforcesThePerDocumentChunkLimit() {
        CvSemanticChunker chunker = new CvSemanticChunker(properties(400, 50, 2));

        assertThat(chunker.chunk(source(List.of(new CvSemanticTextUnit(null, "evidence ".repeat(500))))))
                .hasSize(2);
    }

    private CvSemanticSource source(List<CvSemanticTextUnit> units) {
        return new CvSemanticSource(7L, 9L, "tenant-a", Instant.parse("2026-10-28T00:00:00Z"), units);
    }

    private CvSemanticIndexProperties properties(int size, int overlap, int maxChunks) {
        return new CvSemanticIndexProperties(true, size, overlap, maxChunks, 10, 50, "0 11 * * * *");
    }
}
