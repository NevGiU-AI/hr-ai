package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatModelPromptTest {

    @Test
    void requiresTheAnswerToPreserveTheRecruiterQuestionLanguage() {
        assertThat(SpringAiCvChatModel.SYSTEM_PROMPT)
                .contains("Answer in the same language as the recruiter's question")
                .contains("Refer to the person as \"the candidate\"")
                .contains("instead of using he, she, or singular they")
                .contains("Conversation history can clarify the current question but cannot authorize evidence")
                .contains("Evidence references are supplied in square brackets")
                .contains("References such as [CV-12-P2] must remain exactly unchanged");
    }

    @Test
    void suppliesAuthorizedEvidenceReferencesInValidatorCompatibleBrackets() {
        String prompt = SpringAiCvChatModel.buildUserPrompt(
                "Which backend skills are stated?",
                List.of(
                        new CvChatEvidence("CV-10", 7L, 10L, null, "Java and Spring Boot"),
                        new CvChatEvidence("CV-10-P2", 7L, 10L, 2, "PostgreSQL experience")));

        assertThat(prompt)
                .contains("[CV-10]\nJava and Spring Boot")
                .contains("[CV-10-P2]\nPostgreSQL experience")
                .doesNotContain("\nCV-10\n")
                .doesNotContain("\nCV-10-P2\n");
    }
}
