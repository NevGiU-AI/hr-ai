# CV OCR fallback

## Purpose

Scanned and low-text PDFs must not silently produce empty or misleading candidate records. The backend therefore keeps
PDFBox native extraction as the inexpensive first path and invokes local Tesseract OCR only when native text is below
the configured quality threshold. CV bytes remain inside the application deployment.

## Processing flow

1. Validate, quarantine, and scan the uploaded PDF with ClamAV.
2. Promote the clean original into governed storage.
3. Attempt native PDFBox text extraction.
4. If native text meets `app.cv-ingestion.minimum-text-length`, persist it as `NATIVE` without running OCR.
5. Otherwise render at most the configured number of PDF pages as grayscale images and invoke Tesseract separately for
   each page with a strict timeout.
6. Persist combined OCR text, mean confidence, and page-level text/confidence provenance.
7. Create a candidate only when the result has enough text and meets the confidence threshold. Otherwise retain the
   document as `NEEDS_REVIEW` without presenting uncertain output as reliable candidate data.
8. If Tesseract is unavailable, retain the governed document for review rather than losing the import.

The existing reprocessing endpoint uses the same pipeline after storage retrieval and a fresh ClamAV scan. It preserves
human-corrected metadata and replaces page provenance only after extraction succeeds inside the database transaction.

## Configuration

The backend image contains Tesseract language packs for English, French, and Portuguese. Deployment defaults enable OCR
with `eng+fra+por`; direct non-container backend launches keep OCR disabled unless explicitly enabled.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `APP_CV_OCR_ENABLED` | `false` | Enables the local OCR fallback. Compose enables it by default. |
| `APP_CV_OCR_COMMAND` | `tesseract` | Executable name or absolute path. |
| `APP_CV_OCR_LANGUAGES` | `eng+fra+por` | Installed Tesseract language combination. |
| `APP_CV_OCR_DPI` | `300` | Rasterization resolution. |
| `APP_CV_OCR_MAX_PAGES` | `30` | Bounds CPU, memory, and processing time per PDF. |
| `APP_CV_OCR_PAGE_TIMEOUT` | `30s` | Maximum Tesseract time per page. |
| `APP_CV_OCR_MINIMUM_CONFIDENCE` | `70` | Lower mean confidence routes the document to human review. |

Flyway `V5__add_cv_ocr_provenance.sql` adds extraction method/confidence to `cv_documents` and the cascading
`cv_document_pages` table. Page text is personal data: it is not returned by candidate-details APIs and is erased with
its parent document.

## Environment acceptance

Before deployment, validate matched PostgreSQL and `cv-originals` backups. In staging:

1. Confirm Flyway applies `V5` and the backend becomes healthy.
2. Upload a normal text PDF and verify extraction is `NATIVE` with no OCR pages.
3. Upload known scanned English, French, and Portuguese PDFs and verify `OCR`, page count, and confidence are shown.
4. Confirm sufficiently confident OCR creates a candidate; low-confidence or insufficient OCR remains `NEEDS_REVIEW`.
5. Reprocess an OCR document and confirm no duplicate candidate/document is created, human corrections remain, and
   prior evaluations become stale.
6. Temporarily use an invalid OCR command, upload a disposable scanned PDF, and confirm it is retained for review with
   a generic warning. Restore configuration and confirm successful recovery.
7. Confirm original download, deletion, retention, legal hold, and full erasure still include OCR-backed documents.
8. Inspect the database and confirm page provenance belongs only to the document and disappears through cascading
   document/candidate erasure.
9. Review logs and ensure they contain no filenames, OCR text, candidate PII, storage keys, or antivirus signatures.

Repeat the accepted safe smoke tests in production after a validated production backup and protected release.
