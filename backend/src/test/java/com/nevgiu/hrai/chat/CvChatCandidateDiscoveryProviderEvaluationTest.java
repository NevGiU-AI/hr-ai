package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises real normalization and embeddings through the production organization-discovery ranking service. */
@EnabledIfEnvironmentVariable(named = "RUN_AI_EVALUATIONS", matches = "(?i)true")
class CvChatCandidateDiscoveryProviderEvaluationTest {
    private static final int[] OBSERVED_K_VALUES = {3, 5, 10};

    @Test
    void measuresMultilingualProviderRecallPrecisionLatencyAndTenantFiltering() {
        String apiKey = requiredEnvironmentVariable("OPENAI_API_KEY");
        String chatModelName = System.getenv().getOrDefault("AI_EVALUATION_CHAT_MODEL", "gpt-4o-mini");
        String embeddingModelName = System.getenv().getOrDefault(
                "AI_EVALUATION_EMBEDDING_MODEL", "text-embedding-3-small");
        double similarityThreshold = Double.parseDouble(System.getenv().getOrDefault(
                "AI_EVALUATION_SIMILARITY_THRESHOLD", "0.35"));
        var dataset = CvChatCandidateDiscoveryProviderEvaluationDataset.load();

        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .metadataMode(MetadataMode.NONE)
                .options(OpenAiEmbeddingOptions.builder()
                        .apiKey(apiKey)
                        .model(embeddingModelName)
                        .dimensions(1536)
                        .build())
                .build();
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(dataset.documents().stream().map(this::toDocument).toList());
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .model(chatModelName)
                        .temperature(0.0)
                        .build())
                .build();
        SpringAiCvChatRetrievalQueryNormalizer normalizer =
                new SpringAiCvChatRetrievalQueryNormalizer(ChatClient.builder(chatModel).build());

        CvSemanticSearchService semantic = mock(CvSemanticSearchService.class);
        CvChatEvidenceService evidence = mock(CvChatEvidenceService.class);
        CandidateRepository candidates = mock(CandidateRepository.class);
        CvDocumentRepository documents = mock(CvDocumentRepository.class);
        configureSemanticSearch(semantic, vectorStore, dataset.documents());
        configureCurrentCandidates(dataset.documents(), candidates, documents);
        CvChatOrganizationDiscoveryService service = new CvChatOrganizationDiscoveryService(
                semantic, evidence, new CvChatProperties(10, 1200, true, 6, similarityThreshold),
                candidates, documents);

        for (var testCase : dataset.cases()) {
            long started = System.nanoTime();
            String normalized = normalizer.normalize(testCase.query());
            List<String> retrievalQueries = testCase.query().equalsIgnoreCase(normalized.trim())
                    ? List.of(testCase.query())
                    : List.of(testCase.query(), normalized.trim());
            when(evidence.prepareRetrievalQueries(testCase.query(), testCase.organizationId()))
                    .thenReturn(retrievalQueries);

            var response = service.discover(testCase.query(), testCase.organizationId());
            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            List<Long> actualIds = response.candidates().stream().map(card -> card.id()).toList();

            assertThat(actualIds).as(testCase.id() + " required candidates")
                    .containsAll(testCase.requiredCandidateIds());
            assertThat(actualIds).as(testCase.id() + " forbidden candidates")
                    .doesNotContainAnyElementsOf(testCase.forbiddenCandidateIds());
            double recallAt5 = recallAt(actualIds, testCase.relevantCandidateIds(), 5);
            double precisionAt5 = precisionAt(actualIds, testCase.relevantCandidateIds(), 5);
            assertThat(recallAt5).as(testCase.id() + " recall@5")
                    .isGreaterThanOrEqualTo(testCase.minimumRecallAt5());
            assertThat(precisionAt5).as(testCase.id() + " precision@5")
                    .isGreaterThanOrEqualTo(testCase.minimumPrecisionAt5());

            // Emit aggregate synthetic metrics only; never print questions, normalized queries, or document text.
            for (int k : OBSERVED_K_VALUES) {
                System.out.printf(Locale.ROOT,
                        "case=%s language=%s k=%d recall=%.4f precision=%.4f returned=%d "
                                + "queryCount=%d durationMs=%d chatModel=%s embeddingModel=%s%n",
                        testCase.id(), testCase.language(), k,
                        recallAt(actualIds, testCase.relevantCandidateIds(), k),
                        precisionAt(actualIds, testCase.relevantCandidateIds(), k),
                        Math.min(k, actualIds.size()), retrievalQueries.size(), durationMs,
                        chatModelName, embeddingModelName);
            }
        }
    }

    private void configureSemanticSearch(
            CvSemanticSearchService semantic, SimpleVectorStore vectorStore,
            List<CvChatCandidateDiscoveryProviderEvaluationDataset.EvaluationDocument> fixtures) {
        when(semantic.search(anyString(), anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
            String organizationId = invocation.getArgument(0);
            String query = invocation.getArgument(1);
            int limit = invocation.getArgument(3);
            return vectorStore.similaritySearch(SearchRequest.builder()
                            .query(query)
                            .topK(Math.min(limit, fixtures.size()))
                            .similarityThresholdAll()
                            .filterExpression("organization_id == '" + organizationId + "'")
                            .build()).stream()
                    .map(this::toMatch)
                    .toList();
        });
        when(semantic.searchExplicitTerms(anyString(), anyList(), anyInt())).thenAnswer(invocation -> {
            String organizationId = invocation.getArgument(0);
            List<String> terms = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return fixtures.stream()
                    .filter(document -> document.organizationId().equals(organizationId))
                    .filter(document -> CvChatExplicitCriteria.countMatches(document.text(), terms) > 0)
                    .limit(limit)
                    .map(document -> new CvSemanticMatch(document.candidateId(), document.documentId(), null,
                            document.text(), 0.0, Instant.parse(document.sourceVersion())))
                    .toList();
        });
    }

    private void configureCurrentCandidates(
            List<CvChatCandidateDiscoveryProviderEvaluationDataset.EvaluationDocument> fixtures,
            CandidateRepository candidates, CvDocumentRepository documents) {
        fixtures.forEach(fixture -> {
            Candidate candidate = new Candidate();
            candidate.setId(fixture.candidateId());
            candidate.setName(fixture.candidateName());
            candidate.setCurrentTitle(fixture.currentTitle());
            when(candidates.findByIdAndOrganizationId(fixture.candidateId(), fixture.organizationId()))
                    .thenReturn(Optional.of(candidate));

            CvDocument document = mock(CvDocument.class);
            when(document.getId()).thenReturn(fixture.documentId());
            when(document.getImportedAt()).thenReturn(Instant.parse(fixture.sourceVersion()));
            when(documents.findAllByCandidateIdAndOrganizationId(fixture.candidateId(), fixture.organizationId()))
                    .thenReturn(List.of(document));
        });
    }

    private Document toDocument(CvChatCandidateDiscoveryProviderEvaluationDataset.EvaluationDocument source) {
        return Document.builder()
                .id(source.id())
                .text(source.text())
                .metadata("organization_id", source.organizationId())
                .metadata("candidate_id", source.candidateId())
                .metadata("document_id", source.documentId())
                .metadata("source_version", source.sourceVersion())
                .build();
    }

    private CvSemanticMatch toMatch(Document document) {
        return new CvSemanticMatch(
                ((Number) document.getMetadata().get("candidate_id")).longValue(),
                ((Number) document.getMetadata().get("document_id")).longValue(), null,
                document.getText(), document.getScore() == null ? 0.0 : document.getScore(),
                Instant.parse((String) document.getMetadata().get("source_version")));
    }

    private double recallAt(List<Long> actual, List<Long> relevant, int k) {
        Set<Long> topK = new LinkedHashSet<>(actual.subList(0, Math.min(k, actual.size())));
        long hits = relevant.stream().filter(topK::contains).count();
        return relevant.isEmpty() ? 1.0 : (double) hits / relevant.size();
    }

    private double precisionAt(List<Long> actual, List<Long> relevant, int k) {
        List<Long> topK = actual.subList(0, Math.min(k, actual.size()));
        long hits = topK.stream().filter(relevant::contains).count();
        return topK.isEmpty() ? 1.0 : (double) hits / topK.size();
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_AI_EVALUATIONS=true");
        }
        return value;
    }
}
