package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.AccountResponse;
import com.nevgiu.hrai.security.dto.CreateAccountRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountRolesRequest;
import com.nevgiu.hrai.security.dto.UpdateAccountStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        return accounts.create(principal.organizationId(), principal.id(), request);
    }

    @PutMapping("/{accountId}/roles")
    public AccountResponse updateRoles(@PathVariable Long accountId,
                                       @Valid @RequestBody UpdateAccountRolesRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return accounts.updateRoles(principal.organizationId(), principal.id(), accountId, request);
    }

    @PutMapping("/{accountId}/status")
    public AccountResponse updateStatus(@PathVariable Long accountId,
                                        @Valid @RequestBody UpdateAccountStatusRequest request,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        return accounts.updateStatus(principal.organizationId(), principal.id(), accountId, request);
    }

    @PostMapping("/{accountId}/sessions/revoke")
    public java.util.Map<String, Integer> revokeSessions(@PathVariable Long accountId,
                                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return java.util.Map.of("revokedSessions",
                accounts.revokeSessions(principal.organizationId(), principal.id(), accountId));
    }

    @PostMapping("/{accountId}/lockout/unlock")
    public AccountResponse unlock(@PathVariable Long accountId,
                                  @AuthenticationPrincipal AppUserPrincipal principal) {
        return accounts.unlock(principal.organizationId(), principal.id(), accountId);
    }
}
