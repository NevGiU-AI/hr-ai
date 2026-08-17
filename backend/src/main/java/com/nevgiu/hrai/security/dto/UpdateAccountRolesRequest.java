package com.nevgiu.hrai.security.dto;

import com.nevgiu.hrai.security.AppRole;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateAccountRolesRequest(@NotEmpty Set<@NotNull AppRole> roles) {}
