# Governed Original CV Storage

## Purpose

Preserve each newly imported PDF so OCR, correction, reprocessing, and evidence-backed chat can work from the original
source instead of only extracted text. Original CVs contain personal data and are never exposed as static web content.

## Implemented storage foundation

- `OriginalCvStorage` separates ingestion from the storage provider.
- The initial provider writes to a private filesystem root mounted as the Docker volume `cv-originals`.
- Storage keys contain a SHA-256 tenant directory and a random UUID; user filenames and organization names never become
  filesystem paths.
- Writes use a temporary file followed by a move, and a database rollback removes the newly stored object.
- Duplicate content is detected before storage, so a repeated upload does not create another binary.
- `cv_documents` records the opaque storage key, storage timestamp, and retention deadline.
- Existing documents remain valid with null storage metadata because their original bytes were not previously retained.
- Original files are not static web content. Authenticated access goes through tenant-scoped application APIs.

## Authorized original-file operations

The application exposes two session-authenticated endpoints:

- `GET /api/cv-documents/{documentId}/original`: `ADMIN` and `RECRUITER` may download an original belonging to their
  organization. The response is an attachment with `Cache-Control: no-store`.
- `DELETE /api/cv-documents/{documentId}/original`: only `ADMIN` may delete the stored original within their
  organization. This clears the document's storage metadata but intentionally does not erase the candidate, extracted
  text, evaluations, or document record; full candidate erasure remains a separate governed workflow.

The organization always comes from the authenticated server session. Repository lookup combines the document ID and
organization ID, and a missing, unstored, or cross-organization ID returns the same `404` response. The API never
accepts an organization ID from the browser.

Deletion uses a recoverable two-step operation because PostgreSQL and the filesystem cannot share one atomic
transaction. The file first moves to a private `_deletion` namespace. A database commit purges it; a database rollback
restores it to its original opaque key. A purge failure leaves an unreferenced staged file for safe operational
reconciliation instead of making committed database metadata point to missing bytes.

Successful downloads, successful deletions, denied lookups, and storage failures create security-audit events using
the safe document ID. Audit details and general logs exclude filenames, storage keys, hashes, and CV content.

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

### Authorized-operation `curl` acceptance

Use only a disposable CV and run these commands from the staging deployment host. `jq` is required. Never place a
password directly in a command or retain a downloaded CV as shared validation evidence.

Select the latest stored disposable document without printing candidate data or the storage key:

```bash
cd /opt/nevgiu/deploy
api_url="https://staging-api.hr.nevgiuai.com/api"

document_id="$(
  docker compose --env-file .env --env-file .images.env \
    exec -T db sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
      "SELECT id FROM cv_documents
       WHERE storage_key IS NOT NULL
       ORDER BY id DESC
       LIMIT 1"' |
  tr -d '[:space:]'
)"

case "$document_id" in
  ''|*[!0-9]*) echo "A numeric disposable document ID is required" >&2; exit 1 ;;
esac
```

Create a separate cookie jar for the role under test and capture credentials interactively. The values are entered only
after each prompt; `account_email` and `account_password` are variable names and must not be replaced in the `read`
commands:

```bash
cookie_jar="$(mktemp)"
read -r -p "Account email: " account_email
read -r -s -p "Account password: " account_password
echo

csrf_token="$(
  curl --fail-with-body --silent --show-error \
    --cookie-jar "$cookie_jar" \
    --cookie "$cookie_jar" \
    "$api_url/auth/csrf" |
  jq -r '.token'
)"

login_body="$(
  printf '%s\n%s\n' "$account_email" "$account_password" |
  jq -Rn '[inputs] | {email: .[0], password: .[1]}'
)"

curl --fail-with-body --silent --show-error \
  --cookie-jar "$cookie_jar" \
  --cookie "$cookie_jar" \
  --header 'Content-Type: application/json' \
  --header "X-XSRF-TOKEN: $csrf_token" \
  --data-binary @- \
  "$api_url/auth/login" \
  <<< "$login_body" |
jq

unset account_password login_body
```

For an `ADMIN` or `RECRUITER`, download the original and verify the status, cache policy, and file type without printing
its content:

```bash
download_headers="$(mktemp)"
download_file="$(mktemp --suffix=.pdf)"

curl --silent --show-error \
  --cookie "$cookie_jar" \
  --dump-header "$download_headers" \
  --output "$download_file" \
  --write-out 'HTTP %{http_code}\n' \
  "$api_url/cv-documents/$document_id/original"

grep -Ei '^(content-type|content-disposition|cache-control):' "$download_headers"
file "$download_file"
```

The expected status is `200`; the response is a PDF attachment with `Cache-Control: no-store`. Repeat the session setup
with an independent `READ_ONLY` account and confirm the same request returns `403`. A missing identifier must return
`404`:

```bash
curl --silent --show-error \
  --cookie "$cookie_jar" \
  --output /dev/null \
  --write-out 'HTTP %{http_code}\n' \
  "$api_url/cv-documents/999999999/original"
```

If a controlled second staging organization exists, use one of its disposable document IDs while authenticated to the
first organization and confirm the same `404`. Do not create or alter real cross-organization candidate data solely for
this test.

Before each deletion attempt, refresh the CSRF token in that account's session:

```bash
csrf_token="$(
  curl --fail-with-body --silent --show-error \
    --cookie-jar "$cookie_jar" \
    --cookie "$cookie_jar" \
    "$api_url/auth/csrf" |
  jq -r '.token'
)"

curl --silent --show-error \
  --request DELETE \
  --cookie "$cookie_jar" \
  --header "X-XSRF-TOKEN: $csrf_token" \
  --output /dev/null \
  --write-out 'HTTP %{http_code}\n' \
  "$api_url/cv-documents/$document_id/original"
```

The expected status is `403` for `RECRUITER` and `READ_ONLY`, and `204` for `ADMIN`. A practical order is recruiter,
read-only, then administrator, using a new cookie jar and the login block for each account. Run the successful
administrator deletion last. With the administrator cookie, confirm a subsequent download returns `404`:

```bash
curl --silent --show-error \
  --cookie "$cookie_jar" \
  --output /dev/null \
  --write-out 'HTTP %{http_code}\n' \
  "$api_url/cv-documents/$document_id/original"
```

After the successful deletion, verify only non-sensitive database state. The numeric check above prevents the shell from
placing arbitrary text into this diagnostic SQL:

```bash
docker compose --env-file .env --env-file .images.env \
  exec -T db sh -c \
  "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"
     SELECT id,
            candidate_id,
            storage_key IS NOT NULL AS original_stored,
            stored_at,
            retention_until
     FROM cv_documents
     WHERE id = $document_id
   \""
```

The document and `candidate_id` remain, `original_stored` is false, and the storage timestamps are null. Check the
administrator-only audit API without printing unrelated history:

```bash
curl --fail-with-body --silent --show-error \
  --cookie "$cookie_jar" \
  "$api_url/admin/security-events?page=0&size=100" |
jq --arg document_id "$document_id" \
  '.content[]
   | select((.details // "") | contains("documentId=" + $document_id))
   | {eventType, outcome, details, createdAt}'
```

Confirm access, deletion, and denied outcomes contain only the safe document ID. Finally, inspect operational errors and
remove all local test artifacts:

```bash
docker compose --env-file .env --env-file .images.env \
  logs --tail=150 backend

rm -f "$cookie_jar" "$download_headers" "$download_file"
unset account_email csrf_token document_id
```

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

**Accepted in production 12 October 2026 for the malware-quarantine release:** matched PostgreSQL and `cv-originals`
backups were created and their checksums verified before deployment. ClamAV and the application became healthy, normal
PDF ingestion continued to work, and the harmless EICAR PDF attachment fixture was rejected with the generic
security-validation response. Existing authentication, tenant-scoped jobs, candidates, and evaluations passed smoke
testing. The scanner-unavailable test was not repeated by deliberately stopping the production scanner because that
fail-closed and recovery path had already passed in staging.

**Accepted in staging 14 October 2026 for main revision `b3d2adf`:** the authorized original-file API was exercised
directly with `curl` because its candidate-details UI is not implemented yet. Each role used its own authenticated
session cookie obtained through `/api/auth/csrf` and `/api/auth/login`; mutating requests supplied the returned
`X-XSRF-TOKEN`. `ADMIN` and `RECRUITER` received `200` when downloading an organization-owned disposable original,
while `READ_ONLY` received `403`. `RECRUITER` received `403` when attempting deletion and `ADMIN` received `204`.
Missing and cross-organization document identifiers returned the same generic `404` without exposing storage state.
The successful download was an attachment with `Cache-Control: no-store` and a valid PDF body.

After administrator deletion, a repeated download returned `404`. Database inspection confirmed that the document and
candidate relationship remained, while `storage_key`, `stored_at`, and `retention_until` were cleared; candidate and
evaluation data were therefore not erased by the original-only operation. The administrator security-event history
contained access, deletion, and denied events with the safe document ID and without filenames, storage keys, hashes, or
CV content. Recent backend logs contained no staged-file purge or rollback-restoration failure. Test credentials and
downloaded disposable files were not retained as validation evidence.

**Accepted in production 14 October 2026 for the authorized original-file release:** matched PostgreSQL and
`cv-originals` backups were created with deployment-account ownership, both archive formats were validated, and their
SHA-256 checksums passed before deployment. The production deployment completed successfully and the authorized
original-file operations passed their smoke tests. `ADMIN` and `RECRUITER` could download an organization-owned
original, `READ_ONLY` was denied, only `ADMIN` could delete the stored original, and unavailable document identifiers
returned the generic not-found response. Deleting the original preserved the candidate and evaluation records while
making the binary unavailable. Security-event and backend-log checks completed without exposing or retaining CV
content, filenames, storage keys, credentials, or downloaded test files as validation evidence.

These records validate the implemented storage, malware-quarantine, and authorized original-file operation slices.
They do not complete automatic expiry/deletion, legal-hold enforcement, off-server backup/restore, correction and
reprocessing, or future object-storage controls below.

## Backup and recovery

PostgreSQL backups contain metadata, not original PDFs. Back up the `cv-originals` volume separately with encryption,
restricted access, integrity checks, and the same retention/deletion policy. A database restore without the matching
volume snapshot produces metadata that points to unavailable originals; a volume restore without the matching database
produces unreferenced personal data. Recovery procedures must therefore treat both artifacts as one restore set.

For a temporary manual volume backup, the helper container must set restrictive permissions and return ownership to the
calling deployment account before it exits. Running plain `tar` as container root leaves a root-owned, commonly
world-readable file on the bind-mounted host directory. Use the following pattern instead:

```bash
archive_name="$(basename "$cv_backup")"

docker run --rm \
  --volume "nevgiu-hr-ai_cv-originals:/source:ro" \
  --volume "$backup_dir:/backup" \
  --env "ARCHIVE_NAME=$archive_name" \
  --env "OUTPUT_UID=$(id -u)" \
  --env "OUTPUT_GID=$(id -g)" \
  alpine:3.20 \
  sh -c 'set -eu
         umask 077
         tar -C /source -czf "/backup/$ARCHIVE_NAME" .
         chown "$OUTPUT_UID:$OUTPUT_GID" "/backup/$ARCHIVE_NAME"
         chmod 600 "/backup/$ARCHIVE_NAME"'
```

Always verify the result before checksumming or transferring it:

```bash
test -s "$cv_backup"
test "$(stat -c '%U:%G %a' "$cv_backup")" = "$(id -un):$(id -gn) 600"
tar -tzf "$cv_backup" > /dev/null
```

## Remaining governance work

- Add the candidate-details/ingestion-history UI that invokes the authorized original-file APIs.
- Add auditable metadata correction and reprocessing without changing the submitted original or duplicating candidates.
- Implement scheduled expiry that deletes the binary and updates or deletes its database record safely.
- Implement legal-hold enforcement around deletion and expiry.
- Add encrypted off-server backup and an isolated restore test for database plus original files.
- Replace the local provider with private object storage before horizontal or multi-host backend scaling.
