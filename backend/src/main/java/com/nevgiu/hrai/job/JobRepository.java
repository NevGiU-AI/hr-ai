package com.nevgiu.hrai.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findAllByOrganizationId(String organizationId);
    Optional<Job> findByIdAndOrganizationId(Long id, String organizationId);
}
