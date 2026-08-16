package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findAllByOrganizationId(String organizationId);
    Optional<Candidate> findByIdAndOrganizationId(Long id, String organizationId);
}
