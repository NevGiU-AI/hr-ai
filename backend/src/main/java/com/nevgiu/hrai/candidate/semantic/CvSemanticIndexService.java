package com.nevgiu.hrai.candidate.semantic;

import org.springframework.stereotype.Service;

import java.util.List;

/** Converts one eligible source into Spring AI documents and atomically replaces its vector-store entries. */
@Service
class CvSemanticIndexService {
    private final CvSemanticSourceLoader sourceLoader;
    private final CvSemanticChunker chunker;
    private final CvSemanticIndexRepository repository;

    CvSemanticIndexService(CvSemanticSourceLoader sourceLoader, CvSemanticChunker chunker,
                           CvSemanticIndexRepository repository) {
        this.sourceLoader = sourceLoader;
        this.chunker = chunker;
        this.repository = repository;
    }

    void index(Long documentId, String organizationId) {
        CvSemanticSource source = sourceLoader.load(documentId, organizationId).orElse(null);
        if (source == null) return;
        List<CvSemanticChunk> chunks = chunker.chunk(source);
        repository.replace(source, chunks);
    }
}
