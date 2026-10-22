package com.nevgiu.hrai.candidate;

/** Identifies how the text currently stored for a CV document was obtained. */
public enum CvExtractionMethod {
    /** Text was read directly from the PDF's embedded text layer through PDFBox. */
    NATIVE,
    /** Pages were rendered as images and recognized by the configured OCR engine. */
    OCR
}
