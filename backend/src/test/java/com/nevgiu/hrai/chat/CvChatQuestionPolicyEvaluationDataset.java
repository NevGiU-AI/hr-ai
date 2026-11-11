package com.nevgiu.hrai.chat;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
            // Most cases use the safe default workflow (no preselected candidates and no approved job), so the
            // versioned JSON intentionally omits those false flags. Jackson 3 rejects a missing/null JSON value for
            // a primitive by default; disabling that strict check only for this synthetic dataset preserves the
            // established "absent means false" contract without weakening application JSON handling.
            return JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .build()
                    .readValue(input, Dataset.class);
        } catch (IOException invalidDataset) {
            throw new IllegalStateException("Question-policy evaluation dataset cannot be read", invalidDataset);
        }
    }

    record Dataset(String version, String description, List<EvaluationCase> cases) {
    }

    record EvaluationCase(String id, String language, String category, String question,
                          boolean selectedCandidateComparison, boolean approvedJobSelected,
                          CvChatQuestionPolicyResult expected) {
    }
}
