package com.nevgiu.hrai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/** Protects configuration paths whose YAML hierarchy determines whether Spring Boot binds an integration. */
class ApplicationConfigurationTest {

    @Test
    void bindsPgVectorToTheFlywayManagedSemanticTable() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        PropertySourcesPropertyResolver properties = new PropertySourcesPropertyResolver(sources);

        assertThat(properties.getProperty("spring.ai.vectorstore.pgvector.table-name"))
                .isEqualTo("cv_semantic_chunks");
        // This was the incorrect nesting that made Spring AI fall back to its `vector_store` default in staging.
        assertThat(properties.getProperty("spring.ai.openai.vectorstore.pgvector.table-name")).isNull();
        // Protect the environment-independent fallback selected through reviewed staging retrieval questions.
        assertThat(properties.getProperty("app.cv-chat.semantic-min-similarity")).isEqualTo("0.35");
    }
}
