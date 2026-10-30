package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatModelPromptTest {

    @Test
    void requiresTheAnswerToPreserveTheRecruiterQuestionLanguage() {
        assertThat(SpringAiCvChatModel.SYSTEM_PROMPT)
                .contains("Answer in the same language as the recruiter's question")
                .contains("References such as [CV-12-P2] must remain exactly unchanged");
    }
}
