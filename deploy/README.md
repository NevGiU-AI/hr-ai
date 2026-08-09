# VPS deployment bundle

This directory contains the provider-neutral Docker Compose deployment used by staging and production.

Use the environment-specific runbook for initial OVHcloud provisioning, SSH hardening, Squarespace DNS, credentials, backups, troubleshooting, and readiness checks:

- Staging: `specifications/09-staging-vps-provisioning-runbook.md`
- Production: `specifications/10-production-vps-provisioning-runbook.md`

Never copy an environment file, SSH key, API key, database password, backup credential, or GitHub environment secret from one procedure to the other.

Production database backups use `backup-postgres.sh` with the accompanying units in `systemd/`. Install the script root-owned as `/usr/local/sbin/nevgiu-postgres-backup`; do not execute a root service from a deployment-writable path. The service streams a custom-format `pg_dump` directly into `age`, uploads only the encrypted artifact and its SHA-256 checksum to private Object Storage, and removes its temporary encrypted files on exit. It requires the production `.env`, generated `.images.env`, root-only AWS writer profile, and `/etc/nevgiu/backup-recipient.txt`; the private recovery identity must never exist on the VPS.

The timer is intentionally installed but left disabled during provisioning. Enable it only after the production stack exists, a manual encrypted upload succeeds, Object Lock is verified on the uploaded object, and an isolated restore succeeds. Its daily schedule is 02:30 UTC with up to thirty minutes of randomized delay and persistent catch-up after downtime.

Object Storage lifecycle retains current objects under `postgresql/` for thirty days, removes incomplete multipart uploads after one day, and cleans eligible non-current versions and expired delete markers. Seven-day Governance Object Lock always takes precedence over lifecycle deletion. Apply or change lifecycle policy only with the offline bucket-owner profile, never with credentials installed on the VPS.

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
```

Use immutable GHCR image digests once image publication is implemented:

```env
BACKEND_IMAGE=ghcr.io/<owner>/<repository>/hr-ai-backend@sha256:<digest>
FRONTEND_IMAGE=ghcr.io/<owner>/<repository>/hr-ai-frontend@sha256:<digest>
```

`API_URL` is generated from `API_HOST` when the frontend container starts. The frontend image is therefore identical in staging and production.

The bootstrap administrator variables are required for the first secured startup only. Confirm login, remove `BOOTSTRAP_ADMIN_PASSWORD` from `.env`, and recreate the backend. The bcrypt hash and account remain in PostgreSQL; never commit or print the bootstrap password.

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

Configuration changes still require versioned backup and restoration, while database migration recovery remains a separate reviewed procedure. Exercise the image rollback path on staging before enabling production releases.

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
