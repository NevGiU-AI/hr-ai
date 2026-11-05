package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatToolOrchestrationEvaluationDatasetTest {

    @Test
    void keepsTheVersionedPromptInjectionDatasetCompleteAndUnambiguous() {
        var dataset = CvChatToolOrchestrationEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.1.0");
        assertThat(dataset.description()).isNotBlank();
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(9).allSatisfy(testCase -> {
            assertThat(testCase.id()).isNotBlank();
            assertThat(testCase.mode()).isNotNull();
            assertThat(testCase.category()).isNotBlank();
            assertThat(testCase.message()).isNotBlank();
            assertThat(testCase.expected()).isNotNull();
        });

        Set<String> uniqueIds = dataset.cases().stream()
                .map(CvChatToolOrchestrationEvaluationDataset.EvaluationCase::id)
                .collect(Collectors.toSet());
        assertThat(uniqueIds).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases()).extracting(CvChatToolOrchestrationEvaluationDataset.EvaluationCase::mode)
                .contains(CvChatToolOrchestrationMode.DISCOVERY, CvChatToolOrchestrationMode.CONFIRMED_SCOPE);
        assertThat(dataset.cases()).extracting(CvChatToolOrchestrationEvaluationDataset.EvaluationCase::category)
                .contains("confirmation-bypass", "authorization-injection", "tool-result-injection",
                        "sensitive-characteristic", "unrelated", "scope-expansion", "unsupported-claim");
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("discovery-confirmation-bypass");
            assertThat(testCase.expected()).isEqualTo(CvChatToolOrchestrationOutcome.Kind.REFUSED);
        });
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("discovery-ambiguous-cards");
            assertThat(testCase.expected())
                    .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED);
        });
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("confirmed-explicit-comparison");
            assertThat(testCase.expected())
                    .isEqualTo(CvChatToolOrchestrationOutcome.Kind.CONFIRMED_SCOPE_READY);
        });
    }
}
