package com.nevgiu.hrai.candidate.semantic;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Builds embeddings outside JPA transactions, then atomically replaces one document's persisted index. */
@Service
class CvSemanticIndexService {
    static final int EMBEDDING_DIMENSIONS = 1536;

    private final EmbeddingModel embeddingModel;
    private final CvSemanticSourceLoader sourceLoader;
    private final CvSemanticChunker chunker;
    private final CvSemanticIndexRepository repository;
    private final CvSemanticIndexProperties properties;

    CvSemanticIndexService(EmbeddingModel embeddingModel, CvSemanticSourceLoader sourceLoader,
                           CvSemanticChunker chunker, CvSemanticIndexRepository repository,
                           CvSemanticIndexProperties properties) {
        this.embeddingModel = embeddingModel;
        this.sourceLoader = sourceLoader;
        this.chunker = chunker;
        this.repository = repository;
        this.properties = properties;
    }

    void index(Long documentId, String organizationId) {
        CvSemanticSource source = sourceLoader.load(documentId, organizationId).orElse(null);
        if (source == null) return;
        List<CvSemanticChunk> chunks = chunker.chunk(source);
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += properties.embeddingBatchSize()) {
            int end = Math.min(start + properties.embeddingBatchSize(), chunks.size());
            List<String> content = chunks.subList(start, end).stream().map(CvSemanticChunk::content).toList();
            embeddings.addAll(embeddingModel.embed(content));
        }
        embeddings.forEach(this::validateDimensions);
        repository.replace(source, chunks, embeddings);
    }

    private void validateDimensions(float[] embedding) {
        if (embedding.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("Embedding dimensions do not match the CV semantic index schema");
        }
    }
}
