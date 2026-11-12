package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import com.nevgiu.hrai.chat.tool.CandidateEvidenceCard;
import com.nevgiu.hrai.chat.tool.CandidateEvidenceSearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Finds a bounded candidate shortlist across one organization without sending every CV to a chat model. */
@Service
public class CvChatOrganizationDiscoveryService {
    private static final int RAW_MATCH_LIMIT = 50;
    private static final int CANDIDATE_LIMIT = 10;
    private static final int REFERENCE_LIMIT = 3;

    private final CvSemanticSearchService semanticSearch;
    private final CvChatEvidenceService evidence;
    private final CvChatProperties properties;
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;

    CvChatOrganizationDiscoveryService(CvSemanticSearchService semanticSearch, CvChatEvidenceService evidence,
                                       CvChatProperties properties, CandidateRepository candidates,
                                       CvDocumentRepository documents) {
        this.semanticSearch = semanticSearch;
        this.evidence = evidence;
        this.properties = properties;
        this.candidates = candidates;
        this.documents = documents;
    }

    /**
     * Searches only the authenticated organization and returns ranked choices for human confirmation.
     * Provider/vector I/O runs without a JPA transaction; every returned match is rechecked against current rows.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CandidateEvidenceSearchResult discover(String query, String organizationId) {
        if (!properties.semanticRetrievalEnabled()) {
            throw new CvChatException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Organization candidate discovery is unavailable");
        }
        List<String> queries = evidence.prepareRetrievalQueries(query, organizationId);
        List<CvSemanticMatch> raw = new ArrayList<>();
        for (String retrievalQuery : queries) {
            raw.addAll(semanticSearch.search(organizationId, retrievalQuery, List.of(), RAW_MATCH_LIMIT));
        }

        Map<Long, CandidateAccumulator> grouped = new LinkedHashMap<>();
        Map<Long, java.util.Optional<CurrentCandidate>> currentCandidates = new LinkedHashMap<>();
        raw.stream()
                .filter(match -> match.similarity() >= properties.semanticMinSimilarity())
                .sorted(Comparator.comparingDouble(CvSemanticMatch::similarity).reversed())
                .forEach(match -> currentMatch(match, organizationId, currentCandidates).ifPresent(current ->
                        grouped.computeIfAbsent(match.candidateId(), ignored -> new CandidateAccumulator(current.candidate()))
                                .add(reference(match), match.similarity())));

        List<CandidateEvidenceCard> cards = grouped.values().stream()
                .sorted(Comparator.comparingDouble(CandidateAccumulator::topSimilarity).reversed())
                .limit(CANDIDATE_LIMIT)
                .map(CandidateAccumulator::card)
                .toList();
        return new CandidateEvidenceSearchResult(cards, grouped.size() > CANDIDATE_LIMIT);
    }

    /** Rejects deleted, cross-tenant, obsolete-document, and stale-source-version vector rows. */
    private java.util.Optional<CurrentCandidate> currentMatch(
            CvSemanticMatch match, String organizationId,
            Map<Long, java.util.Optional<CurrentCandidate>> cache) {
        java.util.Optional<CurrentCandidate> resolved = cache.computeIfAbsent(match.candidateId(), candidateId -> {
            java.util.Optional<Candidate> candidate = candidates.findByIdAndOrganizationId(candidateId, organizationId);
            if (candidate.isEmpty()) return java.util.Optional.empty();
            List<CvDocument> history = documents.findAllByCandidateIdAndOrganizationId(candidateId, organizationId);
            if (history.isEmpty()) return java.util.Optional.empty();
            CvDocument current = history.getFirst();
            Instant sourceVersion = current.getReprocessedAt() == null
                    ? current.getImportedAt() : current.getReprocessedAt();
            return sourceVersion == null ? java.util.Optional.empty()
                    : java.util.Optional.of(new CurrentCandidate(candidate.get(), current.getId(), sourceVersion));
        });
        return resolved.filter(current -> current.documentId().equals(match.documentId())
                && current.sourceVersion().equals(match.sourceVersion()));
    }

    private String reference(CvSemanticMatch match) {
        return match.pageNumber() == null
                ? "CV-" + match.documentId()
                : "CV-" + match.documentId() + "-P" + match.pageNumber();
    }

    private record CurrentCandidate(Candidate candidate, Long documentId, Instant sourceVersion) { }

    private static final class CandidateAccumulator {
        private final Candidate candidate;
        private final LinkedHashSet<String> references = new LinkedHashSet<>();
        private double topSimilarity;

        private CandidateAccumulator(Candidate candidate) { this.candidate = candidate; }

        private void add(String reference, double similarity) {
            if (references.size() < REFERENCE_LIMIT) references.add(reference);
            topSimilarity = Math.max(topSimilarity, similarity);
        }

        private double topSimilarity() { return topSimilarity; }

        private CandidateEvidenceCard card() {
            return new CandidateEvidenceCard(candidate.getId(), candidate.getName(), candidate.getCurrentTitle(),
                    List.copyOf(references));
        }
    }
}
