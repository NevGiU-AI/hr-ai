package com.nevgiu.hrai.chat.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatDocumentToolOutcomeTest {
    @Test
    void exposesOnlyTheClosedApplicationKindsToStructuredOutput() {
        var converter = new BeanOutputConverter<>(CvChatDocumentToolOutcome.class);

        CvChatDocumentToolOutcome outcome = converter.convert("{\"kind\":\"DOCUMENT_STATUS_AND_WARNINGS\"}");

        assertThat(outcome.kind()).isEqualTo(CvChatDocumentToolOutcome.Kind.DOCUMENT_STATUS_AND_WARNINGS);
        assertThat(converter.getFormat()).contains("NOT_APPLICABLE", "DOCUMENT_STATUS", "INGESTION_WARNINGS");
    }
}
