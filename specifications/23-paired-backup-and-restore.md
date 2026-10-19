# Encrypted paired backup and restore

Status: **Implemented in the repository; installation deferred until after the dashboard, OpenAPI, and concurrency benchmark**

## Purpose

PostgreSQL contains candidate and original-file metadata, while the `cv-originals` Docker volume contains the PDF
binaries. Either artifact alone is an incomplete recovery point. This control creates, transfers, validates, and restores
them as one timestamped recovery set.

## Backup contract

`deploy/backup-paired.sh` runs as a root-owned systemd service outside the deployment-writable directory. It:

1. acquires a non-blocking host lock, then verifies the database and backend are healthy and the helper image and volume already exist;
2. stops the backend for a short maintenance window so no application write can separate database metadata from files;
3. streams `pg_dump` and a read-only volume archive directly through `age`, without writing plaintext artifacts;
4. restarts the backend on success or failure and requires it to become healthy within approximately two minutes;
5. computes SHA-256 digests for both encrypted artifacts;
6. uploads both artifacts under one Object Storage prefix; and
7. uploads `manifest.txt` last, making its presence the marker of a complete recovery set.

A prefix without `manifest.txt` is incomplete and must never be restored. The upload-only VPS identity cannot read,
delete, or change retention. The private `age` identity and passphrase stay offline.

The volume archive is streamed from a temporary Alpine container instead of being created through a host bind mount.
This avoids the previously observed root-owned archive problem and keeps plaintext original PDFs off the VPS filesystem.

## Isolated restore contract

`deploy/restore-paired.sh` is for a controlled recovery host, not routine application startup. It requires the downloaded
directory, offline recovery identity, a new database name, a new Docker volume name, and the explicit confirmation value
`RESTORE_TO_ISOLATED_TARGETS`. It then:

1. validates the manifest format, artifact names, and both encrypted SHA-256 digests;
2. refuses the live PostgreSQL database and live `cv-originals` volume;
3. decrypts into a restricted temporary directory;
4. validates the dump and archive formats;
5. creates and restores a new database and new Docker volume; and
6. removes all temporary plaintext on exit.

The script deliberately does not delete rehearsal targets. An operator first validates tenant-scoped row counts, file
references, representative downloads, and deletion replay; removal is a separate explicit cleanup decision.

## Installation and acceptance sequence

This sequence is intentionally scheduled after candidate details/history, correction/reprocessing, OCR, CV chat, speech,
the final dashboard, publication of stable OpenAPI contracts, and the bounded-concurrency/virtual-thread benchmark.
Until then, continue using the documented manual matched pre-deployment backups. Do not enable the legacy database-only
timer as a substitute for the paired service.

1. Provision environment-isolated destinations, upload-only credentials, public `age` recipients, and lifecycle rules
   for staging and production; never reuse credentials or recovery identities across environments.
2. Install the backup script and systemd units as root-owned files in staging first; keep the restore script and private
   recovery identity off the application VPS.
3. Run `docker pull alpine:3.20` before the first maintenance window and confirm `docker image inspect alpine:3.20` succeeds.
4. Run the staging service manually and confirm the backend returns healthy.
5. Confirm the remote staging set contains two non-empty `.age` artifacts and `manifest.txt`.
6. Download one staging set on an isolated recovery host, restore into uniquely named targets, and validate business
   counts, tenant boundaries, original-file references, representative downloads, and deletion replay.
7. After staging acceptance, install the same service in production with production-only configuration and repeat the
   manual upload, Object Lock, isolated restore, and application-health checks.
8. Record both acceptance results and explicitly remove rehearsal data.
9. Enable each environment's timer only after that environment passes every preceding check.

## Remaining acceptance evidence

- [ ] Staging destination and environment-isolated credentials configured.
- [ ] Manual staging paired upload and isolated restore completed.
- [ ] Manual production paired upload completed.
- [ ] Object Lock confirmed on both encrypted artifacts.
- [ ] Isolated database and volume restore completed.
- [ ] Business counts, tenant boundaries, and representative original files validated.
- [ ] Deletion replay after restoration validated.
- [ ] Timer enabled and its first scheduled recovery set verified.
- [ ] Backup success, age, and failure monitoring configured.
