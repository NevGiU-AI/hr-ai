package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatCandidateDiscoveryEvaluationDatasetTest {

    @Test
    void datasetIsVersionedSyntheticAndInternallyConsistent() {
        var dataset = CvChatCandidateDiscoveryEvaluationDataset.load();

        assertThat(dataset.version()).matches("\\d+\\.\\d+\\.\\d+");
        assertThat(dataset.description()).contains("Synthetic").doesNotContain("production");
        assertThat(dataset.candidates()).isNotEmpty();
        assertThat(dataset.cases()).isNotEmpty();
        assertThat(dataset.candidates()).extracting(candidate -> candidate.id()).doesNotHaveDuplicates();
        assertThat(dataset.cases()).extracting(testCase -> testCase.id()).doesNotHaveDuplicates();

        Set<Long> candidateIds = new HashSet<>();
        dataset.candidates().forEach(candidate -> {
            candidateIds.add(candidate.id());
            assertThat(candidate.id()).isPositive();
            assertThat(candidate.documentId()).isPositive();
            assertThat(candidate.organizationId()).matches("tenant-[a-z]");
            assertThat(candidate.name()).startsWith("Synthetic ");
            assertThat(candidate.currentTitle()).isNotBlank();
            assertThatCodeParsesAsInstant(candidate.sourceVersion());
        });

        dataset.cases().forEach(testCase -> {
            assertThat(testCase.id()).matches("[a-z0-9-]+");
            assertThat(testCase.organizationId()).matches("tenant-[a-z]");
            assertThat(testCase.query()).isNotBlank();
            assertThat(testCase.retrievalQueries()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(testCase.semanticResults()).extracting(result -> result.query()).doesNotHaveDuplicates();
            assertThat(testCase.semanticResults()).extracting(result -> result.query())
                    .containsExactlyInAnyOrderElementsOf(testCase.retrievalQueries());
            assertThat(testCase.relevantCandidateIds()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(testCase.requiredCandidateIds()).isSubsetOf(testCase.relevantCandidateIds());
            assertThat(testCase.forbiddenCandidateIds()).doesNotContainAnyElementsOf(testCase.relevantCandidateIds());
            assertThat(testCase.expectedCandidateOrder()).hasSizeLessThanOrEqualTo(10).doesNotHaveDuplicates();
            assertThat(testCase.expectedCandidateOrder()).allMatch(candidateIds::contains);
            assertThat(testCase.expectedMetrics()).extracting(metric -> metric.k())
                    .containsExactly(3, 5, 10);
            assertThat(testCase.expectedMetrics()).allSatisfy(metric -> {
                assertThat(metric.recall()).isBetween(0.0, 1.0);
                assertThat(metric.precision()).isBetween(0.0, 1.0);
            });
            assertThat(allMatches(testCase)).allSatisfy(match -> {
                assertThat(match.candidateId()).isIn(candidateIds);
                assertThat(match.documentId()).isPositive();
                assertThat(match.content()).isNotBlank();
                assertThat(match.similarity()).isBetween(0.0, 1.0);
                assertThatCodeParsesAsInstant(match.sourceVersion());
            });
        });
    }

    private List<CvChatCandidateDiscoveryEvaluationDataset.EvaluationMatch> allMatches(
            CvChatCandidateDiscoveryEvaluationDataset.EvaluationCase testCase) {
        return java.util.stream.Stream.concat(
                        testCase.semanticResults().stream().flatMap(result -> result.matches().stream()),
                        testCase.explicitMatches().stream())
                .toList();
    }

    private void assertThatCodeParsesAsInstant(String value) {
        assertThat(Instant.parse(value)).isNotNull();
    }
}
