# CI/CD and VPS Deployment

## Objective

Deploy tested, immutable application images to an isolated staging environment and promote the same artifacts to production only after the release is approved.

The deployment design must support:

- Automated validation of every pull request and branch update.
- Automatic staging deployment after `main` passes CI.
- Production deployment from a versioned release with explicit approval.
- Environment-specific secrets and configuration.
- Health-gated rollout and rollback.
- PostgreSQL backup and restore.
- Appropriate protection for candidate CV data.

## Target delivery flow

```text
Pull request
    |
    v
Backend and frontend CI
    |
    v
Merge to main
    |
    v
Build images once and publish immutable SHA tags to GHCR
    |
    v
Automatically deploy those images to staging
    |
    v
Run health checks and smoke tests
    |
    v
Create a semantic version tag and GitHub Release
    |
    v
Approve the protected production environment
    |
    v
Promote the staging-tested image digests to production
    |
    v
Run health checks or roll back to the previous release
```

The production workflow must not rebuild the source. It must deploy the exact image digests already validated in staging.

## Current repository assessment

### Existing CI

`.github/workflows/ci.yml` currently:

- Runs on pushes and pull requests.
- Executes Maven `clean verify` for the backend.
- Installs frontend dependencies and builds Angular.
- Builds backend and frontend Docker images locally.

Required improvements:

- Replace `npm install` with deterministic `npm ci`.
- Run Angular tests in CI rather than only building the application.
- Cache Maven and npm dependencies where it improves execution time without weakening reproducibility.
- Make CI a required status check on the protected `main` branch.
- Consider dependency, secret, and container-image scanning.

### Existing CD

`.github/workflows/cd.yml` currently:

- Runs for semantic-looking `v*.*.*` tags.
- Builds and pushes backend and frontend images to GHCR.
- Connects to an Azure VM over SSH.
- Mutates a production Compose file with `sed` and starts it.

Problems to correct:

- Image names still use the obsolete `finance-ai-*` identifiers.
- The workflow is tied to Azure-specific secret names and paths.
- Images are rebuilt for production instead of promoting staging-tested digests.
- Compose mutation with `sed` is brittle and makes rollback difficult.
- There is no staging deployment, health gate, smoke test, concurrency control, or automatic rollback.
- Production is not protected by a GitHub environment approval.

`.github/workflows/runner.yml` runs on a self-hosted production-like runner when `main` changes, but only builds Compose images. It neither starts nor validates the deployment. Running a GitHub Actions runner on an application VPS also increases attack surface. Remove or disable this workflow after the SSH-based deployment pipeline replaces it.

## Recommended GitHub workflows

### 1. Continuous integration

Triggers:

- Every pull request.
- Every push, including `main`.

Required jobs:

1. Backend verification with Java 21 and `mvn -B clean verify`.
2. Frontend verification with Node 20, `npm ci`, headless Angular tests, and `npm run build`.
3. Docker build verification for both images.
4. Optional security jobs for dependency review, secret scanning, SBOM generation, and image vulnerability scanning.

CI must not require an OpenAI API key. Automated tests should use mocks or test doubles for AI calls.

### 2. Build and publish images

Trigger:

- A successful CI run for `main`, or a reusable workflow explicitly called after CI.

Behavior:

1. Log in to `ghcr.io` with the workflow `GITHUB_TOKEN`.
2. Build backend and frontend images with Buildx.
3. Publish immutable tags based on the full Git commit SHA.
4. Capture the content digest of each image.
5. Optionally publish a mutable `staging` alias for operator convenience, but never use it as the deployment source of truth.
6. Add build provenance and an SBOM when the repository's GitHub plan and security process support them.

Recommended image names:

```text
ghcr.io/<owner>/<repository>/hr-ai-backend:<git-sha>
ghcr.io/<owner>/<repository>/hr-ai-frontend:<git-sha>
```

Deployments should ultimately reference digests:

```text
ghcr.io/<owner>/<repository>/hr-ai-backend@sha256:<digest>
```

### 3. Automatic staging deployment

Trigger:

- Successful publication of images for `main`.

GitHub environment:

- Name: `staging`.
- Restrict deployment to `main`.
- Store staging-only SSH credentials, host details, URLs, and application secrets.

Behavior:

1. Prevent concurrent staging deployments with a GitHub Actions concurrency group.
2. Copy or select a versioned deployment manifest without editing it in place.
3. Pull the immutable backend and frontend image digests.
4. Preserve the previously deployed digest for rollback.
5. Start the Compose project.
6. Wait for PostgreSQL and backend health checks.
7. Run smoke tests against the public staging URLs.
8. Mark the deployment successful only after every check passes.
9. Roll back to the previous digests if startup or smoke testing fails.

Minimum staging smoke tests:

- `GET /actuator/health` returns `200` and `{"status":"UP"}`.
- The frontend root returns `200`.
- Angular routes fall back correctly through Nginx.
- A safe read-only jobs endpoint returns a valid response.
- A safe read-only candidates endpoint returns a valid response.
- Database connectivity is confirmed indirectly through backend health and API checks.

Live OpenAI smoke tests should be separate, opt-in, rate-limited tests. They should not run on every deployment because of cost and provider variability.

### 4. Approved production promotion

Trigger:

- A published GitHub Release or semantic version tag such as `v1.2.0`.
- Optional manual `workflow_dispatch` for an explicitly selected, previously validated image SHA.

GitHub environment:

- Name: `production`.
- Restrict deployment to approved release tags.
- Require a reviewer when the repository visibility and GitHub plan support environment reviewers.
- Prevent self-review where operationally appropriate.
- Store production-only secrets; do not reuse staging credentials.

Release readiness should require:

- CI passed for the release commit.
- The exact image digests were successfully deployed and smoke-tested in staging.
- Product acceptance criteria for the release are met.
- Database migration and rollback compatibility have been reviewed.
- Backup freshness and restore procedures have been confirmed.
- Known security and privacy risks have been accepted or resolved.

Production behavior:

1. Resolve the staging-tested image digests associated with the release commit.
2. Take or verify a recent PostgreSQL backup before database-affecting changes.
3. Deploy the immutable digests with a production Compose project.
4. Run health checks and production-safe smoke tests.
5. Roll back application images if validation fails.
6. Record the deployed version, commit, digests, actor, and timestamp.

Database rollback is not the same as application rollback. Migrations must be backward compatible whenever possible, and destructive migrations require a separate reviewed recovery plan.

## VPS recommendation

### Recommended initial provider: OVHcloud

Use two separate OVHcloud VPS instances, one for staging and one for production. At the time this specification was updated, the advertised VPS-2 configuration included approximately:

- 4 vCores.
- 8 GB RAM.
- 75 GB NVMe storage.
- Automated one-day backup.
- Unlimited traffic and integrated DDoS protection.
- An entry price around EUR 7.21 excluding VAT per VPS per month.

Two instances would therefore start around EUR 14.42 excluding VAT per month before optional services. Pricing, tax, commitment, location, backup retention, and availability must be confirmed at checkout.

Official pricing: <https://www.ovhcloud.com/nl/vps/>

Why this is suitable:

- 8 GB RAM is a reasonable starting point for PostgreSQL, Spring Boot, Nginx, Docker, and lightweight monitoring.
- Separate machines prevent staging deployments and load from affecting production.
- European hosting is preferable for personal CV data, subject to the final legal and data-residency review.
- Included infrastructure backup and DDoS protection improve the starting operational baseline.

### Alternatives

**Hetzner Cloud** often offers strong European price/performance. Its prices changed substantially in June 2026, so the current instance specification and final price must be verified when ordering.

Official pricing notice: <https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/>

**Scaleway DEV1-L** was advertised with 4 vCPUs and 8 GB RAM at approximately EUR 31.27 per month before tax. Public IPv4 and some storage can be billed separately. It is a reasonable alternative when a France-based European cloud or Scaleway's wider managed-service ecosystem is preferred.

Official pricing: <https://www.scaleway.com/en/pricing/virtual-instances/>

### Single-VPS option

For a short-lived demonstration, staging and production can run on one VPS as isolated Compose projects, using separate networks, volumes, secrets, ports, and hostnames. This reduces cost but creates shared-resource and shared-failure risks.

Do not use the single-VPS model once real candidate data or meaningful production traffic is introduced.

## Target server architecture

Recommended directory layout on each VPS:

```text
/opt/nevgiu/
|-- compose.yml
|-- .env
|-- deploy.sh
|-- backups/
`-- data/
    `-- postgres/
```

The implemented stack uses Caddy as its public reverse proxy and automatic TLS terminator. Only Caddy publishes ports `80` and `443`. It redirects HTTP to HTTPS, obtains and renews certificates, routes the frontend hostname to the frontend Nginx container, and routes the API hostname to Spring Boot over the private Docker network.

Frontend Nginx and Caddy have separate responsibilities: Nginx serves compiled Angular assets and handles single-page-application route fallback, while Caddy owns public domains, TLS, and upstream routing. Caddy does not implement application authentication, business logic, data persistence, or deployment rollback.

Caddy is not mandatory as a product choice, but its gateway responsibilities are mandatory. A replacement such as Nginx, Traefik, HAProxy, or a managed load balancer must preserve automatic or reliably operated certificate renewal, HTTP-to-HTTPS redirects, hostname-based routing, private upstream ports, appropriate security policy, and external health verification. Removing the gateway without replacement would expose sensitive recruitment traffic to transport, certificate, routing, and direct-service-exposure risks.

Canonical HR AI hostnames:

```text
staging-hr.nevgiuai.com
staging-api.hr.nevgiuai.com
hr.nevgiuai.com
api.hr.nevgiuai.com
```

The reusable Nevgiu AI convention is:

| Environment | Frontend | Backend API |
| --- | --- | --- |
| Production | `<project>.nevgiuai.com` | `api.<project>.nevgiuai.com` |
| Staging | `staging-<project>.nevgiuai.com` | `staging-api.<project>.nevgiuai.com` |

For this application, `<project>` is `hr`. The apex `nevgiuai.com` remains available for the Nevgiu AI company and project portal.

Use DNS TTL `300` seconds while provisioning, migrating, or troubleshooting an environment. The short cache lifetime allows an incorrect VPS address to be corrected quickly. After HTTPS, deployment, and functional checks remain stable, increase the TTL to `3600` seconds to reduce unnecessary DNS queries and cache churn.

Before a planned IP migration, lower a stable `3600`-second TTL back to `300` at least one hour before changing the address. This allows records cached under the previous TTL to expire. After the new address is verified, restore `3600`. TTL controls resolver cache duration but does not guarantee that every provider refreshes immediately.

Environment configuration must use:

| Environment | Angular API URL | `APP_CORS_ALLOWED_ORIGINS` |
| --- | --- | --- |
| Production | `https://api.hr.nevgiuai.com/api` | `https://hr.nevgiuai.com` |
| Staging | `https://staging-api.hr.nevgiuai.com/api` | `https://staging-hr.nevgiuai.com` |

The Angular `production` and `staging` build configurations embed their corresponding API URL. The backend reads allowed browser origins from `APP_CORS_ALLOWED_ORIGINS`; do not add API hostnames to CORS unless a browser application is actually served from those hosts.

Only the following ports should be publicly reachable:

- `22/tcp` for restricted SSH administration.
- `80/tcp` for HTTP-to-HTTPS redirection and ACME challenges.
- `443/tcp` for HTTPS.

PostgreSQL port `5432`, the current host mapping `5433`, and backend port `8080` must not be exposed publicly. The reverse proxy, frontend, backend, and database should communicate through private Docker networks.

Server hardening should include:

- SSH keys only; disable password and root login.
- A firewall allowing only required ports.
- Automated security updates or an agreed patch schedule.
- Fail2ban or equivalent SSH abuse protection where appropriate.
- A non-root deployment user with narrowly scoped permissions.
- Docker log rotation and disk-space alerts.
- Time synchronization.
- Monitoring of CPU, memory, disk, container health, certificate expiry, and backup success.

## Production Compose requirements

Create a production-specific Compose file instead of deploying the current development file unchanged.

Required changes:

- Use prebuilt GHCR image references supplied through environment variables.
- Remove application `build` directives.
- Remove the PostgreSQL host port.
- Do not expose backend port `8080` publicly.
- Replace hardcoded database credentials with high-entropy environment secrets.
- Run password-protected Redis only on the private data network and persist Spring Session data in a named volume.
- Use separate staging and production database credentials.
- Add explicit health checks and appropriate restart policies.
- Add bounded CPU and memory configuration where supported.
- Put services on separate public-proxy and private-data networks.
- Disable built-in CV import in production with `app.cv-ingestion.initial-import-enabled=false`.
- Disable SQL logging in production.
- Replace Hibernate `ddl-auto: update` with Flyway or Liquibase migrations before production data is introduced.
- Configure trusted frontend origins instead of unrestricted development CORS.
- Configure structured logs without CV text, prompts, API keys, or personal data.

## Secrets and GitHub environments

Use separate GitHub `staging` and `production` environments. Environment secrets become available only to jobs targeting that environment and, when protection rules are configured, only after approval.

The implemented workflows use these environment variables:

```text
DEPLOY_HOST
DEPLOY_USER
```

They use these environment secrets:

```text
DEPLOY_SSH_KEY
DEPLOY_KNOWN_HOSTS
GHCR_USERNAME
GHCR_TOKEN
```

For staging, `DEPLOY_HOST` is `141.94.33.197` and `DEPLOY_USER` is `deploy`. `GHCR_TOKEN` is a classic personal access token limited to `read:packages`; authorize it for organization SSO when applicable. `GHCR_USERNAME` is the account that created the token. `DEPLOY_KNOWN_HOSTS` must contain the previously verified SSH host-key entry.

The following application configuration remains in the VPS-only `/opt/nevgiu/deploy/.env` file and is deliberately not stored in GitHub:

```text
OPENAI_API_KEY
POSTGRES_PASSWORD
REDIS_PASSWORD
REDIS_SESSION_NAMESPACE
REDIS_SECURITY_NAMESPACE
LOGIN_ACCOUNT_FAILURE_LIMIT
LOGIN_IP_FAILURE_LIMIT
LOGIN_FAILURE_WINDOW
LOGIN_LOCK_DURATION
FRONTEND_HOST
API_HOST
FRONTEND_URL
INITIAL_IMPORT_ENABLED
```

`deploy/.env.example` intentionally leaves `REDIS_PASSWORD` empty. The CI Compose-rendering check supplies a fixed,
validation-only placeholder because Compose requires a non-empty value; that placeholder never starts Redis and must
never be used as an environment credential. Real staging and production passwords exist only in their protected VPS
`.env` files and must be different.

Prefer a dedicated read-only GHCR token on the VPS if private packages require authentication. Do not place secrets in Compose files, workflow logs, image layers, repository variables, or command-line output.

GitHub documentation:

- Environments and protection rules: <https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments>
- Publishing Docker images: <https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images>
- Artifact attestations: <https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations>

Required-reviewer availability depends on repository visibility and GitHub plan. Confirm those constraints before relying on GitHub approval as the only production gate.

## Deployment logging and diagnosis

GitHub Actions is the source of truth for pipeline execution. Operators inspect the relevant `CI`, `Deploy staging`, or `Deploy production` run and begin with the first failed step. Workflow summaries identify the deployed commit and public environment URLs.

The backend Docker build uses bounded Maven retries for dependency prefetch and packaging. A temporary Maven Central
`5xx` response is retried with short backoff; persistent repository or dependency errors still fail the job. Re-run a
failed job when its log identifies an upstream `502 Bad Gateway`, but investigate version or repository errors that
remain after all attempts.

Runtime diagnosis is performed as the environment's `deploy` user from `/opt/nevgiu/deploy`. Compose commands must load both the private application environment and the generated immutable image references:

```bash
docker compose --env-file .env --env-file .images.env ps
docker compose --env-file .env --env-file .images.env logs --since 30m backend
docker compose --env-file .env --env-file .images.env logs --tail 100 -f backend frontend caddy
```

Log access is privileged because candidate data, extracted CV text, AI prompts, or operational details may appear until structured redaction is implemented. Logs must not be pasted into public issues, pull requests, chat, or incident reports without review and redaction. Environment files, SSH keys, tokens, and expanded Compose configuration must never be included in diagnostic output.

### Newly deployed frontend controls are missing

Angular JavaScript and CSS assets use content-hashed filenames and long-lived immutable browser caching. After a
successful deployment, an already-open tab can therefore continue displaying the previous application until it loads
the new `index.html`. First perform a hard refresh (`Ctrl+Shift+R` in Chrome on Windows/Linux) and revisit the affected
route.

If the feature is still absent, verify that the deployment manifest references the expected merge commit before
investigating application code. On the environment VPS as `deploy`:

```bash
cd /opt/nevgiu/deploy
grep '^BACKEND_IMAGE=' .images.env
grep '^FRONTEND_IMAGE=' .images.env
docker compose --env-file .env --env-file .images.env ps
```

Both image tags must end with the commit SHA identified by the corresponding successful deployment workflow. A hard
refresh resolved the apparently missing account-lifecycle controls during staging validation of PR `#25`; the deployed
images were correct and no rollback or redeployment was required.

## Database backup and recovery

Provider snapshots and VPS backups do not replace application-aware PostgreSQL backups.

The production VPS uses OVHcloud Premium automated backup, ordered and confirmed active in the OVHcloud Control Panel on 2026-08-04. It adds seven rolling daily whole-VPS restore points; staging continues to use the included Standard plan with one daily restore point retained for 24 hours. Premium is an infrastructure recovery layer, not a substitute for database-aware, encrypted, off-server backups. The appearance of daily restore points must be verified in the OVHcloud Control Panel before production receives real candidate data.

Minimum policy:

- Run an encrypted daily `pg_dump` or equivalent PostgreSQL backup.
- Encrypt each dump on the VPS with the public `age` recipient before upload. Keep the passphrase-encrypted recovery identity and its passphrase off-server in approved recovery storage.
- Keep backups outside the application VPS and preferably in a separate account or object-storage service. Production uses a private S3-compatible bucket in OVHcloud `GRA`, separate from the `RBX` production VPS.
- Enable versioning and Object Lock with seven-day Governance retention; do not use Compliance mode or Legal Hold by default because candidate-data deletion obligations must remain enforceable. The OVHcloud Control Panel displayed one year during setup, but an authenticated S3 API read confirmed the effective default is seven days; operational checks must trust and record the API result.
- Keep the bucket in the dedicated `NevGiU AI Production Backups` project. Retain bucket-owner credentials only for controlled administration and recovery, and install only the separately verified upload-only writer identity on the VPS; never use personal, staging, or application credentials.
- Define retention tiers, for example seven daily, four weekly, and three monthly copies, subject to candidate-data retention policy.
- Record and alert on backup success or failure.
- Test restoration regularly on an isolated environment.
- Back up before risky schema changes and releases.
- Document recovery point and recovery time objectives.
- Ensure expired candidate data is removed from active data and backups according to the approved governance policy.

## Rollback strategy

The implemented `deploy.sh` provides health-gated application-image rollback. Before replacement, it records the backend and frontend image references used by the running containers in `.images.env.previous`, validates the candidate Compose model, pulls the candidate images, deploys them, and polls for approximately three minutes. Success requires healthy PostgreSQL, Redis, backend, and frontend services plus running Caddy.

If initial startup or internal health validation fails, the script:

1. Retains the last 100 Compose log lines in the failed GitHub job.
2. Restores the previous backend and frontend image references in `.images.env`.
3. Recreates the Compose stack with those images.
4. Repeats internal health validation.
5. Keeps the workflow failed even if rollback restores service successfully.

The staging and production workflows also invoke `deploy.sh --rollback` when a public HTTPS smoke test fails, then repeat both public checks against the restored deployment. The original failure remains visible because the workflow remains failed even after a successful recovery.

Current limitations:

- Compose validation and application-image pull failures leave the running deployment unchanged; they do not require rollback because the active image manifest has not yet changed.
- Compose, Caddy, `.env`, secret, and deployment-file changes are not restored.
- PostgreSQL data and schema are never automatically restored.
- Rollback depends on the previous images remaining available locally or in GHCR.

Before the first production release, exercise a controlled staging failure and verify internal recovery and public availability. Versioned deployment configuration must still be restored alongside image references where configuration compatibility requires it.

Do not automatically restore a database backup for ordinary application failure. Database restoration is destructive and requires an explicit incident decision based on the migration and data-loss impact.

## Data protection and production blockers

Authentication, role-based authorization, restricted CORS, administrator-only built-in import, and organization tenant
isolation are implemented and were validated in production on 16 August 2026. Before real candidate information is
processed beyond the approved validation dataset, resolve the remaining controls:

- Candidate lawful processing basis, notice/consent, retention, deletion, correction, and audit policies.
- Malware scanning for uploads.
- Whether and where original CV files will be retained.
- OpenAI data-processing terms and disclosure that CV text is sent to the provider only during explicit evaluation.
- Prompt-injection defenses for untrusted CV content.
- Log and monitoring redaction.

The deployment pipeline can be built before these items are complete, but the environment must not be described as production-ready for real candidate data until the blockers are addressed.

## Delivery plan

### Phase 1 - Production-ready container configuration

- [x] Add a provider-neutral deployment Compose file.
- [x] Add a Caddy reverse proxy with automatic TLS.
- [x] Externalize database and application secrets through the server environment file.
- [x] Remove public database and backend ports from the deployment topology.
- [x] Add environment-driven production Spring settings.
- [x] Make built-in CV import environment-controlled and document that production disables it.
- [x] Add deployment and rollback scripts.
- [ ] Add database backup and restore scripts and test restoration.

**Exit condition:** the application can be deployed manually to a clean VPS over HTTPS without development credentials or public data services.

### Phase 2 - CI and image publication

- [x] Run backend and frontend tests and builds deterministically.
- [ ] Make CI required for `main`.
- [x] Rename container packages from `finance-ai-*` to `hr-ai-*`.
- [x] Build images once and publish SHA tags to GHCR after successful `main` CI.
- [x] Inject the frontend API URL at container startup so staging and production use the same image.
- [ ] Add image metadata and optional SBOM/provenance.
- [x] Remove the self-hosted runner workflow.

**Exit condition:** every accepted `main` commit has traceable, immutable, tested container images.

### Phase 3 - Automatic staging

- [x] Configure the GitHub `staging` environment and secrets.
- [x] Add automatic deployment of every successful `main` image to staging.
- [x] Add concurrency protection, health waiting, and smoke tests.
- [x] Add automatic application-image rollback.
- [x] Extend automatic rollback to public HTTPS smoke-test and initial Compose startup failures; validate and pull candidates before switching the active manifest.
- [ ] Version and restore compatible Compose and Caddy configuration during application rollback.
- [x] Exercise a controlled failed staging deployment and verify both rollback health and public availability.
- [x] Publish deployment URL and commit in the workflow summary.
- [x] Mark successfully smoke-tested images with staging-validation aliases.
- [ ] Configure staging log rotation and retention limits for Docker and system services.
- [ ] Validate that operators can retrieve staging logs and that candidate data, prompts, and secrets are redacted before logs are shared.

**Exit condition:** a successful merge reaches staging automatically and is accepted only when application checks pass.

Validated on 2 August 2026: CI published immutable backend and frontend images from the transferred `NevGiU-AI/hr-ai` repository, deployed them automatically to the staging VPS, passed public HTTPS smoke tests, and completed the job-offer generation, approval, CV ingestion, and candidate-evaluation functional path. GitHub Action dependencies were upgraded to Node.js 24-compatible major versions during this validation.

Validated on 4 August 2026: a controlled staging drill deliberately failed the public API smoke test after a successful application deployment. GitHub Actions invoked `deploy.sh --rollback`, restored the previous backend and frontend images, and successfully rechecked both public HTTPS endpoints. The workflow remained failed as intended, preserving a visible deployment incident while confirming service recovery.

### Phase 4 - Approved production promotion

- [x] Configure the protected `production` environment and separate secrets.
- [x] Trigger promotion from a semantic version GitHub Release.
- [x] Require staging-validation aliases for the release commit.
- [x] Require production approval where supported.
- [x] Check backup readiness before deployment.
- [x] Add production health checks, application rollback, and deployment summaries.
- [x] Make public smoke-test failure invoke application-image rollback and external verification.
- [x] Exercise the public smoke-test rollback path successfully on staging before enabling production.
- [ ] Configure production log rotation, retention, restricted operator access, and encrypted off-server collection before processing real candidate data.
- [ ] Validate production log redaction and define the approved incident-log export procedure.

**Exit condition:** an approved release promotes the exact staging-tested artifacts and can safely return to the previous application version.

Validated on 16 August 2026: release `v0.2.0` promoted commit `33d8a04` after a validated pre-tenant database backup and
controlled production organization migration. The protected deployment completed successfully, public smoke checks
passed, and authentication plus tenant-scoped application workflows were accepted in production.

### Phase 5 - Operational hardening

- [ ] Add uptime, resource, certificate, disk, container, and backup monitoring.
- [ ] Implement structured application logs with correlation IDs and no CV text, prompts, credentials, tokens, or unnecessary personal data.
- [ ] Select and configure centralized log aggregation for staging and production with separate access controls and retention policies.
- [ ] Alert on repeated deployment failures, unhealthy services, authentication failures, and critical backend errors without including sensitive payloads.
- [ ] Add dependency and image vulnerability scanning.
- [ ] Add alert routing and an incident runbook.
- [ ] Test recovery from server loss and database corruption.
- [ ] Define release, rollback, patching, and secret-rotation ownership.
- [ ] Review capacity after observing real staging workloads.

**Exit condition:** deployment, backup, recovery, monitoring, and security processes have named owners and have been exercised successfully.

## Acceptance criteria

- [ ] Pull requests cannot merge unless required backend and frontend checks pass.
- [x] A passing `main` commit publishes immutable images and deploys automatically to staging.
- [x] Staging deployment fails and rolls back when health or smoke tests fail.
- [ ] Production deployment uses the same image digests validated in staging.
- [ ] Production requires a versioned release and an explicit approval gate where supported.
- [x] Staging and production use separate hosts or an explicitly accepted temporary isolation model.
- [x] Environment secrets are isolated and never written to logs or the repository.
- [x] PostgreSQL and backend application ports are not publicly exposed.
- [x] HTTPS is enforced for all public traffic.
- [ ] Backups are encrypted, stored off-server, monitored, retained according to policy, and restore-tested.
- [ ] Application rollback covers internal health and public smoke-test failures; application coverage is verified, but database recovery must still be restore-tested separately.
- [x] The deployed commit, release, actor, timestamp, and image digests are traceable.
- [ ] Staging and production logs have documented retention, rotation, access control, redaction, and incident-export procedures.
- [ ] Real candidate data is prohibited until the documented privacy and security blockers are resolved.
