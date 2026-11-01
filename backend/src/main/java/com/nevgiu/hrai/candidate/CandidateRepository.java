package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    /**
     * Returns one organization's candidates newest first. The ID supplies a stable tie-breaker when
     * rows share the same creation timestamp, so repeated requests cannot arbitrarily reorder them.
     */
    @Transactional(readOnly = true)
    List<Candidate> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(String organizationId);

    @Transactional(readOnly = true)
    Optional<Candidate> findByIdAndOrganizationId(Long id, String organizationId);

    /**
     * Resolves an exact candidate name inside one organization. Case and harmless surrounding whitespace do not affect
     * matching, but partial matches are deliberately excluded so a natural-language reference cannot silently widen
     * the candidate set. A bounded slice lets the service require explicit disambiguation without returning an
     * unbounded duplicate-name payload.
     */
    @Transactional(readOnly = true)
    @Query("""
            select new com.nevgiu.hrai.candidate.CandidateIdentity(
                candidate.id, candidate.name, candidate.currentTitle)
            from Candidate candidate
            where candidate.organizationId = :organizationId
              and lower(trim(candidate.name)) = lower(trim(:name))
            order by candidate.createdAt desc, candidate.id desc
            """)
    Slice<CandidateIdentity> findAllByExactNameAndOrganizationId(
            String name, String organizationId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from Candidate candidate where candidate.id = :id and candidate.organizationId = :organizationId")
    Optional<Candidate> findLockedByIdAndOrganizationId(Long id, String organizationId);
}
