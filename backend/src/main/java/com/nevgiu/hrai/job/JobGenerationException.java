package com.nevgiu.hrai.job;

import org.springframework.http.HttpStatus;

/** Carries a safe client-facing failure without exposing provider response or credential details. */
public class JobGenerationException extends RuntimeException {
    private final HttpStatus status;

    public JobGenerationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
