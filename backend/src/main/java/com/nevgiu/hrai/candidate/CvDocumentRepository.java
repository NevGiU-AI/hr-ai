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

    // Both predicates are required: candidate identifiers alone are not an authorization boundary in a multi-tenant DB.
    @Transactional(readOnly = true)
    @Query("""
            select document from CvDocument document
            where document.candidate.id = :candidateId and document.organizationId = :organizationId
            order by document.importedAt desc, document.id desc
            """)
    List<CvDocument> findAllByCandidateIdAndOrganizationId(Long candidateId, String organizationId);

    /*
     * PESSIMISTIC_WRITE makes PostgreSQL select the matching row FOR UPDATE. A competing deletion, expiry, or hold
     * change waits until the surrounding transaction commits or rolls back, including when it runs on another replica.
     * The organization predicate preserves the tenant boundary while acquiring that administrative mutation lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from CvDocument document where document.id = :id and document.organizationId = :organizationId")
    Optional<CvDocument> findLockedByIdAndOrganizationId(Long id, String organizationId);

    /*
     * The scheduler has no user-supplied organization context, but it still needs the same database-owned write lock.
     * Holding the lock until transaction completion ensures that only one replica can expire a document at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from CvDocument document where document.id = :id")
    Optional<CvDocument> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select document from CvDocument document
            where document.candidate.id = :candidateId and document.organizationId = :organizationId
            order by document.id
            """)
    List<CvDocument> findAllLockedByCandidateIdAndOrganizationId(Long candidateId, String organizationId);

    @Query("""
            select document.id from CvDocument document
            where document.storageKey is not null
              and document.retentionUntil is not null
              and document.retentionUntil <= :now
              and document.legalHold = false
            order by document.retentionUntil, document.id
            """)
    @Transactional(readOnly = true)
    // This is a candidate scan, not the final decision. CvRetentionService rechecks under a write lock.
    List<Long> findExpiredOriginalIds(Instant now, Pageable pageable);
}
