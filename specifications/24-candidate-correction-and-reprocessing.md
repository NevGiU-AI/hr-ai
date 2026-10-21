# Candidate correction and document reprocessing

## Purpose

Authorized recruiters can correct candidate profile metadata and re-run native PDF text extraction from the already
stored original without changing that original or creating another candidate. This is the correction foundation for
the later OCR workflow.

## Authorization and tenant boundary

- `ADMIN` and `RECRUITER` may correct metadata and reprocess documents in their organization.
- `REVIEWER` and `READ_ONLY` receive `403` for both mutations.
- Missing and cross-organization candidate or document identifiers return the same `404` response.
- The backend derives `organization_id` from the authenticated principal; clients cannot select it.

## Metadata correction

`PUT /api/candidates/{candidateId}/metadata` accepts the complete editable profile: name, email, location, current
title, and years of experience. The candidate row is locked while it is changed. Each successful update creates an
immutable snapshot in `candidate_metadata_revisions` with the actor and timestamp. The candidate-details response and
Angular workspace expose this history only through the same tenant-scoped boundary.

The submitted PDF and extracted CV text are not modified by a metadata correction.

## Reprocessing

`POST /api/cv-documents/{documentId}/reprocess` operates on the existing document and candidate:

1. Lock the tenant-scoped document and candidate rows.
2. Require an available promoted original associated with a candidate.
3. Read and rescan the original with ClamAV. The operation fails closed if scanning is unavailable or infected.
4. Run native PDF text extraction again.
5. Reject low-text output for manual review or future OCR; do not replace useful current text.
6. Update the existing document extraction fields, candidate CV text, and `reprocessed_at` timestamp.
7. Preserve manually corrected profile fields.
8. Mark prior candidate evaluations stale through `invalidated_at`; do not delete their audit history.

No upload, content-hash duplicate check, new candidate, or new document is created during this operation.

## Audit and privacy

Successful corrections emit `CANDIDATE_METADATA_CORRECTED`. Reprocessing emits `CV_DOCUMENT_REPROCESSED` or
`CV_DOCUMENT_REPROCESS_FAILED`. Audit details contain only safe numeric identifiers, status, and counts. They exclude
candidate profile values, CV text, filenames, content hashes, storage keys, and antivirus signatures.

## Staging validation

Before merging, create and validate matched PostgreSQL and `cv-originals` backups. After deployment, confirm Flyway
applied `V4__add_candidate_correction_and_reprocessing.sql`, then test:

1. As `ADMIN`, correct all editable fields and confirm the candidate list/details refresh.
2. Confirm correction history identifies the actor and retains the earlier snapshot after a second correction.
3. Repeat correction and reprocessing as `RECRUITER`.
4. Confirm `REVIEWER` and `READ_ONLY` receive `403` for both mutations.
5. Confirm a cross-tenant candidate or document ID returns `404` and remains unchanged.
6. Create an evaluation, reprocess its document, and verify the evaluation row receives `invalidated_at`.
7. Confirm reprocessing updates `reprocessed_at`, refreshes extracted text, and does not create candidate/document rows.
8. Confirm manually corrected metadata is unchanged after reprocessing.
9. Stop ClamAV and confirm reprocessing fails closed; restart it and confirm recovery.
10. Confirm the three new audit event types appear without PII or storage internals.

Repeat the accepted smoke tests in production after a validated production backup and protected release deployment.
