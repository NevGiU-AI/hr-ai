package com.nevgiu.hrai.candidate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {
    @EntityGraph(attributePaths = "candidate")
    Optional<CvDocument> findByOrganizationIdAndSha256(String organizationId, String sha256);
}
