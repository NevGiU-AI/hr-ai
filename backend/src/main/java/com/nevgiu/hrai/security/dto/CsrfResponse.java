package com.nevgiu.hrai.security.dto;

public record CsrfResponse(String token, String headerName, String parameterName) {}
