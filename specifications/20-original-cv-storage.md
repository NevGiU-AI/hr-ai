# Governed Original CV Storage

## Purpose

Preserve each newly imported PDF so OCR, correction, reprocessing, and evidence-backed chat can work from the original
source instead of only extracted text. Original CVs contain personal data and are never exposed as static web content.

## Implemented first slice

- `OriginalCvStorage` separates ingestion from the storage provider.
- The initial provider writes to a private filesystem root mounted as the Docker volume `cv-originals`.
- Storage keys contain a SHA-256 tenant directory and a random UUID; user filenames and organization names never become
  filesystem paths.
- Writes use a temporary file followed by a move, and a database rollback removes the newly stored object.
- Duplicate content is detected before storage, so a repeated upload does not create another binary.
- `cv_documents` records the opaque storage key, storage timestamp, and retention deadline.
- Existing documents remain valid with null storage metadata because their original bytes were not previously retained.
- No public download endpoint exists. Application authorization must guard any future read, correction, or deletion API.

`CV_STORAGE_RETENTION` defaults to `365d`. This release records the deadline but does not automatically delete expired
records; scheduled deletion, legal-hold handling, and administrator workflows remain required before retention is fully
automated.

## Deployment procedure

Before deploying, create and validate the normal PostgreSQL backup. The deployment creates the `cv-originals` named
volume and Flyway applies `V2__add_original_cv_storage_metadata.sql`. Configure the same reviewed retention value in
each private environment file:

```dotenv
CV_STORAGE_RETENTION=365d
```

After deployment, verify:

```bash
docker compose --env-file .env --env-file .images.env ps
docker volume inspect nevgiu-hr-ai_cv-originals
docker compose --env-file .env --env-file .images.env logs --tail=100 backend
```

Upload one disposable PDF, then query only non-sensitive metadata:

```sql
SELECT id, storage_key IS NOT NULL AS original_stored, stored_at, retention_until
FROM cv_documents
ORDER BY id DESC
LIMIT 1;
```

Confirm `original_stored` is true, the deadline matches the configured retention period, a duplicate upload creates no
new row or file, and normal extraction/evaluation still works. Do not print filenames, extracted text, or storage keys
into shared deployment evidence.

## Environment validation record

**Accepted 6 October 2026 for main revision `86ec842`:** a pre-deployment PostgreSQL backup was created, Flyway applied
`V2__add_original_cv_storage_metadata.sql`, the backend passed Hibernate validation, and the private
`cv-originals` volume was created. A disposable PDF produced non-null storage metadata and the configured retention
deadline. Uploading the identical bytes under a different filename returned `DUPLICATE` without creating another
candidate, document row, or stored original. Text extraction and candidate evaluation continued to work.

**Accepted in production 6 October 2026 for main revision `babee7e`:** the release was deployed successfully and the
production smoke tests passed. The private original-file volume was available, a disposable upload produced stored
original metadata, duplicate detection remained content-based when the filename changed, and normal extraction and
evaluation continued to work.

These records validate the implemented storage slice. They do not approve the outstanding notice/consent, automatic
expiry/deletion, legal-hold, malware-quarantine, off-server backup/restore, or future object-storage controls below.

## Backup and recovery

PostgreSQL backups contain metadata, not original PDFs. Back up the `cv-originals` volume separately with encryption,
restricted access, integrity checks, and the same retention/deletion policy. A database restore without the matching
volume snapshot produces metadata that points to unavailable originals; a volume restore without the matching database
produces unreferenced personal data. Recovery procedures must therefore treat both artifacts as one restore set.

## Remaining governance work

- Approve candidate notice/consent, retention, deletion, legal-hold, and data-residency policies.
- Add malware scanning and quarantine before a stored PDF is available to later processing.
- Add tenant-authorized read/delete operations and auditable correction/reprocessing.
- Implement scheduled expiry that deletes the binary and updates or deletes its database record safely.
- Add encrypted off-server backup and an isolated restore test for database plus original files.
- Replace the local provider with private object storage before horizontal or multi-host backend scaling.
