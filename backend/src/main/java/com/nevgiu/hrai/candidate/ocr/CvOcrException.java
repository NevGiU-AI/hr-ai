package com.nevgiu.hrai.candidate.ocr;

public class CvOcrException extends RuntimeException {
    public CvOcrException(String message) {
        super(message);
    }

    public CvOcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
