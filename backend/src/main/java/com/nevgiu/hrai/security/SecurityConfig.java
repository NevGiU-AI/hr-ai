package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.audit.SecurityAuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityAuditService securityAuditService) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokens = new HttpSessionCsrfTokenRepository();
        csrfTokens.setHeaderName("X-XSRF-TOKEN");
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/import/initial").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/candidates/**", "/api/jobs/**").hasAnyRole("ADMIN", "RECRUITER")
                        .requestMatchers(HttpMethod.POST, "/api/evaluations/**").hasAnyRole("ADMIN", "RECRUITER", "REVIEWER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> jsonError(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) -> {
                            var authentication = SecurityContextHolder.getContext().getAuthentication();
                            if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
                                securityAuditService.administrationDenied(principal, request.getRequestURI(), 403);
                            }
                            jsonError(response, 403, "Access denied");
                        }))
                .build();
    }

    private static void jsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().printf("{\"status\":%d,\"message\":\"%s\"}", status, message);
    }
}
