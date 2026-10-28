package com.nevgiu.hrai.candidate.semantic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adapts domain chunks to Spring AI PgVectorStore documents with mandatory tenant filters. */
@Repository
class CvSemanticIndexRepository {
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbc;

    CvSemanticIndexRepository(VectorStore vectorStore, JdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void replace(CvSemanticSource source, List<CvSemanticChunk> chunks) {
        // Delete and add share one database transaction. A failed embedding call or insert therefore
        // restores the previous index instead of leaving the document only partially searchable.
        Filter.Expression documentScope = documentScope(source.organizationId(), source.documentId());
        vectorStore.delete(documentScope);
        if (chunks.isEmpty()) return;
        vectorStore.add(chunks.stream().map(chunk -> document(source, chunk)).toList());
    }

    List<CvSemanticMatch> search(String organizationId, String query, List<Long> candidateIds, int limit) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op scope = filters.eq("organizationId", organizationId);
        if (candidateIds != null && !candidateIds.isEmpty()) {
            // Use the List<Object> overload deliberately. Passing List<String> selects the varargs overload and
            // produces a nested JSON-filter value that cannot match the scalar candidateId metadata.
            List<Object> candidateFilter = candidateIds.stream()
                    .map(candidateId -> (Object) String.valueOf(candidateId)).toList();
            scope = filters.and(scope, filters.in("candidateId", candidateFilter));
        }
        SearchRequest request = SearchRequest.builder().query(query).topK(limit)
                .similarityThresholdAll().filterExpression(scope.build()).build();
        return vectorStore.similaritySearch(request).stream().map(this::match).toList();
    }

    /** Finds missing or stale indexes without loading any PII-bearing extracted text. */
    List<CvSemanticIndexTarget> findPending(int limit) {
        return jdbc.query("""
                SELECT document.id AS document_id, document.organization_id
                FROM cv_documents document
                WHERE document.status = 'IMPORTED'
                  AND document.candidate_id IS NOT NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM cv_semantic_chunks chunk
                    WHERE chunk.document_id = document.id
                      AND chunk.organization_id = document.organization_id
                      AND chunk.source_version::timestamptz = COALESCE(document.reprocessed_at, document.imported_at)
                  )
                ORDER BY COALESCE(document.reprocessed_at, document.imported_at), document.id
                LIMIT ?
                """, (result, row) -> new CvSemanticIndexTarget(
                result.getLong("document_id"), result.getString("organization_id")), limit);
    }

    private Document document(CvSemanticSource source, CvSemanticChunk chunk) {
        // IDs are deterministic so retries are idempotent. Tenant ownership remains metadata as well,
        // because every Spring AI search/delete must include it as a portable filter expression.
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", source.organizationId());
        metadata.put("candidateId", String.valueOf(source.candidateId()));
        metadata.put("documentId", String.valueOf(source.documentId()));
        if (chunk.pageNumber() != null) metadata.put("pageNumber", chunk.pageNumber());
        metadata.put("chunkIndex", chunk.index());
        metadata.put("sourceVersion", source.sourceVersion().toString());
        String identity = source.organizationId() + ":" + source.documentId() + ":" + chunk.index();
        return new Document(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(),
                chunk.content(), metadata);
    }

    private CvSemanticMatch match(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Object page = metadata.get("pageNumber");
        return new CvSemanticMatch(Long.valueOf(metadata.get("candidateId").toString()),
                Long.valueOf(metadata.get("documentId").toString()),
                page == null ? null : Integer.valueOf(page.toString()), document.getText(),
                document.getScore() == null ? 0 : document.getScore(),
                Instant.parse(metadata.get("sourceVersion").toString()));
    }

    private Filter.Expression documentScope(String organizationId, Long documentId) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        return filters.and(filters.eq("organizationId", organizationId),
                filters.eq("documentId", String.valueOf(documentId))).build();
    }
}
