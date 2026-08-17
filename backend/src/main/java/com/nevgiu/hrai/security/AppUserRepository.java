package com.nevgiu.hrai.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByIdAndOrganizationId(Long id, String organizationId);
    List<AppUser> findAllByOrganizationIdOrderByEmailAsc(String organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findAllByOrganizationIdAndEnabledTrueAndRolesContaining(String organizationId, AppRole role);
}
