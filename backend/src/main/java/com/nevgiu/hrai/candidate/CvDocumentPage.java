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

/** Page-level OCR provenance retained for review and future evidence citations. */
@Entity
@Table(name = "cv_document_pages", uniqueConstraints = @UniqueConstraint(
        name = "uk_cv_document_pages_document_page", columnNames = {"document_id", "page_number"}))
public class CvDocumentPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private CvDocument document;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Lob
    @Column(name = "extracted_text", nullable = false)
    private String extractedText;

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
