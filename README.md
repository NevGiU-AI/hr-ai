# HR AI Recruitment Manager

HR AI Recruitment Manager is a tenant-aware recruitment application for generating job offers, importing and evaluating
CVs, and administering secure recruiter access. AI output supports human decisions; it does not make autonomous hiring
decisions.

## What is implemented

- Generate, edit, approve, persist, and list AI-assisted job offers.
- Import a PDF, a ZIP archive of PDFs, or the administrator-only demonstration CV dataset.
- Detect duplicate CV files within an organization using SHA-256 content hashes.
- Retain new original CVs in private tenant-scoped storage with explicit retention metadata.
- Enforce administrator-managed legal holds and bounded scheduled expiry of stored originals.
- Allow administrators to explicitly confirm full candidate erasure across originals, CV records, and evaluations.
- Quarantine uploaded CVs and require a clean ClamAV result before storage promotion or text extraction.
- Extract CV text and explicitly evaluate a candidate against an approved job.
- Persist candidates, documents, jobs, and evaluation results behind organization boundaries.
- Review tenant-scoped candidate details and document-ingestion history, then download or administer stored originals
  according to the signed-in user's role.
- Correct candidate metadata with immutable revision history and safely reprocess a rescanned stored original without
  changing the submitted PDF or creating duplicate candidates; prior evaluations are retained as stale.
- Review tenant-scoped evaluation history with job context, complete scoring, and explicit current/stale status.
- Fall back to local English/French/Portuguese Tesseract OCR for scanned or low-text PDFs and retain page-level
  provenance plus confidence for human review.
- Ask a bounded, tenant-authorized question about one candidate through the imperative CV-chat backend, with required
  document/page citations and rejection of unsupported model references. The Angular candidate workspace provides the
  corresponding role-aware question, safe-response, error/retry, and linked-citation experience. Semantic search and
  multi-candidate chat remain roadmap work.
- Maintain an opt-in semantic-indexing foundation that creates bounded native/OCR chunks, sends them to Spring AI's
  `PgVectorStore` only after the CV transaction commits, stores portable tenant metadata plus database-enforced tenant
  references, retries missing/stale indexes, and removes them through document/candidate deletion cascades.
  Environment migration and retrieval acceptance remain pending.
- Authenticate with email/password using bcrypt and Redis-backed server sessions.
- Administer tenant users, roles, account state, password resets, and active-session revocation.
- Throttle failed logins, temporarily lock accounts/IPs, and allow administrator account unlock.
- Record tenant-scoped security events with retention cleanup and an administrator history UI.
- Apply versioned PostgreSQL schema changes with Flyway and validate mappings with Hibernate.

Tenant isolation currently uses one shared PostgreSQL schema with `organization_id` on tenant-owned rows. Administrators
may create users only inside their own organization; there is not yet an organization-management API or UI. Independent
customer onboarding requires the planned platform-level organization lifecycle described in the
[tenant-isolation specification](specifications/tenant-isolation.md#planned-organization-lifecycle-management).

Candidate metadata correction/reprocessing passed staging and production acceptance on 22 October 2026. Local OCR and
evaluation history passed staging and production acceptance on 23 October 2026 in release `v0.17.0`. The first
candidate-specific CV-chat backend foundation, including grounded citations and controlled safe refusals, passed
staging and production acceptance on 27 October 2026. Its candidate-specific web UI passed staging acceptance on
28 October 2026 after correcting citation navigation to preserve the candidate-workspace route, then passed focused
production acceptance the same day. Broader semantic retrieval, speech input/playback, and the recruitment dashboard
remain roadmap work. The paired backup tooling is implemented,
but its staging and production installation plus isolated restore acceptance are deliberately scheduled after the
dashboard, stable OpenAPI contracts, and the concurrency benchmark. Private object storage is required before
horizontal or multi-host backend scaling.

## Architecture

```text
Browser
  |
  v
Angular 19 / Nginx
  |
  v
Spring Boot 3 / Java 21
  |---- PostgreSQL 16 + pgvector  (business data and Flyway history)
  |---- Redis 7.4                 (sessions, revocation, login throttling)
  |---- ClamAV 1.4                (fail-closed CV upload scanning)
  |---- Tesseract OCR             (bounded local scanned-PDF extraction)
  |---- Private CV volume         (quarantined and clean original PDFs)
  `---- OpenAI through Spring AI  (job generation, evaluation, and bounded CV chat)
```

Staging and production place Caddy in front of the frontend and API containers for TLS and routing. PostgreSQL, Redis,
and ClamAV remain on the private Compose network.

## Technology stack

- Angular 19, TypeScript, Bootstrap, Nginx
- Spring Boot 3.5, Java 21, Spring Security, Spring Data JPA, Spring Session
- Spring AI with OpenAI and `PgVectorStore`
- PostgreSQL 16 with pgvector
- Flyway versioned schema migrations
- Redis 7.4
- ClamAV 1.4 for fail-closed CV malware scanning
- Docker Compose, Caddy, GitHub Actions, and GHCR

### PostgreSQL and pgvector

PostgreSQL is the relational database that stores application records, transactions, constraints, JSON data, and
Flyway history. pgvector is a PostgreSQL extension—not a separate database—that adds fixed-dimension vector columns,
distance operators, and approximate-nearest-neighbour indexes such as HNSW. The application therefore continues to
use normal PostgreSQL for all business data and adds pgvector only for semantic CV embeddings and similarity search.

The `pgvector/pgvector:pg16` image is PostgreSQL 16 with the extension files already installed. Flyway must still run
`CREATE EXTENSION IF NOT EXISTS vector` inside each application database before a `vector(1536)` column can be used.
Spring AI `PgVectorStore` writes documents and performs metadata-filtered similarity searches; Flyway remains the only
schema owner and creates the compatible table, tenant constraints, and HNSW cosine index.

## Current roadmap

The authentication foundation—including tenant isolation, Redis sessions, throttling/lockout, security auditing,
password management, concurrent-session limits, and the final security acceptance suite—is deployed and validated in
staging and production. The initial original-CV storage slice is also deployed and validated in both environments, and
tenant-authorized original-file operations, scheduled expiry, and administrator-managed legal holds have passed
staging and production acceptance through release `v0.13.0`. Administrator-confirmed full candidate erasure passed
staging and production acceptance in release `v0.14.0`. Candidate details and document-ingestion history passed staging
and production acceptance on 21 October 2026. Correction/reprocessing passed staging and production acceptance on
22 October 2026; encrypted paired backup acceptance remains incomplete. The next delivery sequence is:

1. Validate and enable the semantic-indexing foundation in staging, then integrate tenant-scoped retrieval into chat,
   organization-scoped candidate-name resolution, conversation memory, multi-candidate result cards, cited comparison,
   and formal CV-chat evaluation testing. Candidate names or UI selections must resolve to verified tenant-scoped IDs
   before evidence retrieval; missing or ambiguous names are never guessed.
2. Add editable speech-to-text input, then optional text-to-speech playback.
3. Build the final recruitment dashboard and drill-down workflows.
4. Review the complete codebase and add missing comments and Javadoc for non-obvious contracts, security boundaries,
   transactions, integrations, and business rules.
5. Publish stable OpenAPI contracts for the completed dashboard and application APIs.
6. Benchmark bounded concurrency and Java virtual threads in staging.
7. Install and validate encrypted paired backup/restore in staging.
8. Install and validate paired backup/restore in production after staging acceptance.
9. Persist versioned evaluation prompts, requested model/configuration, exact weights, and source-document version
    metadata after production backup/restore acceptance.
10. Complete cross-cutting security, privacy, AI quality, performance, accessibility, and usability validation.
11. Move original files to private object storage.
12. Consider horizontal or multi-host scaling.
13. Add Telegram/WhatsApp after web chat, speech controls, and documented APIs are stable.
14. Evaluate optional image generation, then video generation, behind separate value and compliance gates.

See the [implementation roadmap](specifications/06-implementation-roadmap.md) for the complete plan.

## Run locally with Docker

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) with Docker Compose
- An OpenAI API key for job generation and explicit candidate evaluation

Confirm Docker is running:

```bash
docker info
docker compose version
```

### 1. Configure local secrets

```bash
git clone https://github.com/NevGiU-AI/hr-ai.git
cd hr-ai
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Set at least these private values in `.env`:

```env
OPENAI_API_KEY=<local OpenAI key>
APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL=admin@localhost
APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD=<local-only password of at least 12 characters>
APP_SECURITY_BOOTSTRAP_ADMIN_ORGANIZATION=local
```

Keep the remaining Redis, session, throttling, audit, and CV-storage retention defaults from `.env.example`. Never
commit `.env`, reuse a staging/production credential, or expose secrets in logs, screenshots, or chat.

CV ingestion and text extraction do not use OpenAI. The key is required only for AI generation or evaluation.

### 2. Build and start

```bash
docker compose up -d --build
docker compose ps
```

The `db`, `redis`, `backend`, and `frontend` services should run. PostgreSQL and Redis should report `healthy`; the
backend may briefly report `health: starting`.

Local Docker persists new original PDFs under `./data/cv-originals`. This ignored directory contains personal data: do
not commit, casually copy, or share it. Removing/recreating the backend container preserves it; deleting `./data`
removes local database and original-CV data.

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

### 3. Sign in and remove the bootstrap secret

Open [http://localhost:4200](http://localhost:4200) and sign in with the bootstrap administrator credentials. After the
first successful login, remove `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD` from `.env` and recreate only the backend:

```bash
docker compose up -d --force-recreate backend
```

Sign in again with the same password. The bcrypt hash remains in PostgreSQL; changing or removing the bootstrap value
does not modify the persisted account. Use **Change password** or the administrator user page for later password changes.

### Local endpoints

| Service | Address |
| --- | --- |
| Frontend | [http://localhost:4200](http://localhost:4200) |
| Backend health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| CSRF bootstrap | [http://localhost:8080/api/auth/csrf](http://localhost:8080/api/auth/csrf) |
| PostgreSQL host port | `localhost:5433` |

Protected APIs intentionally return `401` without an authenticated browser session.

## Database migrations

Flyway migrations live in [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration) and are
packaged inside the backend image. Flyway runs before JPA; Hibernate uses `ddl-auto=validate` and must not mutate staging
or production schemas.

The local Compose configuration enables one-time baselining so an existing developer database created before Flyway can
be adopted. New empty databases run the ordered migration history, currently `V1__baseline_schema.sql`,
`V2__add_original_cv_storage_metadata.sql`, `V3__add_cv_legal_hold.sql`, then
`V4__add_candidate_correction_and_reprocessing.sql`. Staging and production require a validated backup and the
controlled, temporary baseline flag documented in their runbooks.

Migration rules:

- Never edit or remove a migration already applied to a shared environment.
- Add a higher version for every schema change.
- Rehearse migrations against a restored production backup.
- Keep migrations compatible with the previous application image or document a separate recovery procedure.
- Never delete or manually rewrite `flyway_schema_history`.
- Treat application rollback and database recovery as separate operations: Flyway migrations are not undone when Git
  or the deployment script restores an older application image. Prefer a compatible image rollback or a new forward
  corrective migration; restore the validated pre-deployment recovery set only for a reviewed destructive incident.

The complete Flyway rollback decision procedure is documented in [`deploy/README.md`](deploy/README.md#flyway-rollback-and-recovery).

## Recruitment workflow

1. Generate a job-offer draft from **Generate job**.
2. Review, edit, and approve it; only approved jobs are available for evaluation.
3. Open **CVs & Evaluation** and import one PDF, a ZIP archive, or the demonstration dataset.
4. Select a candidate and an approved job, then explicitly start evaluation.
5. Review the overall score, eight metrics, and AI explanation with human judgment.

Uploading a CV never automatically evaluates it. A candidate can be assessed against multiple approved jobs without an
unexpected model call.

### CV ingestion outcomes

| Status | Meaning |
| --- | --- |
| `IMPORTED` | Usable text was extracted and a candidate was created. |
| `DUPLICATE` | The same content already exists in the organization. |
| `NEEDS_REVIEW` | Extracted text is insufficient, commonly because OCR is needed. |
| `SKIPPED` | The archive entry is unsupported or is not a PDF. |
| `FAILED` | Validation, extraction, or persistence failed. |

PDFs are limited to 20 MB and ZIP requests to 100 MB. Candidate metadata inferred from filenames or extracted text must
be reviewed. The application currently stores extracted text and document metadata, not the original PDF binary.

## Authentication and security boundaries

- Browser authentication uses server-side sessions with CSRF protection; it does not expose browser JWTs.
- Sessions and revocation state are shared through Redis and survive backend restarts.
- Roles are `ADMIN`, `RECRUITER`, `REVIEWER`, and `READ_ONLY`.
- Every business query is scoped to the authenticated user's organization.
- Administrators cannot manage accounts in another organization.
- Login responses remain generic to reduce account enumeration.
- Passwords, raw session IDs, CSRF tokens, raw client IPs, and CV contents are excluded from security audit records.
- AI scores are decision support and require human review; they must not be the sole basis of an employment decision.

An organization represents a company; a tenant is that company's isolation boundary. The current one-to-one model uses
`organization_id` for both. Existing `staging` and `production` values are legacy backfill labels, not company names;
they must be replaced by stable organization identities before independent companies are onboarded.

See [authentication and authorization](specifications/15-authentication-and-authorization.md) and
[tenant isolation](specifications/tenant-isolation.md) for the full model.

## Automated tests

Backend tests require Maven and Java 21:

```bash
cd backend
mvn test
```

Flyway integration tests use Testcontainers and run when Docker is available. They use
`pgvector/pgvector:pg16` rather than plain `postgres:16` so V6 can enable and exercise the real vector extension. The
test declaration marks that image as a compatible substitute for `postgres`; this satisfies Testcontainers' image
compatibility check without changing or renaming the image. The tests verify a clean PostgreSQL schema, adoption of an
existing schema, stale audit-constraint removal, original-CV storage metadata, pgvector infrastructure, and Hibernate
mapping validation.

Backend test reports use JUnit Pioneer's
[`ReplaceCamelCaseAndUnderscoreAndNumber`](https://junit-pioneer.org/docs/replace-camelcase-and-underscore-and-number/)
as the global JUnit display-name generator. Descriptive method names therefore become readable report entries without
class-level annotations; an explicit `@DisplayName` still takes precedence.

Frontend tests require Node.js 20:

```bash
cd frontend
npm ci
npm test -- --watch=false
```

The test suites mock AI dependencies; they do not require an OpenAI key.

## Deployment

| Environment | Frontend | Backend API |
| --- | --- | --- |
| Staging | `https://staging-hr.nevgiuai.com` | `https://staging-api.hr.nevgiuai.com` |
| Production | `https://hr.nevgiuai.com` | `https://api.hr.nevgiuai.com` |

Compose responsibilities are intentionally separate:

| File | Purpose |
| --- | --- |
| `docker-compose.yml` | Builds and runs local development services. |
| `deploy/compose.yml` | Runs immutable GHCR images in staging and production. |

There is no separate root production Compose file. GitHub Actions publishes immutable backend/frontend images, copies
the deployment control bundle, and invokes `deploy/deploy.sh`. Application image rollback does not roll back PostgreSQL
schema or data.

Staging and production store new original PDFs in a private `cv-originals` volume. PostgreSQL backups contain only the
corresponding metadata, so database and encrypted original-file backups must be operated and restored as a matched set.

## Documentation

- [Specifications index](specifications/README.md)
- [Implementation roadmap](specifications/06-implementation-roadmap.md)
- [CI/CD and deployment design](specifications/08-ci-cd-deployment.md)
- [Staging runbook](specifications/09-staging-vps-provisioning-runbook.md)
- [Production runbook](specifications/10-production-vps-provisioning-runbook.md)
- [Authentication and authorization](specifications/15-authentication-and-authorization.md)
- [Tenant isolation](specifications/tenant-isolation.md)
- [Governed original CV storage](specifications/20-original-cv-storage.md)
- [Candidate data governance decisions](specifications/21-candidate-data-governance.md)
- [Encrypted paired backup and restore](specifications/23-paired-backup-and-restore.md)
- [Deployment operations](deploy/README.md)

## Useful Docker commands

```bash
# Follow application logs
docker compose logs -f backend frontend

# Rebuild after source or dependency changes
docker compose up -d --build

# Restart without rebuilding
docker compose restart

# Stop containers while retaining data
docker compose down
```

To remove named local volumes deliberately:

```bash
docker compose down -v
```

> This removes the named Redis volume. PostgreSQL and original PDFs use bind-mounted paths under `./data` and remain
> until those directories are separately removed. Treat `./data` as private local candidate data.

## Troubleshooting

### A port is already allocated

Run `docker ps`, then stop the conflicting service or change the host port in `docker-compose.yml`. Default ports are
`4200` for Angular, `8080` for Spring Boot, and `5433` for PostgreSQL host access.

### The backend does not become healthy

```bash
docker compose logs --tail 200 backend
docker compose ps db redis backend
```

Confirm PostgreSQL and Redis are healthy, the OpenAI key exists when AI features are used, Flyway completed, and port
`8080` is available.

### The frontend image reports an esbuild platform mismatch

Ensure `frontend/.dockerignore` excludes `node_modules`, `dist`, and `.angular`. Host-generated dependencies must not be
copied into the Linux image.

## Project history

The project began for the first KLx Crédit Agricole Group Hackathon and moved from the original hackathon repository to
the [NevGiU AI organization](https://github.com/NevGiU-AI/hr-ai) on 1 August 2026.
