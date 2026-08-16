package com.nevgiu.hrai.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    @Transactional(readOnly = true)
    List<Job> findAllByOrganizationId(String organizationId);

    @Transactional(readOnly = true)
    Optional<Job> findByIdAndOrganizationId(Long id, String organizationId);
}
