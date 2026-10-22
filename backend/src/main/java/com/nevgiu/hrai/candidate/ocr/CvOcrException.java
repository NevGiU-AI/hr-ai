package com.nevgiu.hrai.candidate.ocr;

/** Signals OCR configuration, process, timeout, rendering, or output failures to the pipeline. */
public class CvOcrException extends RuntimeException {
    public CvOcrException(String message) {
        super(message);
    }

    public CvOcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
