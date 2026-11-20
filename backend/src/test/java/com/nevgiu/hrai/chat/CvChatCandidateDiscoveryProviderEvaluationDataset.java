package com.nevgiu.hrai.chat;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads synthetic documents and reviewed cases for opt-in provider-backed organization-discovery evaluation. */
final class CvChatCandidateDiscoveryProviderEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-candidate-discovery-provider-v1.json";

    private CvChatCandidateDiscoveryProviderEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatCandidateDiscoveryProviderEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Provider discovery evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Provider discovery evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationDocument> documents,
                   List<EvaluationCase> cases) {
    }

    record EvaluationDocument(String id, String organizationId, Long candidateId, String candidateName,
                              String currentTitle, Long documentId, String sourceVersion, String text) {
    }

    record EvaluationCase(String id, String language, String query, String organizationId,
                          List<Long> relevantCandidateIds, List<Long> requiredCandidateIds,
                          List<Long> forbiddenCandidateIds, double minimumRecallAt5,
                          double minimumPrecisionAt5) {
    }
}
