package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Loads one bounded, tenant-authorized evidence snapshot before the slower provider call begins. */
@Service
class CvChatEvidenceService {
    private final CandidateRepository candidates;
    private final CvDocumentRepository documents;
    private final CvDocumentPageRepository pages;
    private final CvChatProperties properties;

    CvChatEvidenceService(CandidateRepository candidates, CvDocumentRepository documents,
                          CvDocumentPageRepository pages, CvChatProperties properties) {
        this.candidates = candidates;
        this.documents = documents;
        this.pages = pages;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    List<CvChatEvidence> find(Long candidateId, String organizationId) {
        // A tenant predicate deliberately makes missing and cross-tenant identifiers indistinguishable.
        Candidate candidate = candidates.findByIdAndOrganizationId(candidateId, organizationId)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"));
        List<CvDocument> history = documents.findAllByCandidateIdAndOrganizationId(candidate.getId(), organizationId);
        List<CvChatEvidence> evidence;
        if (history.isEmpty()) {
            evidence = nativeEvidence(candidate, null);
        } else {
            CvDocument current = history.getFirst();
            List<CvChatEvidence> pageEvidence = pages.findAllByDocumentIdOrderByPageNumber(current.getId()).stream()
                    .limit(properties.maxEvidencePages())
                    .filter(page -> page.getExtractedText() != null && !page.getExtractedText().isBlank())
                    .map(page -> new CvChatEvidence("CV-" + current.getId() + "-P" + page.getPageNumber(),
                            candidate.getId(), current.getId(), page.getPageNumber(), excerpt(page.getExtractedText())))
                    .toList();
            evidence = pageEvidence.isEmpty() ? nativeEvidence(candidate, current.getId()) : pageEvidence;
        }
        if (evidence.isEmpty()) {
            throw new CvChatException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Candidate has no usable CV evidence for chat");
        }
        return evidence;
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
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= properties.maxExcerptCharacters()
                ? normalized
                : normalized.substring(0, properties.maxExcerptCharacters()) + "…";
    }
}
