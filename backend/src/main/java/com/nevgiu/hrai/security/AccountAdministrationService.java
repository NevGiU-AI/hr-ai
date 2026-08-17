package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AccountResponse;
import com.nevgiu.hrai.security.dto.CreateAccountRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountRolesRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountStatusRequest;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AccountAdministrationService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionService sessions;
    private final LoginThrottleService loginThrottle;
    private final SecurityAuditService audit;

    public AccountAdministrationService(AppUserRepository users, PasswordEncoder passwordEncoder,
                                        AccountSessionService sessions, LoginThrottleService loginThrottle,
                                        SecurityAuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.loginThrottle = loginThrottle;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll(String organizationId) {
        return users.findAllByOrganizationIdOrderByEmailAsc(organizationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountResponse create(String organizationId, Long actorId, CreateAccountRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw duplicateEmail();
        }
        try {
            AppUser saved = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password()),
                    organizationId, request.roles()));
            audit.administration(SecurityEventType.ACCOUNT_CREATED, actorId, organizationId, saved,
                    "roles=" + sortedRoles(saved));
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }
    }

    @Transactional
    public AccountResponse updateRoles(String organizationId, Long actorId, Long accountId,
                                       UpdateAccountRolesRequest request) {
        rejectSelfManagement(actorId, accountId, "change your own roles");
        AppUser account = findAccount(organizationId, accountId);
        protectLastAdministrator(account, request.roles().contains(AppRole.ADMIN), organizationId);
        account.replaceRoles(request.roles());
        AccountResponse response = toResponse(users.saveAndFlush(account));
        sessions.revoke(account.getEmail());
        audit.administration(SecurityEventType.ROLES_CHANGED, actorId, organizationId, account,
                "roles=" + sortedRoles(account));
        return response;
    }

    @Transactional
    public AccountResponse updateStatus(String organizationId, Long actorId, Long accountId,
                                        UpdateAccountStatusRequest request) {
        rejectSelfManagement(actorId, accountId, "disable your own account");
        AppUser account = findAccount(organizationId, accountId);
        if (!request.enabled()) protectLastAdministrator(account, false, organizationId);
        account.setEnabled(request.enabled());
        AccountResponse response = toResponse(users.saveAndFlush(account));
        if (!request.enabled()) sessions.revoke(account.getEmail());
        audit.administration(request.enabled() ? SecurityEventType.ACCOUNT_ENABLED : SecurityEventType.ACCOUNT_DISABLED,
                actorId, organizationId, account, null);
        return response;
    }

    @Transactional(readOnly = true)
    public int revokeSessions(String organizationId, Long actorId, Long accountId) {
        rejectSelfManagement(actorId, accountId, "revoke your own session");
        AppUser account = findAccount(organizationId, accountId);
        int revoked = sessions.revoke(account.getEmail());
        audit.administration(SecurityEventType.SESSIONS_REVOKED, actorId, organizationId, account,
                "revokedSessions=" + revoked);
        return revoked;
    }

    @Transactional(readOnly = true)
    public AccountResponse unlock(String organizationId, Long actorId, Long accountId) {
        rejectSelfManagement(actorId, accountId, "unlock your own account");
        AppUser account = findAccount(organizationId, accountId);
        loginThrottle.unlockAccount(account.getEmail());
        audit.administration(SecurityEventType.ACCOUNT_UNLOCKED, actorId, organizationId, account, null);
        return toResponse(account);
    }

    private AppUser findAccount(String organizationId, Long accountId) {
        return users.findByIdAndOrganizationId(accountId, organizationId)
                .orElseThrow(() -> new AccountAdministrationException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private void rejectSelfManagement(Long actorId, Long accountId, String action) {
        if (actorId.equals(accountId)) {
            throw new AccountAdministrationException(HttpStatus.CONFLICT, "You cannot " + action);
        }
    }

    private void protectLastAdministrator(AppUser account, boolean remainsAdministrator, String organizationId) {
        if (account.isEnabled() && account.getRoles().contains(AppRole.ADMIN) && !remainsAdministrator
                && users.findAllByOrganizationIdAndEnabledTrueAndRolesContaining(organizationId, AppRole.ADMIN).size() <= 1) {
            throw new AccountAdministrationException(HttpStatus.CONFLICT,
                    "The organization must retain at least one enabled administrator");
        }
    }

    private AccountAdministrationException duplicateEmail() {
        return new AccountAdministrationException(HttpStatus.CONFLICT,
                "An account could not be created with this email");
    }

    private AccountResponse toResponse(AppUser user) {
        long remainingSeconds = loginThrottle.accountLockRemainingSeconds(user.getEmail());
        return new AccountResponse(user.getId(), user.getEmail(), user.getOrganizationId(), user.isEnabled(),
                user.getRoles(), remainingSeconds > 0, remainingSeconds);
    }

    private String sortedRoles(AppUser user) {
        return user.getRoles().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
    }
}
