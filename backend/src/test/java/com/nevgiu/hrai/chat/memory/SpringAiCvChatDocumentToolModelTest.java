package com.nevgiu.hrai.chat.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatDocumentToolModelTest {
    @Test
    void promptSeparatesOperationalCategoriesFromCvEvidenceAndRequiresExactCalls() {
        assertThat(SpringAiCvChatDocumentToolModel.SYSTEM_PROMPT)
                .contains("persisted AI-assisted evaluation history", "get_evaluation_evidence exactly once",
                        "NOT_APPLICABLE", "Return only the structured kind")
                .contains("autonomous hiring decision", "objective truth")
                .doesNotContain("Return the generated rationale");
    }
}
