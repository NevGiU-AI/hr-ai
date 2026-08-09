package com.nevgiu.hrai.security.dto;

import java.util.List;

public record AuthUserResponse(Long id, String email, String organizationId, List<String> roles) {}
