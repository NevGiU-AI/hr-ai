package com.nevgiu.hrai.chat;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads versioned synthetic cases used by the generated-answer quality provider gate. */
final class CvChatAnswerQualityEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-answer-quality-v1.json";

    private CvChatAnswerQualityEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatAnswerQualityEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("CV-chat answer-quality evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("CV-chat answer-quality evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationCase> cases) {
    }

    record EvaluationCase(String id, String category, String language, String question,
                          List<EvaluationEvidence> evidence, List<String> requiredReferences,
                          List<String> forbiddenReferences, String expectedControl, boolean evaluateWithJudges) {
    }

    record EvaluationEvidence(String reference, Long candidateId, Long documentId, String candidateName,
                              String excerpt) {
        CvChatEvidence toEvidence() {
            return new CvChatEvidence(reference, candidateId, documentId, null, candidateName, excerpt);
        }
    }
}
