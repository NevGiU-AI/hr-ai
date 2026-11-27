package com.nevgiu.hrai.chat;

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

/** Paid opt-in regressions for single-candidate assessments against an approved job. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatSingleCandidateJobFitProviderEvaluationTest {
    private static final Pattern CITATION = Pattern.compile("\\[(CV-[^\\]\\r\\n]+)\\]");

    @Test
    void givesAQualifiedCitedAssessmentForBroadSuitability() {
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        SpringAiCvChatModel model = model(modelName);
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

    @Test
    void distinguishesUnsupportedRequirementsInAnApprovedJobFollowUp() {
        String modelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        var job = new CvChatComparisonJobContext(16L, "Backend Developer", "Senior",
                "Build Java services.",
                "Maintain backend services.",
                "Professional Java 21 experience and production Kubernetes experience.",
                "", "");
        var evidence = List.of(new CvChatEvidence("CV-69", 70L, 69L, null,
                "Developed and maintained a payment service using Java 21."));
        // History supplies the antecedent of “which ones” and deliberately includes an unrelated role and old
        // citation. Only the application-confirmed job and the current evidence can support this answer.
        List<Message> history = List.of(
                new UserMessage("Which requirements of this job are supported?"),
                new AssistantMessage("Java 21 is documented [CV-99]. The unrelated Systems Engineer role "
                        + "requires Rust and is not the approved job."));

        String answer = model(modelName).answer(
                "Which ones are still unsupported? Briefly recap what is supported as well.", evidence, job, history);
        String normalized = answer.toLowerCase(Locale.ROOT);

        assertThat(CITATION.matcher(answer).results().map(match -> match.group(1)).distinct().toList())
                .as("approved-job follow-up citations, model=%s", modelName)
                .containsExactly("CV-69");
        assertThat(normalized).contains("java 21", "kubernetes")
                .doesNotContain("cv-99", "systems engineer", "rust", "should hire", "should reject",
                        "lacks kubernetes", "has no kubernetes", "has never used kubernetes",
                        "does not have kubernetes experience");
        // Check for an evidence limitation, not an exact English sentence. This is a targeted regression,
        // not a general entailment judge for every possible paraphrase.
        assertThat(normalized).containsAnyOf("does not demonstrate", "does not mention", "not documented",
                "not demonstrated", "not supported", "unsupported", "no evidence", "not shown",
                "does not provide evidence", "not evidenced", "not established");
    }

    private SpringAiCvChatModel model(String modelName) {
        var provider = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(requiredEnvironmentVariable("OPENAI_API_KEY"))
                        .model(modelName)
                        .temperature(0.0)
                        .build())
                .build();
        return new SpringAiCvChatModel(ChatClient.builder(provider).build());
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }
}
