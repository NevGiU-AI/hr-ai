package com.nevgiu.hrai.chat;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads synthetic organization-discovery fixtures shared by structural and deterministic metric tests. */
final class CvChatCandidateDiscoveryEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-candidate-discovery-v1.json";

    private CvChatCandidateDiscoveryEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatCandidateDiscoveryEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Candidate-discovery evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Candidate-discovery evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationCandidate> candidates,
                   List<EvaluationCase> cases) {
    }

    record EvaluationCandidate(Long id, String organizationId, String name, String currentTitle,
                               Long documentId, String sourceVersion) {
    }

    record EvaluationCase(String id, String organizationId, String query, List<String> retrievalQueries,
                          List<SemanticResult> semanticResults, List<EvaluationMatch> explicitMatches,
                          List<Long> relevantCandidateIds, List<Long> requiredCandidateIds,
                          List<Long> forbiddenCandidateIds, List<Long> expectedCandidateOrder,
                          List<MetricExpectation> expectedMetrics, boolean expectedTruncated) {
    }

    record SemanticResult(String query, List<EvaluationMatch> matches) {
    }

    record EvaluationMatch(Long candidateId, Long documentId, Integer pageNumber, String content,
                           double similarity, String sourceVersion) {
    }

    record MetricExpectation(int k, double recall, double precision) {
    }
}
