package com.nevgiu.hrai.candidate.ocr;

/** Port implemented by an OCR provider; keeps the extraction pipeline independent of Tesseract. */
public interface CvOcrEngine {
    /** Extracts document text and page provenance from PDF bytes without transferring them off-host. */
    CvOcrResult extract(byte[] pdf);
}
