package com.nevgiu.hrai.chat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

/** Live-provider gate for the synthetic name-reference cases that deterministic parser tests cannot evaluate. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatCandidateReferenceProviderEvaluationTest {
    @Test
    void extractsHumanReviewedCandidateReferences() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(modelName)
                        .temperature(0.0)
                        .build())
                .build();
        var extractor = new SpringAiCvChatCandidateReferenceExtractor(ChatClient.builder(model).build());
        List<Case> cases = List.of(
                new Case("en-partial-first-names", "Compare Ada and Grace backend engineering experiences",
                        List.of("Ada", "Grace")),
                new Case("en-full-names", "Compare Ada Lovelace and Grace Hopper for this role",
                        List.of("Ada Lovelace", "Grace Hopper")),
                new Case("fr-full-names", "Comparez Ada Lovelace et Grace Hopper pour ce poste",
                        List.of("Ada Lovelace", "Grace Hopper")),
                new Case("en-single-name", "Summarize Ada's backend experience", List.of()));
        SoftAssertions results = new SoftAssertions();

        for (Case testCase : cases) {
            // Failure output identifies only the synthetic case and model; it never prints a production message.
            results.assertThat(extractor.extract(testCase.message()))
                    .as("case=%s model=%s", testCase.id(), modelName)
                    .containsExactlyElementsOf(testCase.expectedReferences());
        }
        results.assertAll();
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }

    private record Case(String id, String message, List<String> expectedReferences) { }
}
