package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Small sequential answer-model baseline, not an HTTP load test or a billing estimate. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatProviderBaselineTest {
    private static final int ATTEMPTS = 3;
    private static final Pattern CITATION = Pattern.compile("\\[(CV-[^\\]\\r\\n]+)\\]");

    @Test
    void recordsRepeatedAnswerQualityLatencyAndTokenUsage() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when RUN_AI_EVALUATIONS=true");
        }
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        var provider = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(modelName).temperature(0.0).build())
                .build();
        var usage = new AtomicReference<Usage>();
        // Observe the real provider response without changing the production answer model or making an extra call.
        ChatModel measuredProvider = prompt -> {
            var response = provider.call(prompt);
            usage.set(response.getMetadata().getUsage());
            return response;
        };
        var model = new SpringAiCvChatModel(ChatClient.builder(measuredProvider).build());
        var evidence = List.of(new CvChatEvidence("CV-61", 60L, 61L, null,
                "At KLx the candidate built the Instant Payments service using Java 17. "
                        + "The internal reporting service used Java 11."));
        var job = new CvChatComparisonJobContext(12L, "Backend Developer", "Senior", "Build Java services.",
                "", "Java 17 experience.", "", "");
        var cases = List.of(
                new BaselineCase("grounded", "Which Java version was used for Instant Payments?", null, List.of()),
                new BaselineCase("fr-follow-up", "Quelle version de Java a été utilisée pour ce projet ?", null,
                        List.of(new UserMessage("Which project did we discuss?"),
                                new AssistantMessage("We discussed Instant Payments [CV-99]."))),
                new BaselineCase("approved-job", "Does the CV document the Java version required by this job?",
                        job, List.of()));
        var failures = new ArrayList<String>();

        for (var testCase : cases) {
            var latencies = new ArrayList<Long>();
            int passed = 0;
            int measuredUsage = 0;
            long promptTokens = 0;
            long completionTokens = 0;
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                usage.set(null);
                long started = System.nanoTime();
                String outcome;
                try {
                    String answer = model.answer(testCase.question(), evidence, testCase.job(), testCase.history());
                    boolean valid = passes(testCase.id(), answer);
                    outcome = valid ? "PASS" : "QUALITY_FAIL";
                    if (valid) passed++;
                } catch (RuntimeException failure) {
                    // Provider error messages may contain request or response data. Report only the exception type.
                    outcome = "ERROR:" + failure.getClass().getSimpleName();
                }
                long duration = (System.nanoTime() - started) / 1_000_000;
                latencies.add(duration);
                Usage reported = usage.get();
                Integer input = reported == null ? null : reported.getPromptTokens();
                Integer output = reported == null ? null : reported.getCompletionTokens();
                if (input != null && output != null && input + output > 0) {
                    measuredUsage++;
                    promptTokens += input;
                    completionTokens += output;
                } else {
                    input = null;
                    output = null;
                }
                System.out.printf(Locale.ROOT,
                        "case=%s attempt=%d outcome=%s durationMs=%d promptTokens=%s completionTokens=%s model=%s%n",
                        testCase.id(), attempt, outcome, duration, input, output, modelName);
                if (!"PASS".equals(outcome)) failures.add(testCase.id() + "#" + attempt + ":" + outcome);
            }
            latencies.sort(Long::compareTo);
            System.out.printf(Locale.ROOT,
                    "baseline case=%s passed=%d/%d medianMs=%d maxMs=%d usageReported=%d/%d "
                            + "promptTokens=%s completionTokens=%s model=%s%n",
                    testCase.id(), passed, ATTEMPTS, latencies.get(ATTEMPTS / 2), latencies.getLast(),
                    measuredUsage, ATTEMPTS, measuredUsage == 0 ? "n/a" : promptTokens,
                    measuredUsage == 0 ? "n/a" : completionTokens, modelName);
        }
        assertThat(failures).as("baseline cases that failed the quality checks or provider call").isEmpty();
    }

    private static boolean passes(String caseId, String answer) {
        if (answer == null || !answer.contains("Java 17") || answer.contains("CV-99")) return false;
        if (!CITATION.matcher(answer).results().map(match -> match.group(1)).distinct().toList()
                .equals(List.of("CV-61"))) return false;
        if (!"fr-follow-up".equals(caseId)) return true;
        String normalized = answer.toLowerCase(Locale.ROOT);
        return !normalized.contains("java 11") && List.of(" le ", " la ", " pour ", "utilis").stream()
                .anyMatch(normalized::contains);
    }

    private record BaselineCase(String id, String question, CvChatComparisonJobContext job, List<Message> history) { }
}
