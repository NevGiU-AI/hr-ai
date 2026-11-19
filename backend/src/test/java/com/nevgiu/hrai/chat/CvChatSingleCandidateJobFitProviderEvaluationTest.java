package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Paid opt-in regression for the broad single-candidate job-fit wording observed in staging. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatSingleCandidateJobFitProviderEvaluationTest {

    @Test
    void givesAQualifiedCitedAssessmentForBroadSuitability() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        OpenAiChatModel provider = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(modelName)
                        .temperature(0.0)
                        .build())
                .build();
        SpringAiCvChatModel model = new SpringAiCvChatModel(ChatClient.builder(provider).build());
        CvChatComparisonJobContext job = new CvChatComparisonJobContext(
                16L, "AI Java Developer", "Senior",
                "Senior Java role using Java 21, Spring AI, Spring Boot, and Angular.",
                "Design scalable Java 21 and Spring applications; collaborate with cross-functional teams.",
                "Proven Java 21 and Spring Boot experience; strong Angular knowledge.",
                "Spring AI experience.",
                "Problem solving, communication, and teamwork.");
        List<CvChatEvidence> evidence = List.of(new CvChatEvidence(
                "CV-69", 70L, 69L, null, "Fanon Jupkwo Java Software Engineer EN",
                "Built an HR application using Java 21, Spring Boot 3.5, Spring AI, and Angular 19. "
                        + "Collaborated with six software engineers."));

        String answer = model.answer("Is the candidate adequate for this job?", evidence, job, List.of());

        assertThat(answer)
                .as("model=%s", modelName)
                .contains("[CV-69]")
                .isNotEqualTo(CvChatModel.INSUFFICIENT_EVIDENCE)
                .isNotEqualTo(CvChatModel.OUT_OF_SCOPE)
                .doesNotContainIgnoringCase("definitely hire")
                .doesNotContainIgnoringCase("possesses teamwork")
                .doesNotContainIgnoringCase("they");
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }
}
