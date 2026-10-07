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

## Malware quarantine gate

Every Compose deployment runs a private ClamAV daemon on the internal data network. An accepted upload follows this
sequence:

1. Validate the file type and size and reject tenant-local duplicates.
2. Write the bytes under the isolated `_quarantine` storage namespace.
3. Stream the bytes to ClamAV without exposing the storage path or candidate metadata.
4. Atomically promote a clean file to its opaque tenant-scoped storage key.
5. Only then extract text, create a candidate, or make the original eligible for later processing.

An infected file is deleted from quarantine and rejected with a generic security-validation response. If ClamAV is
unavailable, times out, or returns an unknown response, ingestion fails closed with `503`; the quarantined copy is
deleted and no candidate or document row is created. Scanner details and signatures are not returned to the caller.
Direct non-Compose development can disable scanning, but the versioned local, staging, and production Compose files
enable it explicitly.

### Why a daemon and a socket are used

A daemon is a long-running background service. The ClamAV daemon, `clamd`, loads its malware signatures once, remains
running, and scans successive uploads on request; starting a new antivirus process for every CV would be slower and more
resource-intensive.

`clamd` is not an HTTP/REST server. It listens on TCP port `3310` and implements ClamAV's native protocol. The backend
therefore opens a socket, sends the `INSTREAM` command, streams length-prefixed PDF chunks, sends a zero-length chunk to
finish, and reads a result such as `stream: OK` or `stream: ... FOUND`.

Compose resolves the hostname `clamav` on the internal `data` network. The service has no published host port, so it is
not directly reachable from the public internet. `CvMalwareScanner` keeps this transport detail behind a provider-neutral
interface; a future REST-based or managed scanner can replace `ClamAvCvMalwareScanner` without changing
`CvIngestionService`.

`CV_STORAGE_RETENTION` defaults to `365d`. This release records the deadline but does not automatically delete expired
records; scheduled deletion, legal-hold handling, and administrator workflows remain required before retention is fully
automated.

## Deployment procedure

Before deploying, create and validate the normal PostgreSQL backup. The deployment creates the `cv-originals` named
volume and Flyway applies `V2__add_original_cv_storage_metadata.sql`. Configure the same reviewed retention value in
each private environment file:

```dotenv
CV_STORAGE_RETENTION=365d
CV_MALWARE_CONNECT_TIMEOUT=2s
CV_MALWARE_READ_TIMEOUT=30s
```

After deployment, verify:

```bash
docker compose --env-file .env --env-file .images.env ps
docker volume inspect nevgiu-hr-ai_cv-originals
docker compose --env-file .env --env-file .images.env ps clamav
docker compose --env-file .env --env-file .images.env exec -T clamav clamdscan --ping 3
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

For malware-gate acceptance, never use real malware. Use the harmless
[`eicar-adobe-acrobat-attachment.pdf`](https://github.com/fire1ce/eicar-standard-antivirus-test-files/blob/master/eicar-adobe-acrobat-attachment.pdf)
fixture from the public `fire1ce/eicar-standard-antivirus-test-files` repository in an isolated staging organization.
Review the source before use, download only the required fixture, and delete it after validation. Confirm the upload is
rejected with `422`, candidate/document counts do not change, no file remains below `_quarantine` or normal storage, and
an ordinary disposable PDF still imports. A plain EICAR file is not a PDF, while merely prefixing it with `%PDF-` is not
a reliable embedded-PDF detection test; use the attachment fixture that ClamAV detects through its PDF scanner.

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

**Accepted in staging 8 October 2026 for main revision `cdb09ea`:** PostgreSQL and `cv-originals` backups were created
and checksummed before deployment. ClamAV became healthy with the reviewed timeout configuration. A clean PDF imported,
the EICAR PDF attachment fixture was rejected with the generic security-validation response, and no infected document
was accepted. Stopping ClamAV caused a new non-duplicate PDF upload to fail closed; after restart and health recovery,
normal import resumed. Recent backend and ClamAV logs were inspected without publishing candidate content or secrets.

These records validate the implemented storage slice. They do not approve the outstanding notice/consent, automatic
expiry/deletion, legal-hold, off-server backup/restore, or future object-storage controls below.

## Backup and recovery

PostgreSQL backups contain metadata, not original PDFs. Back up the `cv-originals` volume separately with encryption,
restricted access, integrity checks, and the same retention/deletion policy. A database restore without the matching
volume snapshot produces metadata that points to unavailable originals; a volume restore without the matching database
produces unreferenced personal data. Recovery procedures must therefore treat both artifacts as one restore set.

## Remaining governance work

- Add tenant-authorized read/delete operations and auditable correction/reprocessing.
- Implement scheduled expiry that deletes the binary and updates or deletes its database record safely.
- Implement legal-hold enforcement around deletion and expiry.
- Add encrypted off-server backup and an isolated restore test for database plus original files.
- Replace the local provider with private object storage before horizontal or multi-host backend scaling.
