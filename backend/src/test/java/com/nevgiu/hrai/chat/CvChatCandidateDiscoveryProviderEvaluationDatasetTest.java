package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatCandidateDiscoveryProviderEvaluationDatasetTest {

    @Test
    void datasetIsVersionedSyntheticMultilingualAndInternallyConsistent() {
        var dataset = CvChatCandidateDiscoveryProviderEvaluationDataset.load();

        assertThat(dataset.version()).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(dataset.description()).contains("Synthetic");
        assertThat(dataset.documents()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(dataset.documents()).extracting(document -> document.id()).doesNotHaveDuplicates();
        assertThat(dataset.documents()).extracting(document -> document.candidateId()).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(testCase -> testCase.id()).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(testCase -> testCase.language())
                .contains("en", "fr", "pt");

        Set<Long> candidateIds = new HashSet<>();
        dataset.documents().forEach(document -> {
            candidateIds.add(document.candidateId());
            assertThat(document.id()).matches("provider-[a-z0-9-]+");
            assertThat(document.organizationId()).matches("tenant-[a-z]");
            assertThat(document.candidateId()).isPositive();
            assertThat(document.candidateName()).startsWith("Synthetic ");
            assertThat(document.currentTitle()).isNotBlank();
            assertThat(document.documentId()).isPositive();
            assertThat(Instant.parse(document.sourceVersion())).isNotNull();
            assertThat(document.text()).isNotBlank();
        });

        dataset.cases().forEach(testCase -> {
            assertThat(testCase.id()).matches("[a-z0-9-]+");
            assertThat(testCase.language()).isIn("en", "fr", "pt");
            assertThat(testCase.query()).isNotBlank();
            assertThat(testCase.organizationId()).matches("tenant-[a-z]");
            assertThat(testCase.relevantCandidateIds()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(testCase.relevantCandidateIds()).allMatch(candidateIds::contains);
            assertThat(testCase.requiredCandidateIds()).isNotEmpty()
                    .isSubsetOf(testCase.relevantCandidateIds());
            assertThat(testCase.forbiddenCandidateIds()).isNotEmpty()
                    .doesNotContainAnyElementsOf(testCase.relevantCandidateIds());
            assertThat(testCase.forbiddenCandidateIds()).allMatch(candidateIds::contains);
            assertThat(testCase.minimumRecallAt5()).isBetween(0.0, 1.0);
            assertThat(testCase.minimumPrecisionAt5()).isBetween(0.0, 1.0);
        });
    }
}
