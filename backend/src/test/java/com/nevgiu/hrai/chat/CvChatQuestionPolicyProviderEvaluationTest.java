package com.nevgiu.hrai.chat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Runs the reviewed policy set against a real provider only when explicitly requested.
 *
 * <p>Ordinary pull-request tests remain deterministic and free of provider cost. Run this suite before changing the
 * policy prompt/model and before promoting either one with {@code RUN_AI_EVALUATIONS=true OPENAI_API_KEY=...}.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatQuestionPolicyProviderEvaluationTest {

    @Test
    void classifiesEveryHumanReviewedQuestionAsExpected() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).temperature(0.0).build())
                .build();
        var policy = new SpringAiCvChatQuestionPolicy(ChatClient.builder(model).build());
        var dataset = CvChatQuestionPolicyEvaluationDataset.load();
        SoftAssertions results = new SoftAssertions();

        for (var testCase : dataset.cases()) {
            // Failure output uses the synthetic case ID, not the question, keeping future CI logs content-safe.
            results.assertThat(policy.classify(testCase.question()))
                    .as("case=%s language=%s category=%s model=%s",
                            testCase.id(), testCase.language(), testCase.category(), modelName)
                    .isEqualTo(testCase.expected());
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
}
