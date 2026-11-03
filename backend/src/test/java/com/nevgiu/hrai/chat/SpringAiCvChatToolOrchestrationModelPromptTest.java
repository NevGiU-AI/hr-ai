package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatToolOrchestrationModelPromptTest {

    @Test
    void treatsMessagesAndToolResultsAsUntrustedAndKeepsScopePlanningSeparateFromEvidenceClaims() {
        assertThat(SpringAiCvChatToolOrchestrationModel.SYSTEM_PROMPT)
                .contains("untrusted data")
                .contains("explicit human confirmation")
                .contains("never choose among ambiguous cards")
                .contains("Do not claim CV skills, experience, education, or suitability")
                .contains("Do not request, infer, or disclose protected or sensitive personal characteristics")
                .contains("grounded evidence and citations are handled by separate application services");
    }
}
