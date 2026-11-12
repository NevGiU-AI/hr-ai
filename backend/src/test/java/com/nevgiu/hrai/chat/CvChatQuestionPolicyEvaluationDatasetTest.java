package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatQuestionPolicyEvaluationDatasetTest {

    @Test
    void keepsTheVersionedHumanReviewedDatasetCompleteAndUnambiguous() {
        var dataset = CvChatQuestionPolicyEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.2.0");
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
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("en-follow-up-professional-reference");
            assertThat(testCase.expected()).isEqualTo(CvChatQuestionPolicyResult.JOB_RELEVANT);
        });
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("en-follow-up-current-sensitive");
            assertThat(testCase.expected()).isEqualTo(CvChatQuestionPolicyResult.SENSITIVE);
        });
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.question()).isEqualTo(
                    "Compare Ada Lovelace and Grace Hopper and Alan Turing for this approved job.");
            assertThat(testCase.selectedCandidateComparison()).isTrue();
            assertThat(testCase.approvedJobSelected()).isTrue();
            assertThat(testCase.expected()).isEqualTo(CvChatQuestionPolicyResult.JOB_RELEVANT);
        });
    }
}
