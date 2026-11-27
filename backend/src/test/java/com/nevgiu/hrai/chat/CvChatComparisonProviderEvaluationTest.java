package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in comparison of confirmed candidates using only current synthetic evidence. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatComparisonProviderEvaluationTest {
    private static final Pattern CITATION = Pattern.compile("\\[(CV-[^\\]\\r\\n]+)\\]");

    @Test
    void comparesOnlyConfirmedCandidatesAgainstTheApprovedJob() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when RUN_AI_EVALUATIONS=true");
        }
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        var provider = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(modelName).temperature(0.0).build())
                .build();
        var model = new SpringAiCvChatComparisonModel(ChatClient.builder(provider).build());
        var candidates = List.of(new CandidateIdentity(7L, "Ada", null), new CandidateIdentity(8L, "Grace", null));
        var job = new CvChatComparisonJobContext(12L, "Backend Developer", "Senior", "Build backend services.",
                "", "Java 21 and Spring Boot experience.", "", "");
        var evidence = List.of(
                new CvChatEvidence("CV-7", 7L, 7L, null, "Ada", "Built a payment service using Java 21."),
                new CvChatEvidence("CV-8", 8L, 8L, null, "Grace", "Built APIs using Spring Boot and Java 17."));
        // A mention in chat history is not confirmation and cannot make that candidate eligible for this answer.
        List<Message> history = List.of(new UserMessage("Who else was mentioned?"),
                new AssistantMessage("Morgan was mentioned [CV-99]."));

        String answer = model.compare(
                "For each confirmed candidate, compare what the CV demonstrates about Java 21 and Spring Boot. "
                        + "Distinguish documented experience from gaps in the available evidence.",
                candidates, evidence, job, history);

        assertThat(CITATION.matcher(answer).results().map(match -> match.group(1)).distinct().toList())
                .as("comparison citations, model=%s", modelName)
                .containsExactlyInAnyOrder("CV-7", "CV-8");
        assertThat(answer.toLowerCase(Locale.ROOT)).contains("java 21", "spring boot")
                .doesNotContain("morgan", "cv-99", "should hire", "should reject");
    }
}
