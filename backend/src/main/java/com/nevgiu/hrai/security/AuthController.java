package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AuthUserResponse;
import com.nevgiu.hrai.security.dto.CsrfResponse;
import com.nevgiu.hrai.security.dto.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
    private final ActiveSessionRegistry sessions;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, ActiveSessionRegistry sessions) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                                   HttpServletResponse servletResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
            servletRequest.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, servletRequest, servletResponse);
            sessions.register(((AppUserPrincipal) authentication.getPrincipal()).id(), servletRequest.getSession(false));
            return ResponseEntity.ok(toResponse((AppUserPrincipal) authentication.getPrincipal()));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("status", 401, "message", "Invalid email or password"));
        }
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return toResponse((AppUserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null) sessions.unregister(session);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    private AuthUserResponse toResponse(AppUserPrincipal principal) {
        return new AuthUserResponse(principal.id(), principal.username(), principal.organizationId(),
                principal.authorities().stream().map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "")).toList());
    }
}
