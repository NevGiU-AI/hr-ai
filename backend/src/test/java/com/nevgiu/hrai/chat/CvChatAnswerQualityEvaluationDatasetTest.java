package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatAnswerQualityEvaluationDatasetTest {

    @Test
    void keepsAnswerQualityCasesVersionedSyntheticAndReviewable() {
        var dataset = CvChatAnswerQualityEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.0.0");
        assertThat(dataset.description()).contains("Synthetic");
        assertThat(dataset.cases()).hasSize(4).allSatisfy(testCase -> {
            assertThat(testCase.id()).isNotBlank();
            assertThat(testCase.category()).isIn(
                    "GROUNDED_ANSWER", "CITATION_COMPLETENESS", "INSUFFICIENT_EVIDENCE", "REFUSAL");
            assertThat(testCase.language()).isEqualTo("en");
            assertThat(testCase.question()).isNotBlank();
            assertThat(testCase.evidence()).isNotEmpty().allSatisfy(evidence -> {
                assertThat(evidence.reference()).matches("CV-\\d+(?:-P\\d+)?");
                assertThat(evidence.candidateId()).isPositive();
                assertThat(evidence.documentId()).isPositive();
                assertThat(evidence.candidateName()).startsWith("Synthetic Candidate");
                assertThat(evidence.excerpt()).isNotBlank();
            });
            assertThat(testCase.requiredReferences()).allMatch(reference -> testCase.evidence().stream()
                    .anyMatch(evidence -> evidence.reference().equals(reference)));
            assertThat(testCase.forbiddenReferences()).noneMatch(testCase.requiredReferences()::contains);
        });
        Set<String> ids = dataset.cases().stream().map(CvChatAnswerQualityEvaluationDataset.EvaluationCase::id)
                .collect(Collectors.toSet());
        assertThat(ids).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases()).filteredOn(CvChatAnswerQualityEvaluationDataset.EvaluationCase::evaluateWithJudges)
                .hasSize(2).allSatisfy(testCase -> {
                    assertThat(testCase.expectedControl()).isNull();
                    assertThat(testCase.requiredReferences()).isNotEmpty();
                });
        assertThat(dataset.cases()).filteredOn(testCase -> !testCase.evaluateWithJudges())
                .extracting(CvChatAnswerQualityEvaluationDataset.EvaluationCase::expectedControl)
                .containsExactlyInAnyOrder(CvChatModel.INSUFFICIENT_EVIDENCE, CvChatModel.OUT_OF_SCOPE);
    }
}
