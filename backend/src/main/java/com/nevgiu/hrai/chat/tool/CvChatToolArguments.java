package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;

/** Application-owned validation for model-generated tool arguments before any external or repository work begins. */
final class CvChatToolArguments {
    static final int MIN_QUERY_CHARACTERS = 2;
    static final int MAX_QUERY_CHARACTERS = 2_000;

    private CvChatToolArguments() { }

    static String requireBoundedQuery(String value) {
        // Validate the original payload length before normalization. Otherwise an oversized whitespace-only argument
        // could shrink below the limit and still force the application to accept a provider-generated oversized value.
        if (value == null || value.length() > MAX_QUERY_CHARACTERS) {
            throw invalidQuery();
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_QUERY_CHARACTERS) {
            throw invalidQuery();
        }
        return normalized;
    }

    static Long requirePositiveId(Long value) {
        if (value == null || value <= 0) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "CV chat tool ID must be a positive number");
        }
        return value;
    }

    private static CvChatException invalidQuery() {
        // Never echo a rejected argument: it may contain prompt injection, personal data, or an unexpectedly large
        // payload. The reviewed bounds are sufficient for clients and provider diagnostics.
        return new CvChatException(HttpStatus.BAD_REQUEST,
                "CV chat tool query must contain between 2 and 2000 characters");
    }
}
