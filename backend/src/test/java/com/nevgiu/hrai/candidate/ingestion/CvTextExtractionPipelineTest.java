package com.nevgiu.hrai.candidate.ingestion;

import com.nevgiu.hrai.candidate.CvExtractionMethod;
import com.nevgiu.hrai.candidate.ocr.CvOcrEngine;
import com.nevgiu.hrai.candidate.ocr.CvOcrException;
import com.nevgiu.hrai.candidate.ocr.CvOcrProperties;
import com.nevgiu.hrai.candidate.ocr.CvOcrResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvTextExtractionPipelineTest {
    private final byte[] pdf = "%PDF-1.4".getBytes();
    private final CvOcrEngine ocr = mock(CvOcrEngine.class);

    @Test
    void keepsSufficientNativeTextWithoutInvokingOcr() throws Exception {
        CvTextExtractionPipeline pipeline = pipeline(content -> "Native text that is long enough", true, 10);

        CvTextExtractionResult result = pipeline.extract(pdf);

        assertThat(result.method()).isEqualTo(CvExtractionMethod.NATIVE);
        assertThat(result.ocrAttemptFailed()).isFalse();
        verify(ocr, never()).extract(pdf);
    }

    @Test
    void usesOcrForLowTextPdfAndPreservesPageProvenance() throws Exception {
        CvOcrResult ocrResult = new CvOcrResult("OCR candidate text", 88.5,
                List.of(new CvOcrResult.Page(1, "OCR candidate text", 88.5)));
        when(ocr.extract(pdf)).thenReturn(ocrResult);
        CvTextExtractionPipeline pipeline = pipeline(content -> "", true, 10);

        CvTextExtractionResult result = pipeline.extract(pdf);

        assertThat(result.method()).isEqualTo(CvExtractionMethod.OCR);
        assertThat(result.confidence()).isEqualTo(88.5);
        assertThat(result.pages()).singleElement().extracting(CvOcrResult.Page::pageNumber).isEqualTo(1);
    }

    @Test
    void preservesNativeResultForReviewWhenOcrIsUnavailable() throws Exception {
        when(ocr.extract(pdf)).thenThrow(new CvOcrException("unavailable"));
        CvTextExtractionPipeline pipeline = pipeline(content -> "scan", true, 10);

        CvTextExtractionResult result = pipeline.extract(pdf);

        assertThat(result.text()).isEqualTo("scan");
        assertThat(result.method()).isEqualTo(CvExtractionMethod.NATIVE);
        assertThat(result.ocrAttemptFailed()).isTrue();
    }

    private CvTextExtractionPipeline pipeline(CvTextExtractor extractor, boolean enabled, int minimumLength) {
        CvIngestionProperties ingestion = new CvIngestionProperties(
                1_000_000, 2_000_000, 10, 2_000_000, 100, minimumLength, "initial.zip", false);
        CvOcrProperties properties = new CvOcrProperties(
                enabled, "tesseract", "eng+fra+por", 300, 30, Duration.ofSeconds(30), 70);
        return new CvTextExtractionPipeline(extractor, ocr, ingestion, properties);
    }
}
