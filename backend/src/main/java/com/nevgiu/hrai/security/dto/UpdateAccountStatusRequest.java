package com.nevgiu.hrai.security.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull Boolean enabled) {}
