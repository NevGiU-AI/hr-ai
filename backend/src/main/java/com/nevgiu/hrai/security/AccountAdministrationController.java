package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AccountResponse;
import com.nevgiu.hrai.security.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AccountAdministrationController {
    private final AccountAdministrationService accounts;

    public AccountAdministrationController(AccountAdministrationService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AccountResponse> findAll(@AuthenticationPrincipal AppUserPrincipal principal) {
        return accounts.findAll(principal.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request,
                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        return accounts.create(principal.organizationId(), request);
    }
}
