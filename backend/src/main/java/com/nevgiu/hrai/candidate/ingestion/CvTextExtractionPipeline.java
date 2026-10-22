package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.ocr.CvOcrEngine;
import com.nevgiu.hrai.candidate.ocr.CvOcrException;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Runs inexpensive native extraction first and invokes OCR only when the result is too small to trust. */
@Service
public class CvTextExtractionPipeline {
    private final CvTextExtractor nativeExtractor;
    private final CvOcrEngine ocrEngine;
    private final CvIngestionProperties ingestionProperties;
    private final CvOcrProperties ocrProperties;

    public CvTextExtractionPipeline(CvTextExtractor nativeExtractor, CvOcrEngine ocrEngine,
                                    CvIngestionProperties ingestionProperties, CvOcrProperties ocrProperties) {
        this.nativeExtractor = nativeExtractor;
        this.ocrEngine = ocrEngine;
        this.ingestionProperties = ingestionProperties;
        this.ocrProperties = ocrProperties;
    }

    public CvTextExtractionResult extract(byte[] content) throws IOException {
        String nativeText = nativeExtractor.extract(content);
        if (nativeText.length() >= ingestionProperties.minimumTextLength() || !ocrProperties.enabled()) {
            return CvTextExtractionResult.nativeText(nativeText);
        }
        try {
            CvOcrResult ocr = ocrEngine.extract(content);
            return new CvTextExtractionResult(
                    ocr.text(), CvExtractionMethod.OCR, ocr.confidence(), ocr.pages(), false);
        } catch (CvOcrException exception) {
            // Preserve the governed original and route it to review instead of losing the import when OCR is unavailable.
            return new CvTextExtractionResult(nativeText, CvExtractionMethod.NATIVE, null, java.util.List.of(), true);
        }
    }
}
