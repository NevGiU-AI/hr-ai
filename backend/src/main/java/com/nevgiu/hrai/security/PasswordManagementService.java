package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import com.nevgiu.hrai.security.dto.ChangePasswordRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordManagementService {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AccountSessionService sessions;
    private final SecurityAuditService audit;

    public PasswordManagementService(AppUserRepository users, PasswordEncoder passwords,
                                     AccountSessionService sessions, SecurityAuditService audit) {
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public void change(AppUserPrincipal principal, ChangePasswordRequest request) {
        AppUser account = users.findByIdAndOrganizationId(principal.id(), principal.organizationId())
                .orElseThrow(() -> new PasswordManagementException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            audit.loginAttempt(SecurityEventType.PASSWORD_CHANGE_FAILED, SecurityEventOutcome.FAILURE,
                    principal.username(), null, "reason=current-password-mismatch");
            throw new PasswordManagementException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (passwords.matches(request.newPassword(), account.getPasswordHash())) {
            throw new PasswordManagementException(HttpStatus.BAD_REQUEST,
                    "New password must be different from the current password");
        }
        account.setPasswordHash(passwords.encode(request.newPassword()));
        users.saveAndFlush(account);
        audit.administration(SecurityEventType.PASSWORD_CHANGED, principal.id(), principal.organizationId(), account, null);
        sessions.revoke(account.getEmail());
    }
}
