package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    @Transactional(readOnly = true)
    List<Candidate> findAllByOrganizationId(String organizationId);

    @Transactional(readOnly = true)
    Optional<Candidate> findByIdAndOrganizationId(Long id, String organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from Candidate candidate where candidate.id = :id and candidate.organizationId = :organizationId")
    Optional<Candidate> findLockedByIdAndOrganizationId(Long id, String organizationId);
}
