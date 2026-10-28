package com.nevgiu.hrai.candidate.semantic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Timestamp;
import java.util.List;

/** Uses explicit tenant predicates for every vector mutation and retrieval. */
@Repository
class CvSemanticIndexRepository {
    private static final String INSERT_SQL = """
            INSERT INTO cv_semantic_chunks
              (organization_id, candidate_id, document_id, page_number, chunk_index, content, embedding, source_version)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    CvSemanticIndexRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void replace(CvSemanticSource source, List<CvSemanticChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Every semantic chunk requires exactly one embedding");
        }
        jdbc.update("DELETE FROM cv_semantic_chunks WHERE document_id = ? AND organization_id = ?",
                source.documentId(), source.organizationId());
        if (chunks.isEmpty()) return;
        jdbc.batchUpdate(INSERT_SQL, chunks, chunks.size(), (statement, chunk) -> {
            statement.setString(1, source.organizationId());
            statement.setLong(2, source.candidateId());
            statement.setLong(3, source.documentId());
            if (chunk.pageNumber() == null) statement.setNull(4, java.sql.Types.INTEGER);
            else statement.setInt(4, chunk.pageNumber());
            statement.setInt(5, chunk.index());
            statement.setString(6, chunk.content());
            statement.setString(7, vector(embeddings.get(chunk.index())));
            statement.setTimestamp(8, Timestamp.from(source.sourceVersion()));
        });
    }

    List<CvSemanticMatch> search(String organizationId, float[] queryEmbedding,
                                 List<Long> candidateIds, int limit) {
        String candidateFilter = candidateIds == null || candidateIds.isEmpty()
                ? "" : " AND candidate_id IN (:candidateIds)";
        String sql = """
                SELECT candidate_id, document_id, page_number, content,
                       1 - (embedding <=> CAST(:embedding AS vector)) AS similarity, source_version
                FROM cv_semantic_chunks
                WHERE organization_id = :organizationId
                """ + candidateFilter + """
                ORDER BY embedding <=> CAST(:embedding AS vector), id
                LIMIT :limit
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("embedding", vector(queryEmbedding))
                .addValue("limit", limit);
        if (!candidateFilter.isEmpty()) parameters.addValue("candidateIds", candidateIds);
        return namedJdbc.query(sql, parameters, (result, row) -> new CvSemanticMatch(
                result.getLong("candidate_id"), result.getLong("document_id"),
                (Integer) result.getObject("page_number"), result.getString("content"),
                result.getDouble("similarity"), result.getTimestamp("source_version").toInstant()));
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
                      AND chunk.source_version = COALESCE(document.reprocessed_at, document.imported_at)
                  )
                ORDER BY COALESCE(document.reprocessed_at, document.imported_at), document.id
                LIMIT ?
                """, (result, row) -> new CvSemanticIndexTarget(
                result.getLong("document_id"), result.getString("organization_id")), limit);
    }

    private String vector(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) result.append(',');
            result.append(values[index]);
        }
        return result.append(']').toString();
    }
}
