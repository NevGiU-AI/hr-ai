package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatComparisonModelPromptTest {
    @Test
    void keepsCandidateSelectionAndEvidenceInsideTheApplicationPolicyBoundary() {
        String normalizedPrompt = SpringAiCvChatComparisonModel.SYSTEM_PROMPT.replaceAll("\\s+", " ");

        assertThat(normalizedPrompt)
                .contains("Compare only the explicitly selected candidates")
                .contains("Never introduce, search for, or infer another candidate")
                .contains("cite evidence for every selected candidate")
                .contains(CvChatModel.INSUFFICIENT_EVIDENCE)
                .contains(CvChatModel.OUT_OF_SCOPE);
    }
}
