package com.nevgiu.hrai.candidate.ocr;

public interface CvOcrEngine {
    CvOcrResult extract(byte[] pdf);
}
