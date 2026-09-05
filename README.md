# HR AI Recruitment Manager

HR AI Recruitment Manager is a tenant-aware recruitment application for generating job offers, importing and evaluating
CVs, and administering secure recruiter access. AI output supports human decisions; it does not make autonomous hiring
decisions.

## What is implemented

- Generate, edit, approve, persist, and list AI-assisted job offers.
- Import a PDF, a ZIP archive of PDFs, or the administrator-only demonstration CV dataset.
- Detect duplicate CV files within an organization using SHA-256 content hashes.
- Extract CV text and explicitly evaluate a candidate against an approved job.
- Persist candidates, documents, jobs, and evaluation results behind organization boundaries.
- Authenticate with email/password using bcrypt and Redis-backed server sessions.
- Administer tenant users, roles, account state, password resets, and active-session revocation.
- Throttle failed logins, temporarily lock accounts/IPs, and allow administrator account unlock.
- Record tenant-scoped security events with retention cleanup and an administrator history UI.
- Apply versioned PostgreSQL schema changes with Flyway and validate mappings with Hibernate.

Original CV binary storage, OCR, CV chat, speech input/playback, and the recruitment dashboard remain roadmap work.

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
  `---- OpenAI through Spring AI  (job generation and candidate evaluation)
```

Staging and production place Caddy in front of the frontend and API containers for TLS and routing. PostgreSQL and Redis
remain on the private Compose network.

## Technology stack

- Angular 19, TypeScript, Bootstrap, Nginx
- Spring Boot 3.5, Java 21, Spring Security, Spring Data JPA, Spring Session
- Spring AI with OpenAI
- PostgreSQL 16 with pgvector
- Flyway versioned schema migrations
- Redis 7.4
- Docker Compose, Caddy, GitHub Actions, and GHCR

## Current roadmap

The authentication foundation, tenant isolation, Redis sessions, throttling/lockout, security auditing, and password
management are deployed and validated in staging and production. The next delivery sequence is:

1. Maximum concurrent-session limits.
2. Final authentication and security validation.
3. Governed original CV storage and correction/reprocessing.
4. OCR for scanned CVs.
5. Imperative, tenant-scoped CV chat with citations and evaluation testing.
6. Editable speech-to-text input, then optional text-to-speech playback.

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

Keep the remaining Redis, session, throttling, and audit defaults from `.env.example`. Never commit `.env`, reuse a
staging/production credential, or expose secrets in logs, screenshots, or chat.

CV ingestion and text extraction do not use OpenAI. The key is required only for AI generation or evaluation.

### 2. Build and start

```bash
docker compose up -d --build
docker compose ps
```

The `db`, `redis`, `backend`, and `frontend` services should run. PostgreSQL and Redis should report `healthy`; the
backend may briefly report `health: starting`.

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
be adopted. New empty databases run the same `V1__baseline_schema.sql`. Staging and production require a validated backup
and the controlled, temporary baseline flag documented in their runbooks.

Migration rules:

- Never edit or remove a migration already applied to a shared environment.
- Add a higher version for every schema change.
- Rehearse migrations against a restored production backup.
- Keep migrations compatible with the previous application image or document a separate recovery procedure.
- Never delete or manually rewrite `flyway_schema_history`.

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

Flyway integration tests use Testcontainers and run when Docker is available. They verify a clean PostgreSQL schema,
adoption of an existing schema, stale audit-constraint removal, and Hibernate mapping validation.

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

## Documentation

- [Specifications index](specifications/README.md)
- [Implementation roadmap](specifications/06-implementation-roadmap.md)
- [CI/CD and deployment design](specifications/08-ci-cd-deployment.md)
- [Staging runbook](specifications/09-staging-vps-provisioning-runbook.md)
- [Production runbook](specifications/10-production-vps-provisioning-runbook.md)
- [Authentication and authorization](specifications/15-authentication-and-authorization.md)
- [Tenant isolation](specifications/tenant-isolation.md)
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

To remove the local database and Redis volumes deliberately:

```bash
docker compose down -v
```

> This permanently deletes local accounts, sessions, jobs, candidates, CV metadata/text, and evaluations.

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
