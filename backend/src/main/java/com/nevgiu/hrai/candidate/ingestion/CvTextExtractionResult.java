package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;

import java.util.List;

public record CvTextExtractionResult(
        String text,
        CvExtractionMethod method,
        Double confidence,
        List<CvOcrResult.Page> pages,
        boolean ocrAttemptFailed
) {
    public static CvTextExtractionResult nativeText(String text) {
        return new CvTextExtractionResult(text, CvExtractionMethod.NATIVE, null, List.of(), false);
    }
}
