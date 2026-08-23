package com.nevgiu.hrai.security;

import org.springframework.http.HttpStatus;

public class PasswordManagementException extends RuntimeException {
    private final HttpStatus status;

    public PasswordManagementException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
