package com.nevgiu.hrai.chat;

import org.springframework.http.HttpStatus;

/**
 * Safe client-facing failure raised by the CV-chat boundary.
 *
 * <p>Messages must remain generic and must never contain CV text, recruiter questions, prompts, credentials, or raw
 * provider responses. {@link com.nevgiu.hrai.web.ApiExceptionHandler} converts the status into the shared API error
 * representation.</p>
 */
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
