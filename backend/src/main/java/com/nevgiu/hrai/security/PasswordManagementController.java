package com.nevgiu.hrai.security;

import com.nevgiu.hrai.security.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordManagementController {
    private final PasswordManagementService passwords;

    public PasswordManagementController(PasswordManagementService passwords) { this.passwords = passwords; }

    @PutMapping
    public void change(@AuthenticationPrincipal AppUserPrincipal principal,
                       @Valid @RequestBody ChangePasswordRequest request) {
        passwords.change(principal, request);
    }
}
