package com.nevgiu.hrai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads the versioned, synthetic question-policy evaluation set shared by structural and provider-backed tests. */
final class CvChatQuestionPolicyEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-question-policy-v1.json";

    private CvChatQuestionPolicyEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatQuestionPolicyEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Question-policy evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Question-policy evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationCase> cases) {
    }

    record EvaluationCase(String id, String language, String category, String question,
                          CvChatQuestionPolicyResult expected) {
    }
}
