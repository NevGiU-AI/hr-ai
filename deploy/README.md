# VPS deployment bundle

This directory contains the provider-neutral Docker Compose deployment used by staging and production.

Use the environment-specific runbook for initial OVHcloud provisioning, SSH hardening, Squarespace DNS, credentials, backups, troubleshooting, and readiness checks:

- Staging: `specifications/09-staging-vps-provisioning-runbook.md`
- Production: `specifications/10-production-vps-provisioning-runbook.md`

Never copy an environment file, SSH key, API key, database password, backup credential, or GitHub environment secret from one procedure to the other.

Paired recovery tooling is implemented in `backup-paired.sh`, `restore-paired.sh`, and the accompanying units in
`systemd/`, but installation in staging and production is deliberately deferred until after the dashboard, stable
OpenAPI contracts, and concurrency benchmark. Until then, use the runbooks' manual matched pre-deployment backups. When activated, install the backup script root-owned as
`/usr/local/sbin/nevgiu-paired-backup`; never execute a root service from a deployment-writable path. The service briefly
stops the backend, streams a custom-format `pg_dump` and matching `cv-originals` archive directly into `age`, restarts
and health-checks the backend, uploads both encrypted artifacts, and uploads `manifest.txt` last as the recovery-set
completion marker. The private recovery identity must never exist on either application VPS.

The paired timer definition is tracked but must not be installed or enabled before its scheduled milestone. After the
dashboard, OpenAPI, and concurrency work, install it in staging first and enable it only after a manual paired upload,
Object Lock verification, and an isolated restore succeed. Production follows the same acceptance sequence only after
staging. Its daily schedule is 02:30 UTC with up to thirty minutes of randomized delay and persistent catch-up after
downtime.

Object Storage lifecycle must retain current objects under `recovery-sets/` for the approved period, remove incomplete multipart uploads after one day, and clean eligible non-current versions and expired delete markers. Seven-day Governance Object Lock always takes precedence over lifecycle deletion. Apply or change lifecycle policy only with the offline bucket-owner profile, never with credentials installed on the VPS.

`deploy/compose.yml` is the only production-capable Compose definition. The root `docker-compose.yml` is exclusively for local development. There is intentionally no `docker-compose.prod.yml`; maintaining a second production definition would allow security, networking, health checks, and rollback behavior to drift from the deployment tested by CI/CD.

## Domain mapping

| Environment | Frontend | Backend API |
| --- | --- | --- |
| Staging | `staging-hr.nevgiuai.com` | `staging-api.hr.nevgiuai.com` |
| Production | `hr.nevgiuai.com` | `api.hr.nevgiuai.com` |

## Caddy's role

Caddy is the public HTTPS gateway for the Compose stack:

```text
Internet
  |-- frontend hostname --> Caddy --> frontend Nginx --> Angular files
  `-- API hostname -------> Caddy --> Spring Boot backend
```

Caddy is the only service that publishes host ports `80` and `443`. It redirects HTTP to HTTPS, obtains and renews TLS certificates, selects the destination from the requested hostname, and proxies traffic over the private Docker `edge` network. This keeps the backend's port `8080` and the frontend container's port `80` off the public host interface.

Frontend Nginx remains necessary: it serves the compiled Angular files and provides single-page-application route fallback. Caddy operates above it and handles public domains, certificates, and routing. Caddy does not execute Angular or backend business logic, store PostgreSQL data, authenticate application users, or perform deployment rollback.

Caddy is replaceable by a correctly configured reverse proxy or managed load balancer such as Nginx, Traefik, or HAProxy. Removing it without replacing its responsibilities risks unencrypted CV and credential traffic, expired certificates, direct backend exposure, missing HTTP-to-HTTPS redirects, incorrect frontend/API routing, and inconsistent security policy. Any replacement must provide automated or operationally reliable TLS renewal, hostname routing, private upstream networking, redirects, security policy, and external health verification.

## Security properties

- Only Caddy publishes host ports.
- PostgreSQL is reachable only on the internal `data` network.
- The backend is reachable externally only through Caddy.
- Caddy obtains and renews HTTPS certificates automatically.
- Database, Caddy certificate, and Caddy configuration data use named volumes.
- Frontend and backend deployments use environment-provided image references.
- The frontend API URL is injected when its container starts, allowing the same image digest to run in staging and production.
- Backend CORS trusts only the configured frontend URL.
- The built-in CV import can be disabled per environment and must be disabled in production.

## Environment file

Copy `.env.example` to `.env` on the target server and replace every placeholder. Never commit the real `.env` file.

Production must use:

```env
FRONTEND_HOST=hr.nevgiuai.com
API_HOST=api.hr.nevgiuai.com
FRONTEND_URL=https://hr.nevgiuai.com
INITIAL_IMPORT_ENABLED=false
BOOTSTRAP_ADMIN_EMAIL=<initial administrator email>
BOOTSTRAP_ADMIN_PASSWORD=<random password of at least 12 characters>
BOOTSTRAP_ADMIN_ORGANIZATION=default
SESSION_TIMEOUT=30m
SECURITY_MAXIMUM_SESSIONS=3
CV_SEMANTIC_INDEX_ENABLED=true
CV_CHAT_SEMANTIC_RETRIEVAL_ENABLED=true
CV_CHAT_SEMANTIC_MAX_MATCHES=6
CV_CHAT_SEMANTIC_MIN_SIMILARITY=0.35
```

Use immutable GHCR image digests once image publication is implemented:

```env
BACKEND_IMAGE=ghcr.io/<owner>/<repository>/hr-ai-backend@sha256:<digest>
FRONTEND_IMAGE=ghcr.io/<owner>/<repository>/hr-ai-frontend@sha256:<digest>
```

`API_URL` is generated from `API_HOST` when the frontend container starts. The frontend image is therefore identical in staging and production.

The bootstrap administrator variables are required for the first secured startup only. Confirm login, remove `BOOTSTRAP_ADMIN_PASSWORD` from `.env`, and recreate the backend. The bcrypt hash and account remain in PostgreSQL; never commit or print the bootstrap password.

`SECURITY_MAXIMUM_SESSIONS` limits each account across the shared Redis session store. The default is `3`; a successful
login that would exceed it removes the oldest-created session. Use the same reviewed value in staging and
production unless a documented environment-specific policy requires otherwise.

The repository example keeps semantic indexing and retrieval disabled as safe dark-launch values for a completely new
environment. The existing staging and production environments passed indexing lifecycle and single-candidate retrieval
acceptance and therefore explicitly use the four semantic values shown above. Indexing controls chunk creation and
reconciliation; retrieval independently controls whether candidate chat queries those chunks. Changing the embedding
model, chunking policy, supported languages, or representative CV corpus requires renewed quality calibration before
changing the accepted `0.35` similarity threshold.

`ACME_EMAIL` is the monitored operational contact Caddy passes to the ACME certificate authority when registering the account used to obtain and renew HTTPS certificates. It may receive certificate expiration, renewal-failure, policy, or account-recovery notices. It is not an application login, SMTP setting, OpenAI credential, or frontend value, and it is not normally embedded in the public certificate.

```text
deploy/.env
    |
    v
deploy/compose.yml
    |
    v
Caddy container environment
    |
    v
Caddyfile: email {$ACME_EMAIL}
    |
    v
ACME certificate authority
```

Staging and production may use the same monitored address because it identifies the certificate administrator rather than an environment credential. Configure it as `ACME_EMAIL=<monitored operational email>` and prefer a shared role mailbox over a personal address. Although the address itself is not a secret, keep the concrete value in each VPS `.env` rather than in repository examples, and keep the completed file private because it also contains database and OpenAI credentials.

## GitHub environment configuration

Create separate `staging` and `production` environments in the repository settings.

Each environment requires these variables:

```text
DEPLOY_HOST
DEPLOY_USER
```

Each environment requires these secrets:

```text
DEPLOY_SSH_KEY
DEPLOY_KNOWN_HOSTS
GHCR_USERNAME
GHCR_TOKEN
```

- `DEPLOY_SSH_KEY` is the private key for that environment's dedicated `deploy` account.
- `DEPLOY_KNOWN_HOSTS` contains the trusted SSH host-key line for that VPS. Do not disable host-key checking.
- `GHCR_USERNAME` is the GitHub account permitted to pull the repository's packages.
- `GHCR_TOKEN` is a dedicated token with the minimum package-read permission required by the VPS.

Validated staging values:

```text
Environment: staging
DEPLOY_HOST=141.94.33.197
DEPLOY_USER=deploy
Environment URL=https://staging-hr.nevgiuai.com
```

Create `GHCR_TOKEN` as a classic personal access token with only `read:packages`. If the organization enforces SSO, authorize the token for the organization. Store the token immediately because GitHub displays it only once. `GHCR_USERNAME` must be the personal or bot account that created that token, not the organization name.

Populate `DEPLOY_KNOWN_HOSTS` from a host key already verified during an interactive SSH connection. For example, from Windows PowerShell:

```powershell
ssh-keygen -F 141.94.33.197 -f "$env:USERPROFILE\.ssh\known_hosts" |
  Where-Object { $_ -notmatch '^#' } |
  Set-Clipboard
```

Do not generate this value with an unverified network lookup and do not disable SSH host-key checking.

Protect the `production` environment with required reviewers and release-tag restrictions where the repository visibility and GitHub plan support them.

The real application secrets (`OPENAI_API_KEY` and `POSTGRES_PASSWORD`) remain only in `/opt/nevgiu/deploy/.env` on each VPS. They are not GitHub Actions secrets because the deployment workflow does not transmit or rewrite them.

## Validate and start

From the deployment directory:

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env pull
docker compose --env-file .env up -d
docker compose --env-file .env ps
```

For the first manual staging deployment, build the local tags defined in `.env` before starting Compose.

Automated deployments write immutable image references to `.images.env` and invoke `deploy.sh`. The script validates the candidate Compose model, pulls both candidate application images before changing the active manifest, waits for database, backend, and frontend health, requires Caddy to be running, and restores the previously running application images if startup or internal validation fails. It does not roll back database contents.

## Flyway database migrations

Flyway migrations are packaged inside the backend image under `db/migration` and run before Hibernate starts.
Hibernate uses `ddl-auto=validate`; it must never create or update staging or production tables. Never edit an applied
migration. Add a new, higher version and rehearse it against a restored production backup.

Existing databases created by Hibernate require a one-time baseline. After taking and validating a backup, set
`FLYWAY_BASELINE_ON_MIGRATE=true`, deploy the Flyway-enabled backend, and confirm `flyway_schema_history` contains a
version `0` baseline followed by successful version `1`. Then set the flag back to `false` and recreate the backend.
New empty databases do not require the flag. Do not delete or manually rewrite `flyway_schema_history`.

Before running Compose manually, clear image variables inherited by the current shell and inspect the resolved image
references:

```bash
unset BACKEND_IMAGE FRONTEND_IMAGE
docker compose --env-file .env --env-file .images.env config --images
```

Exported shell variables take precedence over `.images.env`. Continue only when the output contains the intended full
GHCR commit-SHA references; otherwise Compose can silently recreate a healthy but stale local application image.

The first automated deployment creates `.images.env` before inspecting the manually deployed containers. This preserves the manual images as rollback candidates while allowing Compose to resolve its required image variables.

Current rollback boundary:

- Before replacement, the script records the backend and frontend image references used by the running containers.
- It waits up to 36 attempts at five-second intervals, approximately three minutes, for the database, backend, and frontend to become healthy and for Caddy to be running.
- The previous image references are retained in `.images.env.previous`. `deploy.sh --rollback` restores that manifest, recreates the stack, and validates internal health.
- If initial Compose startup fails or internal validation times out, the script invokes that rollback path. The workflow remains failed even when rollback succeeds.
- If a GitHub public HTTPS smoke test fails, the staging and production workflows invoke `deploy.sh --rollback` and then verify both public endpoints against the restored deployment. The original smoke-test failure keeps the workflow failed.
- PostgreSQL data and schema, `.env`, Compose and Caddy configuration, deployment files, and secrets are not rolled back.
- Candidate Compose validation and application-image pulls occur before the active image manifest changes, so their failure leaves the running deployment unchanged.
- Rollback assumes the previous images remain available in the local Docker cache or registry.

Configuration changes still require versioned backup and restoration. Flyway migrations are forward-only and are not
reversed by application-image rollback, so every migration must remain compatible with the previous application image
or have a separate reviewed recovery procedure. Exercise the image rollback path on staging before production.

### Flyway rollback and recovery

Reverting Java code or restoring a previous backend image does not undo an applied Flyway migration. Flyway records the
migration in `flyway_schema_history`, and the database remains at that schema version. The previous backend image must
therefore be compatible with the expanded schema and must still package every migration already recorded by the
database; otherwise Flyway validation can reject startup because an applied migration is missing locally.

Use this decision order when a release containing a migration fails:

1. If the migration is additive and backward-compatible, restore the previous application images and leave the schema
   and `flyway_schema_history` unchanged.
2. If the new schema or data needs correction, deploy a reviewed higher-version migration, for example V5 correcting
   V4. Do not edit or remove V4 after it has reached a shared environment.
3. If the migration caused destructive corruption that cannot safely be corrected forward, stop backend writes,
   preserve the failed database for investigation, explicitly approve recovery, restore the validated pre-deployment
   PostgreSQL backup, and deploy the matching pre-migration application release.
4. If the failed release also changed original CV files or their lifecycle metadata, restore the PostgreSQL dump and
   its matching `cv-originals` archive as one recovery set. Restoring only one side can leave database metadata and
   stored binaries inconsistent.

Schema reversal through a higher migration can itself be destructive. Dropping a table or column loses the data it
contains, so such a migration requires an explicit data-impact review, a validated backup, and a staging rehearsal.
Prefer expand-and-contract migrations: add compatible schema first, deploy code that can use it, and remove obsolete
schema only in a later release after the rollback-compatibility window has closed.

Never simulate rollback by deleting rows from `flyway_schema_history`, changing recorded checksums, or editing an
already-applied migration. `flyway repair` repairs migration-history metadata; it does not reverse schema or data
changes. Automatic deployment rollback intentionally never restores PostgreSQL because restoration is destructive and
requires an incident-specific decision.

Flyway does offer optional Undo migrations in its Teams edition, but they require a matching `U` script and Redgate
warns that destructive data changes are difficult to reconstruct. This project uses versioned `V` migrations through
`flyway-core`, has no Undo scripts, and does not use `flyway undo`; the reviewed forward-correction or backup-restoration
procedure above remains the supported recovery path.

Authoritative references:

- [Redgate Flyway: versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html)
  confirms that versioned migrations run once, are tracked with checksums, should not be changed after reaching a
  permanent downstream environment, and should normally be corrected with a new versioned migration.
- [Redgate Flyway: schema history table](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table)
  documents applied states, checksums, validation, and the behavior when an applied migration can no longer be found.
- [Redgate Flyway: repair](https://documentation.red-gate.com/flyway/reference/commands/repair) defines `repair` as a
  schema-history operation and notes that database objects left by a failed migration still require separate cleanup.
- [Redgate Flyway: Undo migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/undo-migrations)
  documents the Teams-only matching-script model and its limitations for destructive schema and data changes.
- [PostgreSQL: `pg_dump`](https://www.postgresql.org/docs/current/app-pgdump.html) and
  [PostgreSQL: `pg_restore`](https://www.postgresql.org/docs/current/app-pgrestore.html) define the supported archive
  and reconstruction mechanism used by this project's database recovery procedure.

The requirement to restore PostgreSQL and `cv-originals` as a matched set is a project-specific consistency policy:
Flyway and PostgreSQL document database migration and restoration, but they do not know about the separately stored CV
binaries referenced by application database rows.

## Automatic staging flow

1. A push or pull request runs `CI`.
2. A successful `main` CI run starts `Deploy staging` through `workflow_run`.
3. The workflow builds backend and frontend images once and publishes commit-SHA tags under the current `github.repository` GHCR namespace.
4. GitHub copies only the deployment bundle to the VPS and authenticates the VPS to GHCR with its read-only token.
5. `deploy.sh` pulls the immutable images, starts the stack, and waits for health.
6. GitHub smoke-tests both public HTTPS endpoints. Failure invokes application-image rollback and verifies the restored public endpoints while leaving the workflow failed.
7. Successful images receive `staging-validated-<commit-sha>` aliases for later production promotion.

CI also supports `workflow_dispatch`, which makes a **Run workflow** button available without requiring a placeholder source commit.

## Verify

```bash
curl --fail https://staging-api.hr.nevgiuai.com/actuator/health
curl --fail --head https://staging-hr.nevgiuai.com/
```

## Original-CV volume operations

New non-duplicate PDF imports are stored in the private `cv-originals` volume. Confirm the configured retention period
in the private environment file and inspect the volume without printing application secrets:

```bash
grep '^CV_STORAGE_RETENTION=' .env
grep '^CV_RETENTION_CLEANUP_' .env
docker volume inspect nevgiu-hr-ai_cv-originals
docker compose --env-file .env --env-file .images.env ps clamav
docker compose --env-file .env --env-file .images.env exec -T clamav clamdscan --ping 3
```

The backend fails closed when ClamAV is unavailable: an upload remains isolated only long enough to be scanned, is
deleted on a scanner error or malware result, and is promoted to normal private storage only after a clean verdict.
`CV_MALWARE_CONNECT_TIMEOUT` and `CV_MALWARE_READ_TIMEOUT` may be tuned in `.env`; do not disable scanning in staging
or production. The first ClamAV startup can take longer while signatures are initialized, so wait for the service to
be healthy before diagnosing the backend as unavailable.

Retention cleanup is controlled by `CV_RETENTION_CLEANUP_ENABLED`, `CV_RETENTION_CLEANUP_BATCH_SIZE`, and
`CV_RETENTION_CLEANUP_CRON`. The scheduler removes only expired original binaries and their storage metadata. It skips
legal holds and emits tenant-scoped audit events; full candidate erasure is a separate administrator workflow.

### Validate malware rejection in staging

Never use real malware. Use the harmless
[`eicar-adobe-acrobat-attachment.pdf`](https://github.com/fire1ce/eicar-standard-antivirus-test-files/blob/master/eicar-adobe-acrobat-attachment.pdf)
fixture from the public `fire1ce/eicar-standard-antivirus-test-files` repository. Review the source before use, download
the individual PDF rather than cloning unrelated fixtures, use it only in an isolated staging organization, and delete
the local copy after validation. Endpoint protection may quarantine the fixture because that is its intended purpose.

Upload the fixture through the normal single-PDF UI and confirm:

- the API rejects it with `422` and the UI displays `CV failed security validation`;
- candidate and `cv_documents` counts do not increase;
- no file remains in `_quarantine` or normal original-CV storage; and
- an ordinary disposable PDF still imports afterward.

Validate fail-closed behavior separately with a new, non-duplicate disposable PDF:

```bash
docker compose --env-file .env --env-file .images.env stop clamav
# Upload the new PDF and confirm a 503 scanner-unavailable response with no persisted candidate/document.
docker compose --env-file .env --env-file .images.env start clamav
docker compose --env-file .env --env-file .images.env ps clamav
docker compose --env-file .env --env-file .images.env logs --tail=100 backend clamav
```

Wait for `clamav` to become healthy, then confirm another new ordinary PDF imports. Do not deliberately stop ClamAV in
production merely to repeat this failure test; staging supplies the fail-closed evidence.

The PostgreSQL dump contains `storage_key`, `stored_at`, and `retention_until`, but not the PDF bytes. Consequently:

- never treat a successful database backup as a complete original-CV backup;
- back up `cv-originals` separately with encryption, restricted credentials, integrity verification, and the approved
  candidate-data retention policy;
- treat the database dump and corresponding volume snapshot as one recovery set;
- test paired restoration in isolation before relying on stored originals for OCR or correction;
- do not use `docker compose down --volumes`, `docker volume rm`, or another volume-destructive operation during normal
  deployment or rollback.

The initial named-volume provider supports the current single-host deployment. Replace it with approved private object
storage before running backend replicas on multiple hosts.

## Access deployment and application logs

For CI/CD logs, open the current repository on GitHub, select **Actions**, choose `CI` or `Deploy staging`, open the relevant run, and expand the job step. Start with the first failed step; later steps may be skipped as a consequence. GitHub masks configured secrets, but application output can still contain personal data.

For staging container logs, connect from the administrator workstation:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_staging" deploy@141.94.33.197
```

On the VPS, inspect service state and recent logs:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env --env-file .images.env ps
docker compose --env-file .env --env-file .images.env logs --tail 100 backend frontend caddy
```

Follow live output and stop following with `Ctrl+C`; this does not stop the containers:

```bash
docker compose --env-file .env --env-file .images.env logs --tail 100 -f backend frontend caddy
```

Limit investigation by service or time when possible:

```bash
docker compose --env-file .env --env-file .images.env logs --since 30m backend
docker compose --env-file .env --env-file .images.env logs --tail 200 db
```

Never publish raw logs without reviewing them for candidate names, email addresses, CV content, prompts, API keys, tokens, database details, and other personal or secret data. Do not print or share `.env`, `.images.env`, private keys, or `docker compose config` output.

## Stop

```bash
docker compose --env-file .env down
```

Do not add `--volumes` unless permanent database and certificate deletion is explicitly intended.
