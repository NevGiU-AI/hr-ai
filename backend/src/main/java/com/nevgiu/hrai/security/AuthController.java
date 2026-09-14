package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AuthUserResponse;
import com.nevgiu.hrai.security.dto.CsrfResponse;
import com.nevgiu.hrai.security.dto.LoginRequest;
import com.nevgiu.hrai.security.audit.SecurityAuditService;
import com.nevgiu.hrai.security.audit.SecurityEventOutcome;
import com.nevgiu.hrai.security.audit.SecurityEventType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final LoginThrottleService loginThrottle;
    private final AccountSessionService sessions;
    private final SessionPolicyProperties sessionPolicy;
    private final SecurityAuditService audit;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, LoginThrottleService loginThrottle,
                          AccountSessionService sessions, SessionPolicyProperties sessionPolicy,
                          SecurityAuditService audit) {
        this.authenticationManager = authenticationManager;
        this.loginThrottle = loginThrottle;
        this.sessions = sessions;
        this.sessionPolicy = sessionPolicy;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        String clientIp = servletRequest.getRemoteAddr();
        LoginThrottleService.ThrottleDecision current = loginThrottle.check(request.email(), clientIp);
        if (current.blocked()) {
            audit.loginAttempt(SecurityEventType.LOGIN_THROTTLED, SecurityEventOutcome.DENIED, request.email(),
                    clientIp, "retryAfterSeconds=" + current.retryAfterSeconds());
            return rejectedLogin(HttpStatus.TOO_MANY_REQUESTS, current.retryAfterSeconds());
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
            servletRequest.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, servletRequest, servletResponse);
            int expiredSessions = sessions.expireOldestBeyondLimit(
                    principal.username(), sessionPolicy.maximumSessions());
            loginThrottle.recordSuccess(request.email());
            if (expiredSessions > 0) {
                audit.sessionLimitEnforced(principal, expiredSessions, sessionPolicy.maximumSessions());
            }
            audit.loginSucceeded(principal, clientIp);
            return ResponseEntity.ok(toResponse(principal));
        } catch (AuthenticationException exception) {
            LoginThrottleService.ThrottleDecision recorded = loginThrottle.recordFailure(request.email(), clientIp);
            if (recorded.blocked()) {
                if (loginThrottle.accountLockRemainingSeconds(request.email()) > 0) {
                    audit.loginAttempt(SecurityEventType.ACCOUNT_LOCKED, SecurityEventOutcome.DENIED, request.email(),
                            clientIp, "lockDurationSeconds=" + recorded.retryAfterSeconds());
                }
                audit.loginAttempt(SecurityEventType.LOGIN_THROTTLED, SecurityEventOutcome.DENIED, request.email(),
                        clientIp, "retryAfterSeconds=" + recorded.retryAfterSeconds());
                return rejectedLogin(HttpStatus.TOO_MANY_REQUESTS, recorded.retryAfterSeconds());
            }
            audit.loginAttempt(SecurityEventType.LOGIN_FAILED, SecurityEventOutcome.FAILURE, request.email(),
                    clientIp, null);
            return rejectedLogin(HttpStatus.UNAUTHORIZED, 0);
        }
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return toResponse((AppUserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            audit.logout(principal, request.getRemoteAddr());
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private AuthUserResponse toResponse(AppUserPrincipal principal) {
        return new AuthUserResponse(principal.id(), principal.username(), principal.organizationId(),
                principal.authorities().stream().map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).toList());
    }

    private ResponseEntity<java.util.Map<String, Object>> rejectedLogin(HttpStatus status, long retryAfterSeconds) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (retryAfterSeconds > 0) response.header("Retry-After", Long.toString(retryAfterSeconds));
        return response.body(java.util.Map.of("status", status.value(), "message", "Invalid email or password"));
    }
}
