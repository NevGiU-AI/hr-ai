package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateMetadataRevisionRepository extends JpaRepository<CandidateMetadataRevision, Long> {
    // Candidate ID is not globally sufficient in the shared schema; every history read must include organization ID.
    List<CandidateMetadataRevision> findAllByCandidateIdAndOrganizationIdOrderByCreatedAtDescIdDesc(
            Long candidateId, String organizationId);
}
