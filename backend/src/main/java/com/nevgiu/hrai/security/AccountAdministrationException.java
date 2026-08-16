package com.nevgiu.hrai.security;

import org.springframework.http.HttpStatus;

public class AccountAdministrationException extends RuntimeException {
    private final HttpStatus status;

    public AccountAdministrationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
