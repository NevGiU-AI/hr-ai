package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    @Transactional(readOnly = true)
    List<Candidate> findAllByOrganizationId(String organizationId);

    @Transactional(readOnly = true)
    Optional<Candidate> findByIdAndOrganizationId(Long id, String organizationId);
}
