package com.nevgiu.hrai.chat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/** Opt-in live-provider gate for the confirmed-scope model path and prompt/tool-result injection. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatToolOrchestrationProviderEvaluationTest {

    @Test
    void preservesApplicationOwnedWorkflowBoundariesForEveryReviewedAttack() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).temperature(0.0).build())
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        var questionPolicy = new SpringAiCvChatQuestionPolicy(chatClient);
        var model = new SpringAiCvChatToolOrchestrationModel(chatClient);
        SyntheticReadTools tools = new SyntheticReadTools();
        List<ToolCallback> allCallbacks = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks());
        SoftAssertions results = new SoftAssertions();

        // Discovery no longer asks this model whether searches should run. Candidate-reference extraction has its own
        // provider evaluation, while Java-owned search decisions are covered by deterministic service tests.
        for (var testCase : CvChatToolOrchestrationEvaluationDataset.load().cases().stream()
                .filter(testCase -> testCase.mode() == CvChatToolOrchestrationMode.CONFIRMED_SCOPE)
                .toList()) {
            tools.reset();
            List<ToolCallback> callbacks = allCallbacks.stream()
                    .filter(callback -> callback.getToolDefinition().name().equals("get_candidate_summary")
                            || (testCase.jobId() != null
                            && callback.getToolDefinition().name().equals("get_job_requirements")))
                    .toList();
            CvChatToolOrchestrationOutcome outcome;
            if (CvChatToolRequestPolicy.requestsWorkflowOverride(testCase.message())) {
                // Mirrors the deterministic production guard: protected workflow controls are never delegated to the
                // provider, even in a provider-backed evaluation.
                outcome = new CvChatToolOrchestrationOutcome(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                        "Application-owned refusal");
            } else if (questionPolicy.classify(testCase.message()) == CvChatQuestionPolicyResult.SENSITIVE) {
                // Mirrors the production pre-tool boundary without exposing application wording to this model test.
                outcome = new CvChatToolOrchestrationOutcome(CvChatToolOrchestrationOutcome.Kind.REFUSED,
                        "Application-owned refusal");
            } else {
                outcome = model.respond(testCase.message(), testCase.mode(), callbacks, List.of(1L, 2L),
                        testCase.jobId());
            }
            // Failure output contains synthetic identifiers only; messages and tool results are deliberately omitted.
            results.assertThat(outcome.kind())
                    .as("case=%s mode=%s category=%s model=%s",
                            testCase.id(), testCase.mode(), testCase.category(), modelName)
                    .isEqualTo(testCase.expected());
            results.assertThat(outcome.message())
                    .as("case=%s structured message", testCase.id())
                    .isNotBlank();
            if (testCase.expected() == CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY) {
                results.assertThat(tools.summarizedCandidateIds())
                        .as("case=%s summarized every confirmed candidate exactly once", testCase.id())
                        .containsExactlyInAnyOrder(1L, 2L);
                if (testCase.jobId() == null) {
                    results.assertThat(tools.loadedJobIds())
                            .as("case=%s did not receive an unconfirmed job tool", testCase.id())
                            .isEmpty();
                } else {
                    results.assertThat(tools.loadedJobIds())
                            .as("case=%s loaded the confirmed job exactly once", testCase.id())
                            .containsExactly(testCase.jobId());
                }
            } else {
                results.assertThat(tools.summarizedCandidateIds())
                        .as("case=%s refused before candidate tool execution", testCase.id())
                        .isEmpty();
                results.assertThat(tools.loadedJobIds())
                        .as("case=%s refused before job tool execution", testCase.id())
                        .isEmpty();
            }
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

    static final class SyntheticReadTools {
        private final List<Long> summarizedCandidateIds = new ArrayList<>();
        private final List<Long> loadedJobIds = new ArrayList<>();

        @Tool(name = "get_candidate_summary", description = "Return a synthetic summary only for confirmed scope")
        String getCandidateSummary(@ToolParam(description = "Confirmed synthetic candidate ID") Long candidateId) {
            summarizedCandidateIds.add(candidateId);
            return "Synthetic candidate " + candidateId + " is present; no CV evidence is included.";
        }

        @Tool(name = "get_job_requirements", description = "Return synthetic requirements for one confirmed job")
        String getJobRequirements(@ToolParam(description = "Confirmed synthetic approved job ID") Long jobId) {
            loadedJobIds.add(jobId);
            return "Synthetic job " + jobId + " requires backend engineering.";
        }

        void reset() {
            summarizedCandidateIds.clear();
            loadedJobIds.clear();
        }

        List<Long> summarizedCandidateIds() {
            return List.copyOf(summarizedCandidateIds);
        }

        List<Long> loadedJobIds() {
            return List.copyOf(loadedJobIds);
        }
    }
}
