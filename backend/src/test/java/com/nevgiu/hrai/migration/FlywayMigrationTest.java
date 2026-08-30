package com.nevgiu.hrai.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void createsANewSchemaAndSafelyAdoptsAnExistingSchema() throws Exception {
        Flyway cleanDatabase = flyway(false);
        assertThat(cleanDatabase.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM app_users")).isEqualTo(0);

        execute("DROP TABLE flyway_schema_history");
        execute("ALTER TABLE security_audit_events ADD CONSTRAINT security_audit_events_event_type_check "
                + "CHECK (event_type IN ('LOGIN_FAILED'))");
        execute("ALTER TABLE security_audit_events ADD CONSTRAINT security_audit_events_outcome_check "
                + "CHECK (outcome IN ('SUCCESS'))");

        Flyway existingDatabase = flyway(true);
        assertThat(existingDatabase.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success")).isEqualTo(2);
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
