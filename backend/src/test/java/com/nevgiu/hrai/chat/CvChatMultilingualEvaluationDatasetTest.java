package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatMultilingualEvaluationDatasetTest {

    @Test
    void keepsRetrievalAndAnswerFixturesVersionedSyntheticAndComplete() {
        var dataset = CvChatMultilingualEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.0.0");
        assertThat(dataset.description()).contains("Synthetic");
        assertThat(dataset.documents()).hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(document -> {
                    assertThat(document.id()).isNotBlank();
                    assertThat(document.reference()).startsWith("CV-");
                    assertThat(document.organizationId()).isIn("tenant-a", "tenant-b");
                    assertThat(document.candidateId()).isPositive();
                    assertThat(document.documentId()).isPositive();
                    assertThat(document.text()).isNotBlank();
                });
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(7)
                .allSatisfy(testCase -> {
                    assertThat(testCase.id()).isNotBlank();
                    assertThat(testCase.language()).isIn("en", "fr", "pt");
                    assertThat(testCase.question()).isNotBlank();
                    assertThat(testCase.expectedReference()).isEqualTo("CV-61");
                    assertThat(testCase.requiredEvidenceTerms()).isNotEmpty();
                    assertThat(testCase.languageMarkers()).isNotEmpty();
                });
        Set<String> uniqueIds = dataset.cases().stream()
                .map(CvChatMultilingualEvaluationDataset.EvaluationCase::id)
                .collect(Collectors.toSet());
        assertThat(uniqueIds).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases()).extracting(CvChatMultilingualEvaluationDataset.EvaluationCase::language)
                .contains("en", "fr", "pt");
        assertThat(dataset.cases()).filteredOn(CvChatMultilingualEvaluationDataset.EvaluationCase::evaluateAnswer)
                .hasSize(3);
    }
}
