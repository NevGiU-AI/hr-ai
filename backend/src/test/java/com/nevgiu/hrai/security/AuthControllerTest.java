package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.nevgiu.hrai.security.audit.SecurityAuditService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final LoginThrottleService throttle = mock(LoginThrottleService.class);
    private final AccountSessionService sessions = mock(AccountSessionService.class);
    private final SessionPolicyProperties sessionPolicy = new SessionPolicyProperties(3);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final AuthController controller = new AuthController(
            authenticationManager, throttle, sessions, sessionPolicy, audit);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void rejectsAnAlreadyLockedLoginWithoutAuthenticating() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(throttle.check("person@example.com", "203.0.113.10"))
                .thenReturn(new LoginThrottleService.ThrottleDecision(120));

        ResponseEntity<?> result = controller.login(
                new LoginRequest("person@example.com", "wrong-password"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("120");
        assertThat(result.getBody().toString()).contains("Invalid email or password");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void recordsFailedAuthenticationAndKeepsTheGenericError() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(throttle.check("person@example.com", "203.0.113.10"))
                .thenReturn(new LoginThrottleService.ThrottleDecision(0));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(throttle.recordFailure("person@example.com", "203.0.113.10"))
                .thenReturn(new LoginThrottleService.ThrottleDecision(0));

        ResponseEntity<?> result = controller.login(
                new LoginRequest("person@example.com", "wrong-password"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getBody().toString()).contains("Invalid email or password");
        verify(throttle).recordFailure("person@example.com", "203.0.113.10");
    }

    @Test
    void enforcesTheSessionLimitForASuccessfulLogin() {
        Authentication authentication = mock(Authentication.class);
        HttpSession httpSession = mock(HttpSession.class);
        AppUserPrincipal principal = new AppUserPrincipal(1L, "person@example.com", "hash", "tenant-a", true,
                List.of(new SimpleGrantedAuthority("ROLE_RECRUITER")));
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.changeSessionId()).thenReturn("new-session");
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.getSession(true)).thenReturn(httpSession);
        when(throttle.check("person@example.com", "203.0.113.10"))
                .thenReturn(new LoginThrottleService.ThrottleDecision(0));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(sessions.expireOldestBeyondLimit("person@example.com", 3)).thenReturn(1);

        ResponseEntity<?> result = controller.login(
                new LoginRequest("person@example.com", "correct-password"), request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(sessions).expireOldestBeyondLimit("person@example.com", 3);
        verify(audit).sessionLimitEnforced(principal, 1, 3);
        verify(audit).loginSucceeded(principal, "203.0.113.10");
    }
}
