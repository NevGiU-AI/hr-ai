package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CvDocumentPageRepository extends JpaRepository<CvDocumentPage, Long> {
    /**
     * Removes the previous OCR provenance before replacement during successful reprocessing.
     * The bulk JPQL delete avoids loading every page entity. Flushing first ensures pending entity
     * changes reach the database before the bulk statement bypasses Hibernate's managed lifecycle.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from CvDocumentPage page where page.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") Long documentId);

    /** Counts provenance pages without exposing their personal-information-bearing text. */
    long countByDocumentId(Long documentId);
}
