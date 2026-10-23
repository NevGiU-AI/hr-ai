package com.nevgiu.hrai.chat;

import org.springframework.http.HttpStatus;

public class CvChatException extends RuntimeException {
    private final HttpStatus status;

    CvChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
