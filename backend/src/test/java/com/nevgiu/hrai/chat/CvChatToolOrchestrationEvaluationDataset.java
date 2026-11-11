package com.nevgiu.hrai.chat;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Loads synthetic, human-reviewed tool-orchestration attacks for deterministic and opt-in provider evaluation. */
final class CvChatToolOrchestrationEvaluationDataset {
    static final String RESOURCE = "/ai-evaluations/cv-chat-tool-orchestration-v1.json";

    private CvChatToolOrchestrationEvaluationDataset() {
    }

    static Dataset load() {
        try (InputStream input = CvChatToolOrchestrationEvaluationDataset.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Tool-orchestration evaluation dataset is missing: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Tool-orchestration evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationCase> cases) {
    }

    record EvaluationCase(String id, CvChatToolOrchestrationMode mode, String category, String message,
                          CvChatToolOrchestrationOutcome.Kind expected, Long jobId) {
    }
}
