package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatMultilingualEvaluationDatasetTest {

    @Test
    void keepsRetrievalAndAnswerFixturesVersionedSyntheticAndComplete() {
        var dataset = CvChatMultilingualEvaluationDataset.load();

        assertThat(dataset.version()).isEqualTo("1.1.0");
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
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(8)
                .allSatisfy(testCase -> {
                    assertThat(testCase.id()).isNotBlank();
                    assertThat(testCase.language()).isIn("en", "fr", "pt");
                    assertThat(testCase.question()).isNotBlank();
                    assertThat(testCase.expectedCandidateId()).isPositive();
                    assertThat(testCase.expectedReference()).startsWith("CV-");
                    assertThat(testCase.requiredEvidenceTerms()).isNotEmpty();
                    assertThat(testCase.languageMarkers()).isNotEmpty();
                    assertThat(dataset.documents()).anyMatch(document ->
                            document.organizationId().equals("tenant-a")
                                    && document.candidateId().equals(testCase.expectedCandidateId())
                                    && document.reference().equals(testCase.expectedReference()));
                });
        Set<String> uniqueIds = dataset.cases().stream()
                .map(CvChatMultilingualEvaluationDataset.EvaluationCase::id)
                .collect(Collectors.toSet());
        assertThat(uniqueIds).hasSameSizeAs(dataset.cases());
        assertThat(dataset.cases()).extracting(CvChatMultilingualEvaluationDataset.EvaluationCase::language)
                .contains("en", "fr", "pt");
        assertThat(dataset.cases()).filteredOn(CvChatMultilingualEvaluationDataset.EvaluationCase::evaluateAnswer)
                .hasSize(3);
        assertThat(dataset.cases()).anySatisfy(testCase -> {
            assertThat(testCase.id()).isEqualTo("en-picker-short-java-comparison");
            assertThat(testCase.expectedCandidateId()).isEqualTo(71L);
            assertThat(testCase.expectedReference()).isEqualTo("CV-70");
        });
    }
}
