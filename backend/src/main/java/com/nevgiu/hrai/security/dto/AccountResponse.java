package com.nevgiu.hrai.security.dto;

import com.nevgiu.hrai.security.AppRole;

import java.util.Set;

public record AccountResponse(Long id, String email, String organizationId, boolean enabled, Set<AppRole> roles) {}
