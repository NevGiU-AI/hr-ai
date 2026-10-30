package com.nevgiu.hrai.chat;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises real multilingual embeddings, generated answers, and Spring AI evaluators against synthetic fixtures.
 *
 * <p>The suite is opt-in because it performs paid external calls and provider availability is not a deterministic pull-
 * request dependency. Failure descriptions expose fixture IDs and aggregate metadata only, never fixture text.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatMultilingualProviderEvaluationTest {
    private static final String EVALUATION_ORGANIZATION = "tenant-a";
    private static final long EVALUATION_CANDIDATE = 60L;
    private static final int ANSWER_REPETITIONS = 2;

    @Test
    void retrievesExpectedTenantEvidenceAndGeneratesRelevantGroundedSameLanguageAnswers() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String chatModelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        String judgeModelName = System.getenv().getOrDefault("AI_EVALUATION_JUDGE_MODEL", chatModelName);
        String embeddingModelName = System.getenv().getOrDefault(
                "AI_EVALUATION_EMBEDDING_MODEL", "text-embedding-3-small");
        double similarityThreshold = Double.parseDouble(System.getenv().getOrDefault(
                "AI_EVALUATION_SIMILARITY_THRESHOLD", "0.35"));
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(api, MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model(embeddingModelName).dimensions(1536).build());
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        OpenAiChatModel answerModel = chatModel(api, chatModelName);
        OpenAiChatModel judgeModel = chatModel(api, judgeModelName);
        SpringAiCvChatModel cvChatModel = new SpringAiCvChatModel(ChatClient.builder(answerModel).build());
        RelevancyEvaluator relevancy = new RelevancyEvaluator(ChatClient.builder(judgeModel));
        FactCheckingEvaluator factChecking = FactCheckingEvaluator.builder(ChatClient.builder(judgeModel)).build();
        var dataset = CvChatMultilingualEvaluationDataset.load();
        vectorStore.add(dataset.documents().stream().map(this::toDocument).toList());
        SoftAssertions results = new SoftAssertions();

        for (var testCase : dataset.cases()) {
            List<Document> retrieved = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(testCase.question())
                    .topK(6)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression("organization_id == 'tenant-a' && candidate_id == 60")
                    .build());
            assertRetrievedEvidence(results, testCase, retrieved, embeddingModelName, similarityThreshold);

            if (!testCase.evaluateAnswer() || retrieved.isEmpty()) {
                continue;
            }
            List<CvChatEvidence> evidence = retrieved.stream().map(this::toEvidence).toList();
            for (int attempt = 1; attempt <= ANSWER_REPETITIONS; attempt++) {
                String answer = cvChatModel.answer(testCase.question(), evidence);
                String description = "case=%s language=%s attempt=%d answerModel=%s judgeModel=%s"
                        .formatted(testCase.id(), testCase.language(), attempt, chatModelName, judgeModelName);

                results.assertThat(answer).as(description + " citation")
                        .contains("[" + testCase.expectedReference() + "]")
                        .doesNotContain("CV-99")
                        .isNotEqualTo(CvChatModel.OUT_OF_SCOPE)
                        .isNotEqualTo(CvChatModel.INSUFFICIENT_EVIDENCE);
                results.assertThat(containsLanguageMarker(answer, testCase.languageMarkers()))
                        .as(description + " response language")
                        .isTrue();

                EvaluationRequest request = new EvaluationRequest(testCase.question(), retrieved, answer);
                results.assertThat(relevancy.evaluate(request).isPass())
                        .as(description + " Spring AI relevancy")
                        .isTrue();
                results.assertThat(factChecking.evaluate(request).isPass())
                        .as(description + " Spring AI fact checking")
                        .isTrue();
            }
        }
        results.assertAll();
    }

    private void assertRetrievedEvidence(SoftAssertions results,
                                         CvChatMultilingualEvaluationDataset.EvaluationCase testCase,
                                         List<Document> retrieved, String embeddingModelName,
                                         double similarityThreshold) {
        String description = "case=%s language=%s embeddingModel=%s threshold=%s"
                .formatted(testCase.id(), testCase.language(), embeddingModelName, similarityThreshold);
        results.assertThat(retrieved).as(description + " expected document")
                .isNotEmpty()
                .anySatisfy(document -> assertThat(document.getMetadata().get("reference"))
                        .isEqualTo(testCase.expectedReference()))
                .allSatisfy(document -> {
                    assertThat(document.getMetadata().get("organization_id")).isEqualTo(EVALUATION_ORGANIZATION);
                    assertThat(((Number) document.getMetadata().get("candidate_id")).longValue())
                            .isEqualTo(EVALUATION_CANDIDATE);
                });
        String combinedEvidence = retrieved.stream().map(Document::getText).reduce("", (left, right) -> left + right);
        results.assertThat(combinedEvidence).as(description + " required evidence")
                .contains(testCase.requiredEvidenceTerms().toArray(String[]::new))
                .doesNotContain("Decoy record");
    }

    private Document toDocument(CvChatMultilingualEvaluationDataset.EvaluationDocument source) {
        return Document.builder()
                .id(source.id())
                .text(source.text())
                .metadata("reference", source.reference())
                .metadata("organization_id", source.organizationId())
                .metadata("candidate_id", source.candidateId())
                .metadata("document_id", source.documentId())
                .build();
    }

    private CvChatEvidence toEvidence(Document document) {
        return new CvChatEvidence((String) document.getMetadata().get("reference"),
                ((Number) document.getMetadata().get("candidate_id")).longValue(),
                ((Number) document.getMetadata().get("document_id")).longValue(), null, document.getText());
    }

    private OpenAiChatModel chatModel(OpenAiApi api, String modelName) {
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).temperature(0.0).build())
                .build();
    }

    private boolean containsLanguageMarker(String answer, List<String> markers) {
        String normalized = " " + answer.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";
        return markers.stream().anyMatch(normalized::contains);
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }
}
