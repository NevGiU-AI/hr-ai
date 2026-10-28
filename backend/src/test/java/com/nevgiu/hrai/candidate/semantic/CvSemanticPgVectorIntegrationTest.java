package com.nevgiu.hrai.candidate.semantic;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises Spring AI against the exact pgvector and Flyway schema used by deployed environments. */
@Testcontainers(disabledWithoutDocker = true)
class CvSemanticPgVectorIntegrationTest {
    private static final int DIMENSIONS = 1536;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private JdbcTemplate jdbc;
    private PgVectorStore vectorStore;
    private CvSemanticIndexRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void prepareDatabase() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        vectorStore = PgVectorStore.builder(jdbc, new DeterministicEmbeddingModel())
                .vectorTableName("cv_semantic_chunks")
                .dimensions(DIMENSIONS)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(true)
                .build();
        vectorStore.afterPropertiesSet();
        repository = new CvSemanticIndexRepository(vectorStore, jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        seedDocument(101, 201, "tenant-a", "2026-10-28T00:00:00Z");
        seedDocument(102, 202, "tenant-b", "2026-10-28T00:00:00Z");
    }

    @Test
    void identifiesWhetherTheRealSpringAiFailureOccursDuringInsertionOrFilterDeletion() {
        Document document = vectorDocument(101, 201, "tenant-a", 0, "Java evidence",
                "2026-10-28T00:00:00Z");

        // Separate assertions deliberately make CI name the failing Spring AI stage instead of reporting only the
        // outer best-effort indexing exception seen in staging.
        assertThatCode(() -> vectorStore.add(List.of(document))).as("PgVectorStore insertion").doesNotThrowAnyException();
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        var scope = filters.and(filters.eq("organizationId", "tenant-a"), filters.eq("documentId", "201")).build();
        assertThatCode(() -> vectorStore.delete(scope)).as("PgVectorStore filter deletion").doesNotThrowAnyException();

        assertThat(chunkCount(201)).isZero();
    }

    @Test
    void atomicallyReplacesChunksAndRollsBackToThePreviousIndexOnFailure() {
        CvSemanticSource source = source(101, 201, "tenant-a", "2026-10-28T00:00:00Z");
        transactions.executeWithoutResult(status -> repository.replace(source,
                List.of(new CvSemanticChunk(0, null, "old evidence"))));

        assertThat(content(201)).containsExactly("old evidence");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            repository.replace(source, List.of(new CvSemanticChunk(0, null, "replacement evidence")));
            throw new IllegalStateException("force rollback after replacement");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(content(201)).containsExactly("old evidence");
        transactions.executeWithoutResult(status -> repository.replace(source,
                List.of(new CvSemanticChunk(0, null, "replacement evidence"))));
        assertThat(content(201)).containsExactly("replacement evidence");
    }

    @Test
    void enforcesTenantSearchIsolationAndDatabaseDeletionCascades() {
        transactions.executeWithoutResult(status -> {
            repository.replace(source(101, 201, "tenant-a", "2026-10-28T00:00:00Z"),
                    List.of(new CvSemanticChunk(0, 1, "tenant A evidence")));
            repository.replace(source(102, 202, "tenant-b", "2026-10-28T00:00:00Z"),
                    List.of(new CvSemanticChunk(0, 1, "tenant B evidence")));
        });

        List<CvSemanticMatch> matches = repository.search("tenant-a", "evidence", List.of(), 10);
        assertThat(matches).extracting(CvSemanticMatch::candidateId).containsExactly(101L);
        assertThat(matches).extracting(CvSemanticMatch::documentId).doesNotContain(202L);

        jdbc.update("DELETE FROM cv_documents WHERE id = ? AND organization_id = ?", 201L, "tenant-a");
        assertThat(chunkCount(201)).isZero();
        assertThat(chunkCount(202)).isEqualTo(1);
    }

    @Test
    void reconciliationSelectsMissingAndStaleDocumentsButNotTheCurrentIndex() {
        assertThat(repository.findPending(10)).contains(
                new CvSemanticIndexTarget(201L, "tenant-a"), new CvSemanticIndexTarget(202L, "tenant-b"));

        transactions.executeWithoutResult(status -> repository.replace(
                source(101, 201, "tenant-a", "2026-10-28T00:00:00Z"),
                List.of(new CvSemanticChunk(0, null, "current evidence"))));
        assertThat(repository.findPending(10)).doesNotContain(new CvSemanticIndexTarget(201L, "tenant-a"));

        jdbc.update("UPDATE cv_documents SET reprocessed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-10-28T01:00:00Z")), 201L);
        assertThat(repository.findPending(10)).contains(new CvSemanticIndexTarget(201L, "tenant-a"));
    }

    private void seedDocument(long candidateId, long documentId, String organizationId, String importedAt) {
        jdbc.update("INSERT INTO candidates (id, organization_id, name) VALUES (?, ?, ?)",
                candidateId, organizationId, "Integration candidate");
        jdbc.update("""
                INSERT INTO cv_documents
                  (id, organization_id, candidate_id, original_filename, content_type, file_size, sha256,
                   source, status, text_length, imported_at)
                VALUES (?, ?, ?, ?, 'application/pdf', 1, ?, 'USER_UPLOAD', 'IMPORTED', 10, ?)
                """, documentId, organizationId, candidateId, "integration.pdf",
                "%064d".formatted(documentId), java.sql.Timestamp.from(Instant.parse(importedAt)));
    }

    private CvSemanticSource source(long candidateId, long documentId, String organizationId, String version) {
        return new CvSemanticSource(candidateId, documentId, organizationId, Instant.parse(version),
                List.of(new CvSemanticTextUnit(null, "evidence")));
    }

    private Document vectorDocument(long candidateId, long documentId, String organizationId, int chunkIndex,
                                    String content, String version) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("organizationId", organizationId);
        metadata.put("candidateId", String.valueOf(candidateId));
        metadata.put("documentId", String.valueOf(documentId));
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("sourceVersion", version);
        String identity = organizationId + ":" + documentId + ":" + chunkIndex;
        return new Document(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(),
                content, metadata);
    }

    private long chunkCount(long documentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cv_semantic_chunks WHERE document_id = ?", Long.class,
                documentId);
    }

    private List<String> content(long documentId) {
        return jdbc.queryForList("SELECT content FROM cv_semantic_chunks WHERE document_id = ? ORDER BY chunk_index",
                String.class, documentId);
    }

    /** Avoids external embedding calls while preserving PgVectorStore's production 1,536-dimension contract. */
    private static final class DeterministicEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < request.getInstructions().size(); index++) {
                embeddings.add(new Embedding(vector(request.getInstructions().get(index)), index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private float[] vector(String text) {
            float[] vector = new float[DIMENSIONS];
            // Two stable non-zero coordinates are sufficient for deterministic cosine-distance integration tests.
            vector[0] = 1.0f;
            vector[1] = Math.floorMod(text.hashCode(), 997) / 997.0f;
            return vector;
        }
    }
}
