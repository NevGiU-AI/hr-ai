package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatFollowUpContextTest {
    @Test
    void addsOnlyBoundedRecentRecruiterQuestions() {
        List<Message> history = List.of(
                new UserMessage("Old question that falls outside the two-question window"),
                new AssistantMessage("CV-derived answer that must not enter policy or retrieval"),
                new UserMessage("Which backend technologies are listed?"),
                new AssistantMessage("Java and Spring [CV-7]."),
                new UserMessage("Which databases are listed?"));

        String context = CvChatFollowUpContext.contextualQuestion(
                "Which of those appears most relevant?", history);

        assertThat(context)
                .startsWith("CURRENT RECRUITER QUESTION (CLASSIFY THIS INTENT):")
                .contains("Which of those appears most relevant?")
                .contains("Which backend technologies are listed?")
                .contains("Which databases are listed?")
                .doesNotContain("Old question")
                .doesNotContain("CV-derived answer")
                .doesNotContain("Java and Spring [CV-7]");
    }

    @Test
    void preservesTheExistingQuestionWhenThereIsNoRecruiterHistory() {
        assertThat(CvChatFollowUpContext.contextualQuestion("Summarize Java experience", List.of()))
                .isEqualTo("Summarize Java experience");
    }
}
