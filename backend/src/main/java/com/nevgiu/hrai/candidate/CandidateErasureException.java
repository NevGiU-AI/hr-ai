package com.nevgiu.hrai.candidate;

import org.springframework.http.HttpStatus;

public class CandidateErasureException extends RuntimeException {
    private final HttpStatus status;

    public CandidateErasureException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
