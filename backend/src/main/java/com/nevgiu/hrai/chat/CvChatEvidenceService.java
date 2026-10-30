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
    private final CvChatProperties properties;

    CvChatEvidenceService(CandidateRepository candidates, CvDocumentRepository documents,
                          CvDocumentPageRepository pages, CvSemanticSearchService semanticSearch,
                          CvChatProperties properties) {
        this.candidates = candidates;
        this.documents = documents;
        this.pages = pages;
        this.semanticSearch = semanticSearch;
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
            return semanticEvidence(candidate, history.getFirst(), question, organizationId);
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

    private List<CvChatEvidence> semanticEvidence(Candidate candidate, CvDocument current, String question,
                                                  String organizationId) {
        List<CvSemanticMatch> matches;
        try {
            // Candidate ownership was verified above. Passing only that verified ID narrows the mandatory tenant
            // filter and prevents either recruiter text or model output from selecting a broader search scope.
            matches = semanticSearch.search(organizationId, question, List.of(candidate.getId()),
                    properties.semanticMaxMatches());
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
        Map<String, CvChatEvidence> evidenceByReference = new LinkedHashMap<>();
        matches.stream()
                // Metadata filters are the storage boundary; these checks provide defense in depth before disclosure.
                .filter(match -> candidate.getId().equals(match.candidateId()))
                .filter(match -> current.getId().equals(match.documentId()))
                // Reprocessing changes the source version before replacement indexing completes. Rejecting old-version
                // chunks prevents chat from using stale evidence during that bounded reconciliation window.
                .filter(match -> currentSourceVersion != null && currentSourceVersion.equals(match.sourceVersion()))
                .filter(match -> match.similarity() >= properties.semanticMinSimilarity())
                .forEach(match -> mergeEvidence(evidenceByReference, candidate.getId(), match));
        return List.copyOf(evidenceByReference.values());
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
