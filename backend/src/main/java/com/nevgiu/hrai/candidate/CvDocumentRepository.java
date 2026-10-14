package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {
    @EntityGraph(attributePaths = "candidate")
    @Transactional(readOnly = true)
    Optional<CvDocument> findByOrganizationIdAndSha256(String organizationId, String sha256);

    @Transactional(readOnly = true)
    Optional<CvDocument> findByIdAndOrganizationId(Long id, String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from CvDocument document where document.id = :id and document.organizationId = :organizationId")
    Optional<CvDocument> findLockedByIdAndOrganizationId(Long id, String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from CvDocument document where document.id = :id")
    Optional<CvDocument> findLockedById(Long id);

    @Query("""
            select document.id from CvDocument document
            where document.storageKey is not null
              and document.retentionUntil is not null
              and document.retentionUntil <= :now
              and document.legalHold = false
            order by document.retentionUntil, document.id
            """)
    @Transactional(readOnly = true)
    List<Long> findExpiredOriginalIds(Instant now, Pageable pageable);
}
