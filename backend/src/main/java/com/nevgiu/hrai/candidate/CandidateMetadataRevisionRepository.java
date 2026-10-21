package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateMetadataRevisionRepository extends JpaRepository<CandidateMetadataRevision, Long> {
    List<CandidateMetadataRevision> findAllByCandidateIdAndOrganizationIdOrderByCreatedAtDescIdDesc(
            Long candidateId, String organizationId);
}
