package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.semantic.CvSemanticMatch;
import com.nevgiu.hrai.candidate.semantic.CvSemanticSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Loads one bounded, tenant-authorized evidence snapshot before the slower provider call begins. */
@Service
class CvChatEvidenceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatEvidenceService.class);
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;
    private final CvDocumentPageRepository pages;
    private final CvSemanticSearchService semanticSearch;
    private final CvChatRetrievalQueryNormalizer retrievalQueryNormalizer;
    private final CvChatProperties properties;

    CvChatEvidenceService(CandidateRepository candidates, CvDocumentRepository documents,
                          CvDocumentPageRepository pages, CvSemanticSearchService semanticSearch,
                          CvChatRetrievalQueryNormalizer retrievalQueryNormalizer, CvChatProperties properties) {
        this.candidates = candidates;
        this.documents = documents;
        this.pages = pages;
        this.semanticSearch = semanticSearch;
        this.retrievalQueryNormalizer = retrievalQueryNormalizer;
        this.properties = properties;
    }

    /**
     * Verifies the selected candidate inside the authenticated organization before question classification.
     * This lookup discloses no CV evidence and preserves the generic missing/cross-tenant {@code 404} contract.
     */
    @Transactional(readOnly = true)
    void authorizeCandidate(Long candidateId, String organizationId) {
        authorizedCandidate(candidateId, organizationId);
    }

    @Transactional(readOnly = true)
    List<CvChatEvidence> find(Long candidateId, String question, String organizationId) {
        Candidate candidate = authorizedCandidate(candidateId, organizationId);
        List<CvDocument> history = documents.findAllByCandidateIdAndOrganizationId(candidate.getId(), organizationId);
        if (properties.semanticRetrievalEnabled() && !history.isEmpty()) {
            return semanticEvidence(candidate, history.getFirst(), retrievalQueries(candidate, question, organizationId),
                    organizationId);
        }
        List<CvChatEvidence> evidence;
        if (history.isEmpty()) {
            evidence = nativeEvidence(candidate, null);
        } else {
            // The repository orders newest imports first. Chat uses only the current document, never obsolete history.
            CvDocument current = history.getFirst();
            // Page rows exist for OCR extractions and preserve the page numbers a human reviewer sees in the PDF.
            List<CvChatEvidence> pageEvidence = pages.findAllByDocumentIdOrderByPageNumber(current.getId()).stream()
                    .limit(properties.maxEvidencePages())
                    .filter(page -> page.getExtractedText() != null && !page.getExtractedText().isBlank())
                    .map(page -> new CvChatEvidence("CV-" + current.getId() + "-P" + page.getPageNumber(),
                            candidate.getId(), current.getId(), page.getPageNumber(), excerpt(page.getExtractedText())))
                    .toList();
            // Native extraction has no page provenance, so it receives one document-level citation instead.
            evidence = pageEvidence.isEmpty() ? nativeEvidence(candidate, current.getId()) : pageEvidence;
        }
        if (evidence.isEmpty()) {
            throw new CvChatException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Candidate has no usable CV evidence for chat");
        }
        return evidence;
    }

    private Candidate authorizedCandidate(Long candidateId, String organizationId) {
        // A tenant predicate deliberately makes missing and cross-tenant identifiers indistinguishable.
        return candidates.findByIdAndOrganizationId(candidateId, organizationId)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }

    private List<CvChatEvidence> semanticEvidence(Candidate candidate, CvDocument current, List<String> queries,
                                                  String organizationId) {
        List<CvSemanticMatch> matches;
        try {
            // Candidate ownership was verified above. Passing only that verified ID narrows the mandatory tenant
            // filter and prevents either recruiter text or model output from selecting a broader search scope.
            List<CvSemanticMatch> originalMatches = semanticSearch.search(organizationId, queries.getFirst(),
                    List.of(candidate.getId()), properties.semanticMaxMatches());
            if (queries.size() == 1) {
                matches = originalMatches;
            } else {
                try {
                    List<CvSemanticMatch> normalizedMatches = semanticSearch.search(organizationId, queries.get(1),
                            List.of(candidate.getId()), properties.semanticMaxMatches());
                    matches = java.util.stream.Stream.concat(originalMatches.stream(), normalizedMatches.stream())
                            .toList();
                } catch (RuntimeException normalizedSearchFailure) {
                    // The expansion is optional. Once the original search succeeds, a failure in its additional
                    // embedding/search must not reduce the availability of the already accepted retrieval path.
                    LOGGER.warn("CV normalized semantic retrieval failed for organizationId={} candidateId={} "
                                    + "documentId={} failureType={}; using original matches",
                            organizationId, candidate.getId(), current.getId(),
                            normalizedSearchFailure.getClass().getSimpleName());
                    matches = originalMatches;
                }
            }
        } catch (RuntimeException failure) {
            // Never log the question, matched excerpts, embeddings, or provider/database message because each may
            // contain personal data. IDs, stage, and exception type are sufficient operational diagnostics.
            LOGGER.warn("CV semantic retrieval failed for organizationId={} candidateId={} documentId={} failureType={}",
                    organizationId, candidate.getId(), current.getId(), failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV semantic retrieval is unavailable");
        }
        Instant currentSourceVersion = current.getReprocessedAt() == null
                ? current.getImportedAt()
                : current.getReprocessedAt();
        List<CvSemanticMatch> accepted = matches.stream()
                // Metadata filters are the storage boundary; these checks provide defense in depth before disclosure.
                .filter(match -> candidate.getId().equals(match.candidateId()))
                .filter(match -> current.getId().equals(match.documentId()))
                // Reprocessing changes the source version before replacement indexing completes. Rejecting old-version
                // chunks prevents chat from using stale evidence during that bounded reconciliation window.
                .filter(match -> currentSourceVersion != null && currentSourceVersion.equals(match.sourceVersion()))
                .filter(match -> match.similarity() >= properties.semanticMinSimilarity())
                .collect(java.util.stream.Collectors.toMap(
                        MatchIdentity::new,
                        match -> match,
                        (left, right) -> left.similarity() >= right.similarity() ? left : right,
                        LinkedHashMap::new))
                .values().stream()
                .sorted(Comparator.comparingDouble(CvSemanticMatch::similarity).reversed())
                // Query expansion may return up to N neighbors per query, but disclosure remains bounded to N overall.
                .limit(properties.semanticMaxMatches())
                .toList();
        double topSimilarity = matches.stream().mapToDouble(CvSemanticMatch::similarity).max().orElse(0.0);
        // Log only counts and scores. Questions, normalized queries, chunk text, and embeddings may contain personal
        // data and are deliberately excluded from operational diagnostics.
        LOGGER.info("CV semantic retrieval completed for organizationId={} candidateId={} documentId={} "
                        + "queryCount={} rawMatches={} acceptedMatches={} topSimilarity={}",
                organizationId, candidate.getId(), current.getId(), queries.size(), matches.size(), accepted.size(),
                String.format(java.util.Locale.ROOT, "%.4f", topSimilarity));
        Map<String, CvChatEvidence> evidenceByReference = new LinkedHashMap<>();
        accepted.forEach(match -> mergeEvidence(evidenceByReference, candidate.getId(), match));
        return List.copyOf(evidenceByReference.values());
    }

    private List<String> retrievalQueries(Candidate candidate, String question, String organizationId) {
        String normalizedRetrievalQuery;
        try {
            // The original question remains the answer-model input. This separate English query improves multilingual
            // embedding recall without allowing provider output to select a tenant, candidate, or document.
            normalizedRetrievalQuery = retrievalQueryNormalizer.normalize(question);
        } catch (RuntimeException normalizationFailure) {
            // Normalization is an availability optimization, not an authorization boundary. The policy-approved
            // original question remains a safe fallback when the additional provider call is unavailable.
            LOGGER.warn("CV chat retrieval query normalization failed for candidateId={} organizationId={} "
                            + "failureType={}", candidate.getId(), organizationId,
                    normalizationFailure.getClass().getSimpleName());
            return List.of(question);
        }
        if (normalizedRetrievalQuery == null || normalizedRetrievalQuery.isBlank()
                || question.equalsIgnoreCase(normalizedRetrievalQuery.trim())) {
            return List.of(question);
        }
        // Original wording retains language-specific signal; the normalized English query adds cross-language recall.
        return List.of(question, normalizedRetrievalQuery.trim());
    }

    /** Identifies the same stored chunk across original and normalized-query result sets without logging its content. */
    private record MatchIdentity(Long candidateId, Long documentId, Integer pageNumber, String content,
                                 Instant sourceVersion) {
        private MatchIdentity(CvSemanticMatch match) {
            this(match.candidateId(), match.documentId(), match.pageNumber(), match.content(), match.sourceVersion());
        }
    }

    private void mergeEvidence(Map<String, CvChatEvidence> evidenceByReference, Long candidateId,
                               CvSemanticMatch match) {
        String reference = reference(match);
        CvChatEvidence existing = evidenceByReference.get(reference);
        String combined = existing == null ? match.content() : existing.excerpt() + " " + match.content();
        // Multiple chunks can originate from one OCR page. One merged, bounded evidence item keeps the reviewer-visible
        // citation unique while retaining the vector-store ranking order in the supplied excerpt.
        evidenceByReference.put(reference, new CvChatEvidence(reference, candidateId, match.documentId(),
                match.pageNumber(), excerpt(combined)));
    }

    private String reference(CvSemanticMatch match) {
        return match.pageNumber() == null
                ? "CV-" + match.documentId()
                : "CV-" + match.documentId() + "-P" + match.pageNumber();
    }

    private List<CvChatEvidence> nativeEvidence(Candidate candidate, Long documentId) {
        if (candidate.getCvText() == null || candidate.getCvText().isBlank()) {
            return List.of();
        }
        String reference = documentId == null ? "CV-CANDIDATE-" + candidate.getId() : "CV-" + documentId;
        return List.of(new CvChatEvidence(reference, candidate.getId(), documentId, null,
                excerpt(candidate.getCvText())));
    }

    private String excerpt(String text) {
        // Normalization makes excerpts deterministic and avoids spending model tokens on PDF/OCR layout whitespace.
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= properties.maxExcerptCharacters()
                ? normalized
                : normalized.substring(0, properties.maxExcerptCharacters()) + "…";
    }
}
