package com.nevgiu.hrai.security.audit;

import com.nevgiu.hrai.security.AppUser;
import com.nevgiu.hrai.security.AppUserPrincipal;
import com.nevgiu.hrai.security.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private final SecurityAuditRepository events;
    private final SecurityAuditWriter writer;
    private final AppUserRepository users;

    public SecurityAuditService(SecurityAuditRepository events, SecurityAuditWriter writer, AppUserRepository users) {
        this.events = events;
        this.writer = writer;
        this.users = users;
    }

    public void loginSucceeded(AppUserPrincipal principal, String clientIp) {
        save(new SecurityAuditEvent(principal.organizationId(), principal.id(), principal.username(), principal.id(),
                principal.username(), null, hash(clientIp), SecurityEventType.LOGIN_SUCCEEDED,
                SecurityEventOutcome.SUCCESS, null));
    }

    public void loginAttempt(SecurityEventType type, SecurityEventOutcome outcome, String email,
                             String clientIp, String details) {
        String normalizedEmail = normalizeEmail(email);
        AppUser target = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        save(new SecurityAuditEvent(target == null ? null : target.getOrganizationId(), null, null,
                target == null ? null : target.getId(), target == null ? null : target.getEmail(),
                target == null ? hash(normalizedEmail) : null, hash(clientIp), type, outcome, safe(details)));
    }

    public void logout(AppUserPrincipal principal, String clientIp) {
        save(new SecurityAuditEvent(principal.organizationId(), principal.id(), principal.username(), principal.id(),
                principal.username(), null, hash(clientIp), SecurityEventType.LOGOUT,
                SecurityEventOutcome.SUCCESS, null));
    }

    public void administration(SecurityEventType type, Long actorId, String organizationId,
                               AppUser target, String details) {
        save(new SecurityAuditEvent(organizationId, actorId, null, target.getId(), target.getEmail(), null, null,
                type, SecurityEventOutcome.SUCCESS, safe(details)));
    }

    public void administrationDenied(AppUserPrincipal actor, String path, int status) {
        if (actor == null) return;
        save(new SecurityAuditEvent(actor.organizationId(), actor.id(), actor.username(), null, null, null, null,
                SecurityEventType.ADMIN_ACTION_DENIED, SecurityEventOutcome.DENIED,
                safe("status=" + status + ";path=" + path)));
    }

    public SecurityAuditPageResponse findAll(String organizationId, int page, int size) {
        var result = events.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return new SecurityAuditPageResponse(result.getContent().stream().map(SecurityAuditEventResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private void save(SecurityAuditEvent event) {
        try {
            writer.save(event);
        } catch (RuntimeException exception) {
            log.error("Security audit event persistence failed for type={} outcome={}",
                    event.getEventType(), event.getOutcome());
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        String normalized = value == null || value.isBlank() ? "unknown" : value.trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String safe(String details) {
        if (details == null) return null;
        return details.length() <= 500 ? details : details.substring(0, 500);
    }
}
