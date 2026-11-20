package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Measures deterministic organization-discovery recall/precision while retaining absolute security invariants. */
class CvChatCandidateDiscoveryMetricsTest {
    private static final double METRIC_TOLERANCE = 0.000_001;

    @Test
    void measuresReviewedRecallPrecisionAndAuthorizationBoundaries() {
        var dataset = CvChatCandidateDiscoveryEvaluationDataset.load();

        for (var testCase : dataset.cases()) {
            CvSemanticSearchService semantic = mock(CvSemanticSearchService.class);
            CvChatEvidenceService evidence = mock(CvChatEvidenceService.class);
            CandidateRepository candidates = mock(CandidateRepository.class);
            CvDocumentRepository documents = mock(CvDocumentRepository.class);
            CvChatProperties properties = new CvChatProperties(10, 1200, true, 6, 0.35);
            CvChatOrganizationDiscoveryService service = new CvChatOrganizationDiscoveryService(
                    semantic, evidence, properties, candidates, documents);

            when(evidence.prepareRetrievalQueries(testCase.query(), testCase.organizationId()))
                    .thenReturn(testCase.retrievalQueries());
            testCase.semanticResults().forEach(result -> when(semantic.search(
                    testCase.organizationId(), result.query(), List.of(), 50))
                    .thenReturn(toMatches(result.matches())));
            when(semantic.searchExplicitTerms(testCase.organizationId(),
                    CvChatExplicitCriteria.from(testCase.query()), 200))
                    .thenReturn(toMatches(testCase.explicitMatches()));
            configureCurrentCandidates(dataset.candidates(), candidates, documents);

            var response = service.discover(testCase.query(), testCase.organizationId());
            List<Long> actualIds = response.candidates().stream().map(card -> card.id()).toList();

            assertThat(actualIds).as(testCase.id() + " ranked candidates")
                    .containsExactlyElementsOf(testCase.expectedCandidateOrder());
            assertThat(actualIds).as(testCase.id() + " required candidates")
                    .containsAll(testCase.requiredCandidateIds());
            if (!testCase.forbiddenCandidateIds().isEmpty()) {
                assertThat(actualIds).as(testCase.id() + " forbidden candidates")
                        .doesNotContainAnyElementsOf(testCase.forbiddenCandidateIds());
            }
            assertThat(response.truncated()).as(testCase.id() + " truncation")
                    .isEqualTo(testCase.expectedTruncated());

            for (var expected : testCase.expectedMetrics()) {
                int k = expected.k();
                double recall = recallAt(actualIds, testCase.relevantCandidateIds(), k);
                double precision = precisionAt(actualIds, testCase.relevantCandidateIds(), k);
                // Only aggregate synthetic fixture identifiers and metrics are emitted; no questions or evidence text.
                System.out.printf(Locale.ROOT, "case=%s k=%d recall=%.4f precision=%.4f returned=%d%n",
                        testCase.id(), k, recall, precision, Math.min(k, actualIds.size()));
                assertThat(recall).as(testCase.id() + " recall@" + k)
                        .isCloseTo(expected.recall(), org.assertj.core.data.Offset.offset(METRIC_TOLERANCE));
                assertThat(precision).as(testCase.id() + " precision@" + k)
                        .isCloseTo(expected.precision(), org.assertj.core.data.Offset.offset(METRIC_TOLERANCE));
            }
        }
    }

    private void configureCurrentCandidates(
            List<CvChatCandidateDiscoveryEvaluationDataset.EvaluationCandidate> fixtures,
            CandidateRepository candidates, CvDocumentRepository documents) {
        fixtures.forEach(fixture -> {
            Candidate candidate = new Candidate();
            candidate.setId(fixture.id());
            candidate.setName(fixture.name());
            candidate.setCurrentTitle(fixture.currentTitle());
            when(candidates.findByIdAndOrganizationId(fixture.id(), fixture.organizationId()))
                    .thenReturn(Optional.of(candidate));

            CvDocument document = mock(CvDocument.class);
            when(document.getId()).thenReturn(fixture.documentId());
            when(document.getImportedAt()).thenReturn(Instant.parse(fixture.sourceVersion()));
            when(documents.findAllByCandidateIdAndOrganizationId(fixture.id(), fixture.organizationId()))
                    .thenReturn(List.of(document));
        });
    }

    private List<CvSemanticMatch> toMatches(
            List<CvChatCandidateDiscoveryEvaluationDataset.EvaluationMatch> fixtures) {
        return fixtures.stream().map(fixture -> new CvSemanticMatch(
                fixture.candidateId(), fixture.documentId(), fixture.pageNumber(), fixture.content(),
                fixture.similarity(), Instant.parse(fixture.sourceVersion()))).toList();
    }

    private double recallAt(List<Long> actual, List<Long> relevant, int k) {
        Set<Long> topK = new LinkedHashSet<>(actual.subList(0, Math.min(k, actual.size())));
        long hits = relevant.stream().filter(topK::contains).count();
        return relevant.isEmpty() ? 1.0 : (double) hits / relevant.size();
    }

    private double precisionAt(List<Long> actual, List<Long> relevant, int k) {
        List<Long> topK = actual.subList(0, Math.min(k, actual.size()));
        long hits = topK.stream().filter(relevant::contains).count();
        return topK.isEmpty() ? 1.0 : (double) hits / topK.size();
    }
}
