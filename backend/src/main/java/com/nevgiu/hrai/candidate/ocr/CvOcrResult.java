package com.nevgiu.hrai.candidate.ocr;

import java.util.List;

public record CvOcrResult(String text, double confidence, List<Page> pages) {
    public record Page(int pageNumber, String text, double confidence) { }
}
