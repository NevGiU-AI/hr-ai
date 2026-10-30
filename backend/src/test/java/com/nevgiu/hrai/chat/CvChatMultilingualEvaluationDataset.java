package com.nevgiu.hrai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads synthetic multilingual fixtures shared by deterministic and provider-backed CV-chat evaluation tests. */
final class CvChatMultilingualEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-multilingual-retrieval-v1.json";

    private CvChatMultilingualEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatMultilingualEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Multilingual CV-chat evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Multilingual CV-chat evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationDocument> documents,
                   List<EvaluationCase> cases) {
    }

    record EvaluationDocument(String id, String reference, String organizationId, Long candidateId, Long documentId,
                              String text) {
    }

    record EvaluationCase(String id, String language, String question, String expectedReference,
                          List<String> requiredEvidenceTerms, boolean evaluateAnswer, List<String> languageMarkers) {
    }
}
