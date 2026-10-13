package com.nevgiu.hrai.candidate;

import org.springframework.http.HttpStatus;

public class CvOriginalAccessException extends RuntimeException {
    private final HttpStatus status;

    public CvOriginalAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
