package com.nevgiu.hrai.security.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEvent, Long> {
    Page<SecurityAuditEvent> findAllByOrganizationIdOrderByCreatedAtDesc(String organizationId, Pageable pageable);
    long deleteByCreatedAtBefore(Instant cutoff);
}
