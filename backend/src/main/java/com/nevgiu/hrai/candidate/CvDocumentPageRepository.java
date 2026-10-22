package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CvDocumentPageRepository extends JpaRepository<CvDocumentPage, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from CvDocumentPage page where page.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") Long documentId);

    long countByDocumentId(Long documentId);
}
