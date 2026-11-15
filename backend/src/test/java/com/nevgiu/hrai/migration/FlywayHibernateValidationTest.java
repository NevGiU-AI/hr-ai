package com.nevgiu.hrai.migration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves that the schema owned by Flyway is compatible with every JPA/Hibernate entity mapping.
 *
 * <p>The assertion is performed while Spring creates this test's application context: Flyway first migrates the real
 * PostgreSQL/pgvector container, then Hibernate starts with {@code ddl-auto=validate}. A missing table or column, an
 * incompatible type, or another supported mapping mismatch prevents context creation and fails the test before its
 * method body is entered. Hibernate never creates or alters the schema in this test.</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FlywayHibernateValidationTest {

    // Plain postgres:16 does not ship the vector extension files required by V6.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void flywaySchemaMatchesHibernateMappings() {
        // Reaching this empty body is the assertion: Flyway migration and Hibernate validation both completed during
        // Spring context startup. A trivial assertTrue would add no validation beyond that successful initialization.
    }
}
