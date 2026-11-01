package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCvChatRetrievalQueryNormalizerTest {

    @Test
    void promptPreservesNamedProfessionalTermsAndForbidsAnswerGeneration() {
        assertThat(SpringAiCvChatRetrievalQueryNormalizer.SYSTEM_PROMPT)
                .contains("Preserve explicit employer, project, certification, technology, and product names exactly")
                .contains("Do not answer the question, infer facts, or add CV evidence")
                .contains("Sur quoi a-t-elle travaillé chez Exemple SA ?")
                .doesNotContain("KLx");
    }

    @Test
    void parsesOnlyOneBoundedApplicationPrefixedQuery() {
        assertThat(SpringAiCvChatRetrievalQueryNormalizer.parse(
                "RETRIEVAL_QUERY: professional work and projects at KLx"))
                .isEqualTo("professional work and projects at KLx");

        assertThatThrownBy(() -> SpringAiCvChatRetrievalQueryNormalizer.parse("professional work at KLx"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SpringAiCvChatRetrievalQueryNormalizer.parse("RETRIEVAL_QUERY: first\nsecond"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SpringAiCvChatRetrievalQueryNormalizer.parse("RETRIEVAL_QUERY: "))
                .isInstanceOf(IllegalStateException.class);
    }
}
