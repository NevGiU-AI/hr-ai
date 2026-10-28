package com.nevgiu.hrai.candidate.semantic;

import com.nevgiu.hrai.candidate.CvDocument;
import com.nevgiu.hrai.candidate.CvDocumentPageRepository;
import com.nevgiu.hrai.candidate.CvDocumentRepository;
import com.nevgiu.hrai.candidate.CvIngestionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Loads a short immutable source snapshot and closes the JPA transaction before embedding begins. */
@Service
class CvSemanticSourceLoader {
    private final CvDocumentRepository documents;
    private final CvDocumentPageRepository pages;

    CvSemanticSourceLoader(CvDocumentRepository documents, CvDocumentPageRepository pages) {
        this.documents = documents;
        this.pages = pages;
    }

    /**
     * Loads a document with an explicit tenant predicate and returns no source unless import completed successfully.
     * A new read-only transaction ensures all lazy entity access finishes before Spring AI invokes the provider.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    Optional<CvSemanticSource> load(Long documentId, String organizationId) {
        CvDocument document = documents.findByIdAndOrganizationId(documentId, organizationId).orElse(null);
        if (document == null || document.getStatus() != CvIngestionStatus.IMPORTED || document.getCandidate() == null) {
            return Optional.empty();
        }
        List<CvSemanticTextUnit> pageUnits = pages.findAllByDocumentIdOrderByPageNumber(documentId).stream()
                .filter(page -> page.getExtractedText() != null && !page.getExtractedText().isBlank())
                .map(page -> new CvSemanticTextUnit(page.getPageNumber(), page.getExtractedText()))
                .toList();
        // OCR pages provide stronger citation provenance; native extracted text is the fallback when no pages exist.
        List<CvSemanticTextUnit> units = pageUnits.isEmpty()
                ? List.of(new CvSemanticTextUnit(null, document.getExtractedText()))
                : pageUnits;
        Instant sourceVersion = document.getReprocessedAt() == null
                ? document.getImportedAt() : document.getReprocessedAt();
        return Optional.of(new CvSemanticSource(document.getCandidate().getId(), document.getId(), organizationId,
                sourceVersion, units));
    }
}
