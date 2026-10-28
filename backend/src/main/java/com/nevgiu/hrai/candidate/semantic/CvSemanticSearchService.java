package com.nevgiu.hrai.candidate.semantic;

import org.springframework.stereotype.Service;

import java.util.List;

/** Internal tenant-scoped retrieval boundary; no controller is exposed until result policy is reviewed. */
@Service
public class CvSemanticSearchService {
    private final CvSemanticIndexRepository repository;

    CvSemanticSearchService(CvSemanticIndexRepository repository) {
        this.repository = repository;
    }

    /**
     * Searches only inside an authenticated organization, optionally narrowed to already authorized candidate IDs.
     * This internal method intentionally accepts organization context from application code, not model-generated data.
     */
    public List<CvSemanticMatch> search(String organizationId, String query,
                                        List<Long> candidateIds, int limit) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("Organization is required for semantic retrieval");
        }
        if (query == null || query.isBlank()) return List.of();
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Semantic result limit must be between 1 and 50");
        return repository.search(organizationId, query.trim(),
                candidateIds == null ? List.of() : List.copyOf(candidateIds), limit);
    }
}
