package com.nevgiu.hrai.candidate.semantic;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/** Internal tenant-scoped retrieval boundary; no controller is exposed until result policy is reviewed. */
@Service
public class CvSemanticSearchService {
    private final EmbeddingModel embeddingModel;
    private final CvSemanticIndexRepository repository;

    CvSemanticSearchService(EmbeddingModel embeddingModel, CvSemanticIndexRepository repository) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
    }

    public List<CvSemanticMatch> search(String organizationId, String query,
                                        List<Long> candidateIds, int limit) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Organization is required for semantic retrieval");
        }
        if (query == null || query.isBlank()) return List.of();
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Semantic result limit must be between 1 and 50");
        float[] embedding = embeddingModel.embed(query.trim());
        if (embedding.length != CvSemanticIndexService.EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("Embedding dimensions do not match the CV semantic index schema");
        }
        return repository.search(organizationId, embedding,
                candidateIds == null ? List.of() : List.copyOf(candidateIds), limit);
    }
}
