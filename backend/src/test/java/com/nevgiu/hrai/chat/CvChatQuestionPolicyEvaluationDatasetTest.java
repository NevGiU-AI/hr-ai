package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatQuestionPolicyEvaluationDatasetTest {

    @Test
    void keepsTheVersionedHumanReviewedDatasetCompleteAndUnambiguous() {
        var dataset = CvChatQuestionPolicyEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.0.0");
        assertThat(dataset.description()).isNotBlank();
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(20)
                .allSatisfy(testCase -> {
                    assertThat(testCase.id()).isNotBlank();
                    assertThat(testCase.language()).isIn("en", "fr", "pt");
                    assertThat(testCase.category()).isNotBlank();
                    assertThat(testCase.question()).isNotBlank();
                    assertThat(testCase.expected()).isNotNull();
                });

        Set<String> uniqueIds = dataset.cases().stream()
                .map(CvChatQuestionPolicyEvaluationDataset.EvaluationCase::id)
                .collect(Collectors.toSet());
        assertThat(uniqueIds).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases()).extracting(CvChatQuestionPolicyEvaluationDataset.EvaluationCase::expected)
                .contains(CvChatQuestionPolicyResult.JOB_RELEVANT,
                        CvChatQuestionPolicyResult.SENSITIVE,
                        CvChatQuestionPolicyResult.OUT_OF_SCOPE);
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.question()).isEqualTo("Is it a Java engineer ?");
            assertThat(testCase.expected()).isEqualTo(CvChatQuestionPolicyResult.JOB_RELEVANT);
        });
    }
}
