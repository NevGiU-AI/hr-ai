package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolOrchestrationOutcomeTest {

    @Test
    void exposesOnlyWorkflowStateAndSafePresentationText() {
        assertThat(CvChatToolOrchestrationOutcome.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("kind", "message")
                .doesNotContain("candidateId", "candidateIds", "organizationId", "tenant", "role");

        BeanOutputConverter<CvChatToolOrchestrationOutcome> converter =
                new BeanOutputConverter<>(CvChatToolOrchestrationOutcome.class);
        assertThat(converter.getFormat())
                .contains("kind", "message")
                .doesNotContain("candidateIds", "organizationId", "authorization");
        assertThat(converter.convert("""
                {"kind":"CLARIFICATION_REQUIRED","message":"Name at least one candidate."}
                """))
                .isEqualTo(new CvChatToolOrchestrationOutcome(
                        CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED,
                        "Name at least one candidate."));
    }
}
