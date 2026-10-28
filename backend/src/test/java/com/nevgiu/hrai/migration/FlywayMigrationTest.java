package com.nevgiu.hrai.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    // Declare the pgvector image as PostgreSQL-compatible so Testcontainers supplies its normal JDBC lifecycle.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Test
    void createsANewSchemaAndSafelyAdoptsAnExistingSchema() throws Exception {
        Flyway cleanDatabase = flyway(false);
        assertThat(cleanDatabase.migrate().migrationsExecuted).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM app_users")).isEqualTo(0);
        assertThat(count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'cv_documents' AND column_name = 'storage_key'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_name = 'candidate_metadata_revisions'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'candidate_evaluations' AND column_name = 'invalidated_at'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_name = 'cv_semantic_chunks'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'"))
                .isEqualTo(1);
        // Insert through Spring AI's native content/metadata/embedding contract; generated columns must enforce tenant
        // ownership without requiring the Java adapter to duplicate relational values.
        execute("INSERT INTO candidates (id, organization_id, name) VALUES (9001, 'tenant-a', 'Index Test')");
        execute("""
                INSERT INTO cv_documents
                  (id, organization_id, candidate_id, original_filename, content_type, file_size, sha256,
                   source, status, text_length, imported_at)
                VALUES
                  (9002, 'tenant-a', 9001, 'index.pdf', 'application/pdf', 1,
                   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                   'USER_UPLOAD', 'IMPORTED', 10, CURRENT_TIMESTAMP)
                """);
        execute("""
                INSERT INTO cv_semantic_chunks
                  (content, metadata, embedding)
                VALUES
                  ('tenant evidence',
                   '{"organizationId":"tenant-a","candidateId":"9001","documentId":"9002",\
                     "chunkIndex":0,"sourceVersion":"2026-10-28T00:00:00Z"}',
                   CAST(array_fill(0::real, ARRAY[1536]) AS vector))
                """);
        assertThat(count("SELECT COUNT(*) FROM cv_semantic_chunks WHERE organization_id = 'tenant-a'"))
                .isEqualTo(1);
        // Metadata claiming another tenant must fail at the generated composite foreign-key boundary.
        assertThatThrownBy(() -> execute("""
                INSERT INTO cv_semantic_chunks
                  (content, metadata, embedding)
                VALUES
                  ('cross tenant evidence',
                   '{"organizationId":"tenant-b","candidateId":"9001","documentId":"9002",\
                     "chunkIndex":1,"sourceVersion":"2026-10-28T00:00:00Z"}',
                   CAST(array_fill(0::real, ARRAY[1536]) AS vector))
                """))
                .hasMessageContaining("fk_cv_semantic_chunks_candidate_tenant");
        execute("DELETE FROM cv_documents WHERE id = 9002");
        // Candidate/document erasure must remove derived embeddings without a separate provider call.
        assertThat(count("SELECT COUNT(*) FROM cv_semantic_chunks WHERE document_id = 9002")).isZero();

        execute("DROP TABLE flyway_schema_history");
        execute("ALTER TABLE security_audit_events ADD CONSTRAINT security_audit_events_event_type_check "
                + "CHECK (event_type IN ('LOGIN_FAILED'))");
        execute("ALTER TABLE security_audit_events ADD CONSTRAINT security_audit_events_outcome_check "
                + "CHECK (outcome IN ('SUCCESS'))");

        Flyway existingDatabase = flyway(true);
        assertThat(existingDatabase.migrate().migrationsExecuted).isEqualTo(6);
        // Baseline version 0 plus migrations V1-V6 must be recorded after adopting the existing schema.
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success")).isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM pg_constraint "
                + "WHERE conrelid = 'security_audit_events'::regclass AND contype = 'c'"))
                .isEqualTo(0);
    }

    private Flyway flyway(boolean baselineOnMigrate) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load();
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
