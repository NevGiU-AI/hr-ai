# Staging VPS Provisioning and Operations Runbook

## Purpose

Provide the detailed, validated procedure for provisioning, securing, deploying, testing, operating, and troubleshooting the HR AI staging VPS. This environment validates every passing `main` revision before any production release.

## Staging inventory

| Item | Value |
| --- | --- |
| OVHcloud plan | VPS-2 |
| Operating system | Ubuntu Server 26.04 LTS |
| Public IP | `141.94.33.197` |
| Frontend | `https://staging-hr.nevgiuai.com` |
| Backend API | `https://staging-api.hr.nevgiuai.com` |
| Initial administrator | `ubuntu` |
| Deployment account | `deploy` |
| VPS backup | OVHcloud Standard; one daily restore point retained for 24 hours |

The domain is authoritatively served by Squarespace DNS. Keep setup TTL at `300` seconds until validation is stable, then use `3600`. Do not modify Google Workspace MX or TXT records.

## Staging-specific rules

- Use staging-only SSH keys, OpenAI credentials, PostgreSQL credentials, GitHub environment secrets, and container image aliases.
- Real candidate data is prohibited; staging uses synthetic or approved test data only.
- Store runtime secrets only in `/opt/nevgiu/deploy/.env`, owned by `deploy` with mode `600`.
- The GitHub `staging` environment deploys immutable images after CI passes on `main`.
- `deploy` belongs to the root-equivalent `docker` group; protect and rotate its key accordingly.
- OVHcloud Standard backup is an infrastructure recovery layer, not a replacement for database-aware backup testing.
- Expose only SSH, HTTP, and HTTPS through UFW. PostgreSQL and backend container ports remain private.

## Staging procedure

### 1. Update Ubuntu

Connect with the OVH-provided administrative user:

```bash
ssh ubuntu@141.94.33.197
```

Update and reboot:

```bash
sudo apt update
sudo apt upgrade -y
sudo reboot
```

Wait approximately one minute and reconnect.

### 2. Create the deployment user

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo mkdir -p /home/deploy/.ssh
sudo chown -R deploy:deploy /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
```

Generate a dedicated staging key on the administrator workstation. On Windows PowerShell:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\nevgiu_hr_staging"
```

The CI/CD key must not reuse a personal SSH key. A passphrase-free key is required for unattended GitHub Actions unless a separate supported key-agent mechanism is introduced.

Copy only the public key:

```powershell
scp "$env:USERPROFILE\.ssh\nevgiu_hr_staging.pub" ubuntu@141.94.33.197:/tmp/deploy-key.pub
```

Install it from the administrative session:

```bash
sudo cp /tmp/deploy-key.pub /home/deploy/.ssh/authorized_keys
sudo chown -R deploy:deploy /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo rm /tmp/deploy-key.pub
```

Test from a second terminal before closing the administrative session:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_staging" deploy@141.94.33.197
```

### 3. Install Docker

Run as `ubuntu`:

```bash
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker deploy
```

Disconnect and reconnect as `deploy` so the new group membership applies, then verify without `sudo`:

```bash
docker version
docker compose version
```

### 4. Enable administrative key authentication

Before disabling password authentication, install and verify an SSH key for `ubuntu`. The staging setup reused the staging key's public half for initial administration:

```bash
sudo mkdir -p /home/ubuntu/.ssh
sudo cp /home/deploy/.ssh/authorized_keys /home/ubuntu/.ssh/authorized_keys
sudo chown -R ubuntu:ubuntu /home/ubuntu/.ssh
sudo chmod 700 /home/ubuntu/.ssh
sudo chmod 600 /home/ubuntu/.ssh/authorized_keys
```

Verify key-based administrative access from a new terminal:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_staging" ubuntu@141.94.33.197
```

For a mature production environment, consider separate human-administration and deployment keys instead of reusing one public key across both accounts.

### 5. Configure the firewall

Run as `ubuntu`:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status
```

`OpenSSH` represents TCP port 22. Keep an existing SSH session open and verify a new connection succeeds before continuing.

Only ports 22, 80, and 443 are public. PostgreSQL, backend port 8080, and frontend port 80 inside Docker are not published directly by the deployment Compose file.

### 6. Disable password and root SSH login

Run as `ubuntu` only after both `ubuntu` and `deploy` key access have been tested:

```bash
printf '%s\n' \
  'PasswordAuthentication no' \
  'KbdInteractiveAuthentication no' \
  'PermitRootLogin no' \
  'PubkeyAuthentication yes' \
  | sudo tee /etc/ssh/sshd_config.d/00-nevgiu-hardening.conf

sudo sshd -t
sudo systemctl reload ssh
```

OpenSSH uses the first obtained value for each setting. The `00-` prefix ensures this file is evaluated before Ubuntu cloud-init files such as `50-cloud-init.conf`, which may explicitly enable password authentication. A later `99-` file does not reliably override an earlier value.

Verify the effective configuration rather than assuming the file was applied:

```bash
sudo sshd -T | grep -E 'passwordauthentication|kbdinteractiveauthentication|permitrootlogin|pubkeyauthentication'
```

Keep the current session open and test new key-based connections for both accounts. If `sshd -t` fails, do not reload SSH; correct the reported configuration error first. Staging was initially configured with a `99-` file and its audit found `passwordauthentication yes`. The file was migrated to `00-nevgiu-hardening.conf`; all four effective settings then matched the expected secure values. Fresh `ubuntu` and `deploy` key sessions succeeded, and a password-only attempt was rejected with `Permission denied (publickey)`.

### 7. Prepare the application directory

Run as `ubuntu`:

```bash
sudo hostnamectl set-hostname nevgiu-hr-staging
sudo mkdir -p /opt/nevgiu/backups /opt/nevgiu/data/postgres
sudo chown -R deploy:deploy /opt/nevgiu
sudo chmod 755 /opt/nevgiu
```

Verify:

```bash
hostname
ls -ld /opt/nevgiu
```

### 8. Configure Squarespace DNS

In Squarespace:

1. Open the Squarespace account's domain management.
2. Select `nevgiuai.com`.
3. Open **DNS** or **DNS Settings**.
4. Under custom records, create the two staging A records listed in the DNS inventory.
5. Use TTL `300` during setup; after all staging validation succeeds, increase it to `3600`.
6. Do not modify Google Workspace email records.

Verify from Windows PowerShell:

```powershell
Resolve-DnsName staging-hr.nevgiuai.com
Resolve-DnsName staging-api.hr.nevgiuai.com
```

Both must resolve to `141.94.33.197` before Caddy requests certificates.

### 9. Copy the deployment bundle

From the repository workstation:

```powershell
$deployBundle = Join-Path (Get-Location) "deploy"
scp -i "$env:USERPROFILE\.ssh\nevgiu_hr_staging" -r $deployBundle deploy@141.94.33.197:/opt/nevgiu/
```

Run these commands from the root of the current repository checkout. They do not depend on its local directory name.

Verify on the VPS:

```bash
ls -la /opt/nevgiu/deploy
```

Expected tracked files include:

```text
.env.example
Caddyfile
compose.yml
deploy.sh
README.md
```

If `deploy` receives permission errors:

```bash
sudo chown -R deploy:deploy /opt/nevgiu
sudo chmod 755 /opt/nevgiu /opt/nevgiu/deploy
```

Run those repair commands as `ubuntu`, then return to `deploy` for application operations.

### 10. Create the private environment file

Run as `deploy`:

```bash
cd /opt/nevgiu/deploy
cp .env.example .env
openssl rand -hex 32
nano .env
chmod 600 .env
```

Set real staging values only in `.env`:

```env
ACME_EMAIL=<operations email>
POSTGRES_PASSWORD=<generated staging password>
OPENAI_API_KEY=<staging OpenAI key>
```

`ACME_EMAIL` is the monitored operational contact Caddy supplies to the ACME certificate authority for automatic HTTPS certificate issuance and renewal. It can receive expiration, renewal-failure, policy, or recovery notices. It is not used to sign in to the application, send application email, or authenticate to OpenAI. Use a role mailbox where possible; staging and production may share this contact because it is administrative metadata, not an environment credential.

Confirm these environment-specific values remain:

```env
FRONTEND_HOST=staging-hr.nevgiuai.com
API_HOST=staging-api.hr.nevgiuai.com
FRONTEND_URL=https://staging-hr.nevgiuai.com
INITIAL_IMPORT_ENABLED=true
```

Do not display or transmit the completed file.

### 11. Perform the first manual image build

This step bootstraps staging before GHCR automation is enabled. Clone the repository as `deploy`:

```bash
cd /opt/nevgiu
git clone <repository-url> source
```

Replace `<repository-url>` with the HTTPS or SSH URL shown by the **Code** button on the current GitHub repository. Do not hardcode a personal account or organization name in this runbook.

Build local images:

```bash
docker build -t hr-ai-backend:staging-manual /opt/nevgiu/source/backend

docker build \
  --build-arg CONFIGURATION=production \
  -t hr-ai-frontend:staging-manual \
  /opt/nevgiu/source/frontend
```

The frontend image uses runtime `API_URL` injection, so the same production-built image format is valid for staging and production.

### 12. Start the stack

Run as `deploy`:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d
```

Wait approximately one minute:

```bash
docker compose --env-file .env ps
```

Expected services:

- `db`: healthy.
- `backend`: healthy.
- `frontend`: healthy.
- `caddy`: running.

### 13. Verify HTTPS

```bash
curl --fail https://staging-api.hr.nevgiuai.com/actuator/health
curl --fail --head https://staging-hr.nevgiuai.com/
```

Expected backend response:

```json
{"status":"UP"}
```

Caddy obtains and renews certificates automatically after DNS resolves and ports 80/443 are reachable.

### 14. Run the functional smoke test

In the staging frontend:

1. Generate a job offer.
2. Enter edit mode and approve it.
3. Confirm it appears in approved jobs.
4. Import a PDF, ZIP, or the staging built-in CV dataset.
5. Select an imported candidate and approved job.
6. Explicitly evaluate the candidate.
7. Confirm the overall score, all eight metrics, and explanation appear.
8. Restart the Compose services and confirm persisted jobs, candidates, documents, and evaluations remain.

### 15. Access operational logs

GitHub pipeline logs:

1. Open the current repository on GitHub and select **Actions**.
2. Select `CI`, `Deploy staging`, or `Deploy production`.
3. Open the run for the affected commit.
4. Expand the first failed job step and inspect later steps only if they ran.

Staging application logs require the dedicated deployment key. From Windows PowerShell:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_staging" deploy@141.94.33.197
```

Then run on the VPS:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env --env-file .images.env ps
docker compose --env-file .env --env-file .images.env logs --tail 100 backend frontend caddy
```

Follow live logs:

```bash
docker compose --env-file .env --env-file .images.env logs --tail 100 -f backend frontend caddy
```

Press `Ctrl+C` to stop following output; containers continue running. To narrow the output:

```bash
docker compose --env-file .env --env-file .images.env logs --since 30m backend
docker compose --env-file .env --env-file .images.env logs --tail 200 db
```

Review and redact output before sharing it. Logs may contain candidate personal data, extracted CV content, prompts, or operational details. Never display or transmit `.env`, `.images.env`, private SSH keys, access tokens, or expanded Compose configuration.

## Troubleshooting learned during staging

### Frontend reported unhealthy while Nginx was running

Symptom:

```text
wget: can't connect to remote host: Connection refused
```

Cause: `localhost` resolved to IPv6 while Nginx listened on IPv4.

Resolution: the tracked Compose health check uses:

```text
http://127.0.0.1/
```

Inspect health and logs with:

```bash
cd /opt/nevgiu/deploy
frontend_container=$(docker compose --env-file .env ps -q frontend)
docker inspect "$frontend_container" --format '{{range .State.Health.Log}}{{.Output}}{{end}}'
docker logs "$frontend_container" --tail 50
```

### Caddy did not start

Caddy depends on healthy frontend and backend containers. Correct unhealthy services first, then rerun:

```bash
docker compose --env-file .env up -d
```

### `.env` permission denied

Run as `ubuntu`:

```bash
sudo chown deploy:deploy /opt/nevgiu/deploy/.env
sudo chmod 600 /opt/nevgiu/deploy/.env
```

Verify as `deploy` without printing its contents:

```bash
test -r /opt/nevgiu/deploy/.env && echo "Environment file is readable"
```

### `scp` requested the deploy password

Run PowerShell-style `scp` commands from the local Windows PowerShell prompt, not from inside an SSH session. Include the correct private-key path with `-i`.

## Credential rotation

### Rotate the OpenAI API key

1. Revoke the old key at the provider.
2. Create a new environment-specific key.
3. Edit `/opt/nevgiu/deploy/.env` as `deploy` and replace only `OPENAI_API_KEY`.
4. Recreate the backend:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env up -d --force-recreate backend
```

5. Verify backend health and an AI operation.

### Rotate the PostgreSQL password

Generate a new password without sharing it:

```bash
openssl rand -hex 32
```

Open PostgreSQL as `deploy`:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env exec db psql -U hr_user -d hr_ai
```

At the PostgreSQL prompt:

```text
\password hr_user
\q
```

Update `POSTGRES_PASSWORD` in `/opt/nevgiu/deploy/.env` with the same value, then recreate the affected containers:

```bash
cd /opt/nevgiu/deploy
docker compose --env-file .env up -d --force-recreate db backend
docker compose --env-file .env ps
```

Confirm both services are healthy.

## Validated GitHub staging automation

The GitHub `staging` environment was configured and the first complete automated rollout was validated on 2 August 2026.

Configured non-secret variables:

```text
DEPLOY_HOST=141.94.33.197
DEPLOY_USER=deploy
```

Configured secret names, with values retained only by GitHub:

```text
DEPLOY_SSH_KEY
DEPLOY_KNOWN_HOSTS
GHCR_USERNAME
GHCR_TOKEN
```

Validation completed:

- CI backend, frontend, and container-build jobs passed.
- Commit-addressed backend and frontend images were published under the transferred repository's dynamic GHCR namespace.
- GitHub connected to the VPS using the dedicated `deploy` key and verified host key.
- The VPS authenticated to GHCR with a classic token limited to `read:packages`.
- The deployment script initialized `.images.env`, retained previous images for rollback, and brought the stack to a healthy state.
- GitHub smoke tests passed for the public frontend and backend health URLs.
- The deployed images received staging-validation aliases.
- A human functional test successfully generated and approved a job offer, ingested CV data, and evaluated a candidate against the approved job.

No OpenAI key, PostgreSQL password, private SSH key, GHCR token, or completed environment file was added to Git or documentation.

### Rollback boundary observed in staging

The deployment script records the current backend and frontend images before replacement. If database, backend, or frontend health does not pass within approximately three minutes, or Caddy is not running, it restores those application images and repeats internal health checks. The GitHub job remains failed so the attempted release is visible.

This does not restore PostgreSQL data or schema, `.env`, Compose or Caddy configuration, secrets, or other deployment files. Public HTTPS smoke tests run after the script returns; a failure at that stage currently leaves the new images running. Errors during Compose validation, pulling, or initial startup can also occur before automatic rollback begins.

Before enabling production releases, extend and test rollback so public smoke-test failures and recoverable pre-health failures restore the previous compatible application and configuration, then verify both internal health and external HTTPS. Database restoration must remain a separate, explicitly approved recovery operation.

## Completion criteria

Staging is ready for automated validation only when:

- Key-only SSH works for both `ubuntu` and `deploy`, while password and root login are disabled.
- UFW exposes only SSH, HTTP, and HTTPS.
- Docker and Compose work for `deploy` without `sudo`.
- The staging `.env` has mode `600` and contains only staging credentials.
- Both staging DNS names serve valid HTTPS.
- Database, backend, frontend, and Caddy health checks pass.
- Functional smoke tests and persistence checks pass with non-production data.
- The GitHub `staging` environment holds separate deployment credentials.

