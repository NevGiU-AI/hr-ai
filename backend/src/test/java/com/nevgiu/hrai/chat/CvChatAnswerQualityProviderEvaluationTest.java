package com.nevgiu.hrai.chat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Locale;

/** Opt-in provider gate for answer grounding, citation completeness, and controlled outcomes. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatAnswerQualityProviderEvaluationTest {

    @Test
    void preservesGroundingCitationsAndControlledOutcomes() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String answerModelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        String judgeModelName = System.getenv().getOrDefault("AI_EVALUATION_JUDGE_MODEL", answerModelName);
        OpenAiChatModel answerModel = chatModel(apiKey, answerModelName);
        OpenAiChatModel judgeModel = chatModel(apiKey, judgeModelName);
        SpringAiCvChatModel cvChatModel = new SpringAiCvChatModel(ChatClient.builder(answerModel).build());
        RelevancyEvaluator relevancy = new RelevancyEvaluator(ChatClient.builder(judgeModel));
        FactCheckingEvaluator factChecking = FactCheckingEvaluator.builder(ChatClient.builder(judgeModel)).build();
        SoftAssertions results = new SoftAssertions();

        for (var testCase : CvChatAnswerQualityEvaluationDataset.load().cases()) {
            List<CvChatEvidence> evidence = testCase.evidence().stream()
                    .map(CvChatAnswerQualityEvaluationDataset.EvaluationEvidence::toEvidence)
                    .toList();
            long startedAt = System.nanoTime();
            String answer = cvChatModel.answer(testCase.question(), evidence);
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            String description = "case=%s category=%s answerModel=%s judgeModel=%s"
                    .formatted(testCase.id(), testCase.category(), answerModelName, judgeModelName);

            if (testCase.expectedControl() != null) {
                results.assertThat(answer).as(description + " exact controlled outcome")
                        .isEqualTo(testCase.expectedControl());
                System.out.printf(Locale.ROOT, "case=%s category=%s controlled=true durationMs=%d%n",
                        testCase.id(), testCase.category(), durationMs);
                continue;
            }

            results.assertThat(answer).as(description + " usable answer")
                    .isNotEqualTo(CvChatModel.OUT_OF_SCOPE)
                    .isNotEqualTo(CvChatModel.INSUFFICIENT_EVIDENCE);
            results.assertThat(answer).as(description + " required citation completeness")
                    .contains(testCase.requiredReferences().stream().map(reference -> "[" + reference + "]")
                            .toArray(String[]::new));
            results.assertThat(answer).as(description + " forbidden citations")
                    .doesNotContain(testCase.forbiddenReferences().stream().map(reference -> "[" + reference + "]")
                            .toArray(String[]::new));
            results.assertThatCode(() -> CvChatCitations.fromAnswer(answer, evidence))
                    .as(description + " application citation validation")
                    .doesNotThrowAnyException();

            List<Document> context = testCase.evidence().stream().map(item -> Document.builder()
                    .id(item.reference())
                    .text(item.excerpt())
                    .build()).toList();
            EvaluationRequest request = new EvaluationRequest(testCase.question(), context, answer);
            boolean relevant = relevancy.evaluate(request).isPass();
            boolean grounded = factChecking.evaluate(request).isPass();
            results.assertThat(relevant).as(description + " Spring AI relevancy").isTrue();
            results.assertThat(grounded).as(description + " Spring AI fact checking").isTrue();
            System.out.printf(Locale.ROOT,
                    "case=%s category=%s requiredCitations=%d relevant=%s grounded=%s durationMs=%d%n",
                    testCase.id(), testCase.category(), testCase.requiredReferences().size(), relevant, grounded,
                    durationMs);
        }
        results.assertAll();
    }

    private OpenAiChatModel chatModel(String apiKey, String modelName) {
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(modelName)
                        .temperature(0.0)
                        .build())
                .build();
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }
}
