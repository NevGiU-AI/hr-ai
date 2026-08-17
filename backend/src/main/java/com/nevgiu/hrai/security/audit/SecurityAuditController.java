package com.nevgiu.hrai.security.audit;

import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security-events")
public class SecurityAuditController {
    private final SecurityAuditService audit;

    public SecurityAuditController(SecurityAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public SecurityAuditPageResponse findAll(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return audit.findAll(principal.organizationId(), page, size);
    }
}
