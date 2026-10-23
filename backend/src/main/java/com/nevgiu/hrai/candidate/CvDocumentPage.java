package com.nevgiu.hrai.candidate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Page-level OCR provenance retained for review and future evidence citations.
 *
 * <p>Rows are created only for OCR extraction. Native PDF text currently remains document-level
 * because PDFBox extraction does not use this page-evidence model.</p>
 */
@Entity
@Table(name = "cv_document_pages", uniqueConstraints = @UniqueConstraint(
        name = "uk_cv_document_pages_document_page", columnNames = {"document_id", "page_number"}))
public class CvDocumentPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Flyway foreign key deletes page provenance with its owning document. LAZY avoids loading
    // the full document when a caller only needs page text or confidence.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CvDocument document;

    // One-based page number as shown to a reviewer, not PDFBox's zero-based page index.
    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    // OCR output may exceed a normal varchar and contains candidate personal information.
    @Lob
    @Column(name = "extracted_text", nullable = false)
    private String extractedText;

    // Mean confidence for recognized words on this page, conventionally in Tesseract's 0-100 range.
    @Column(nullable = false)
    private double confidence;

    public Long getId() { return id; }
    public CvDocument getDocument() { return document; }
    public void setDocument(CvDocument document) { this.document = document; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
