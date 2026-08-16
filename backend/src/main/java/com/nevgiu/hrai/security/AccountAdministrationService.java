package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AccountResponse;
import com.nevgiu.hrai.security.dto.CreateAccountRequest;
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

    public AccountAdministrationService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll(String organizationId) {
        return users.findAllByOrganizationIdOrderByEmailAsc(organizationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountResponse create(String organizationId, CreateAccountRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw duplicateEmail();
        }
        try {
            AppUser saved = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(request.password()),
                    organizationId, request.roles()));
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }
    }

    private AccountAdministrationException duplicateEmail() {
        return new AccountAdministrationException(HttpStatus.CONFLICT,
                "An account could not be created with this email");
    }

    private AccountResponse toResponse(AppUser user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getOrganizationId(), user.isEnabled(),
                user.getRoles());
    }
}
