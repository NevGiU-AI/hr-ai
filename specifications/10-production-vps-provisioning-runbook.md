# Production VPS Provisioning and Operations Runbook

## Purpose

Provide the detailed, production-specific procedure for provisioning, securing, backing up, deploying, validating, and operating HR AI on OVHcloud. Production must promote the exact immutable images already validated in staging.

## Production inventory

| Item | Value |
| --- | --- |
| OVHcloud plan | VPS-2 |
| Operating system | Ubuntu Server 26.04 LTS |
| VPS location | `RBX` |
| Public IP | `141.94.94.198` |
| Frontend | `https://hr.nevgiuai.com` |
| Backend API | `https://api.hr.nevgiuai.com` |
| Initial administrator | `ubuntu` |
| Deployment account | `deploy` |
| VPS backup | OVHcloud Premium; seven rolling daily restore points |
| Database backup destination | Private S3-compatible Object Storage in `GRA` |
| Backup bucket | `nevgiu-hr-production-db-backups` |
| S3 endpoint | `https://s3.gra.io.cloud.ovh.net` |

The domain is authoritatively served by Squarespace DNS. Keep setup TTL at `300` seconds until production validation is stable, then use `3600`. Do not modify Google Workspace MX or TXT records.

## Production-specific rules

- Never reuse staging SSH keys, passwords, OpenAI keys, PostgreSQL credentials, backup credentials, or GitHub environment secrets.
- Store runtime secrets only in `/opt/nevgiu/deploy/.env`, owned by `deploy` with mode `600`.
- Set `INITIAL_IMPORT_ENABLED=false`; production must not automatically load built-in candidate data.
- Require a versioned release and protected GitHub `production` environment approval before deployment.
- Promote the exact image digest or immutable revision alias that passed staging; do not rebuild for production.
- `deploy` belongs to the root-equivalent `docker` group; its unattended key is a privileged production credential.
- Expose only SSH, HTTP, and HTTPS through UFW. PostgreSQL and backend container ports remain private.
- OVHcloud Premium whole-VPS recovery does not replace encrypted, application-consistent PostgreSQL backups stored outside `RBX`.
- Do not accept real candidate data until backup restoration, rollback, privacy, log-redaction, and access-control checks are complete.

## Production procedure — in progress

This section records the production procedure as it is validated. Production uses public IP `141.94.94.198`, the OVH-provided `ubuntu` account for administration, and a dedicated `deploy` account for application delivery. Never reuse staging keys, passwords, API keys, database credentials, or environment files.

Commands labelled **local PowerShell** run on the administrator's Windows workstation. Commands labelled **production VPS** run only after connecting to `141.94.94.198`.

### 1. Connect with the initial administrative user

From local PowerShell:

```powershell
ssh ubuntu@141.94.94.198
```

Verify the operating system on the production VPS:

```bash
hostname
cat /etc/os-release
```

Validated operating system: Ubuntu 26.04 LTS (`resolute`). The `ubuntu` account remains the human administrative and recovery account; it is not used by GitHub Actions.

### 2. Update and reboot the clean VPS

On the production VPS:

```bash
sudo apt update
sudo apt full-upgrade -y
sudo reboot
```

`apt update` refreshes package metadata. `apt full-upgrade` applies available updates and may add or remove dependencies when required for kernel or system upgrades. `-y` automatically confirms the package-manager prompt; use it only after accepting that behavior for this newly provisioned server. `sudo reboot` disconnects SSH while the updated system restarts.

After approximately one minute, reconnect from local PowerShell:

```powershell
ssh ubuntu@141.94.94.198
```

### 3. Create the production deployment account

On the production VPS as `ubuntu`:

```bash
sudo adduser --disabled-password --gecos "" deploy
sudo mkdir -p /home/deploy/.ssh
sudo chown -R deploy:deploy /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh
```

- `--disabled-password` prevents password authentication for `deploy`; access requires an installed SSH public key.
- `--gecos ""` avoids interactive profile questions.
- `/home/deploy/.ssh` stores SSH authorization data.
- Ownership is assigned to `deploy`, and mode `700` allows only that user to read, modify, or enter the directory.

An attempt by `ubuntu` to write `/home/deploy/.ssh/authorized_keys` directly returns `Permission denied` by design. Do not weaken the directory permissions; use `sudo` for the controlled installation step.

### 4. Generate a production-only deployment key

From local PowerShell, outside the SSH session:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\nevgiu_hr_production"
```

This creates:

```text
nevgiu_hr_production      private key; never copy to the VPS or repository
nevgiu_hr_production.pub  public key; install on the VPS
```

The deployment key is separate from staging. For unattended GitHub Actions, it is generated without a passphrase unless a supported non-interactive key-agent design is introduced. The private key must therefore be protected as a production credential and will later be stored as a GitHub production environment secret.

### 5. Transfer and install only the public key

From local PowerShell:

```powershell
scp "$env:USERPROFILE\.ssh\nevgiu_hr_production.pub" ubuntu@141.94.94.198:/tmp/deploy-production-key.pub
```

This copies only the public half to a temporary location accessible to `ubuntu`. Then, on the production VPS as `ubuntu`:

```bash
sudo cp /tmp/deploy-production-key.pub /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo rm /tmp/deploy-production-key.pub
```

`authorized_keys` lists keys permitted to authenticate as `deploy`. Ownership must be `deploy:deploy`; mode `600` allows only `deploy` to read or modify it. The temporary copy is removed after installation.

### 6. Verify production deployment access

Keep the administrative session open and test from a second local PowerShell window:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_production" deploy@141.94.94.198
```

On the VPS:

```bash
whoami
hostname
```

The expected user is `deploy`, and authentication must succeed without a `deploy` password. This check was completed successfully. Global SSH hardening is deliberately postponed until both administrative recovery access and deployment access are independently verified.

### 7. Install Docker Engine and Compose

Keep the administrative `ubuntu` session open. If a separate `deploy` test session is open, exit only that test session:

```bash
exit
```

On the production VPS as `ubuntu`:

```bash
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker deploy
```

- `docker.io` installs the Ubuntu-packaged Docker Engine.
- `docker-compose-v2` installs the `docker compose` CLI plugin used by the deployment scripts.
- `systemctl enable --now docker` starts Docker immediately and configures it to start after future reboots.
- `usermod -aG docker deploy` appends `deploy` to the `docker` group without replacing its other group memberships.

Docker group membership permits control of the Docker daemon and is effectively root-equivalent. Treat the production deployment key and GitHub production environment secret as privileged credentials.

Group membership is evaluated at login. Keep the `ubuntu` recovery session open and connect again from a new local PowerShell window:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_production" deploy@141.94.94.198
```

Verify Docker and Compose without `sudo`:

```bash
docker version
docker compose version
```

Both commands succeeded. No production application containers were started at this stage.

### 8. Configure independent administrative key access

Production uses a separate, passphrase-protected human administrative key. Do not reuse the unattended `deploy` key for routine `ubuntu` administration.

From local PowerShell:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin"
scp "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin.pub" ubuntu@141.94.94.198:/tmp/production-admin-key.pub
```

The private administrative key remains on the administrator workstation and is protected by a strong passphrase. Only its `.pub` half is transferred.

On the production VPS in the existing `ubuntu` session:

```bash
sudo mkdir -p /home/ubuntu/.ssh
sudo touch /home/ubuntu/.ssh/authorized_keys
sudo sh -c 'cat /tmp/production-admin-key.pub >> /home/ubuntu/.ssh/authorized_keys'
sudo chown -R ubuntu:ubuntu /home/ubuntu/.ssh
sudo chmod 700 /home/ubuntu/.ssh
sudo chmod 600 /home/ubuntu/.ssh/authorized_keys
sudo rm /tmp/production-admin-key.pub
```

The public key is appended rather than copied over `authorized_keys`, preserving any existing OVH-authorized recovery key. SSH requires `ubuntu` ownership, mode `700` on `.ssh`, and mode `600` on `authorized_keys`.

Keep the original session open and verify from a second local PowerShell window:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin" ubuntu@141.94.94.198
```

On the VPS:

```bash
whoami
sudo -v
```

Both key authentication and administrative `sudo` validation succeeded. Production now has separate human-administration and unattended-deployment keys.

### 9. Enable the production firewall

Keep the verified administrative and deployment sessions available while changing firewall rules. On the production VPS as `ubuntu`:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status verbose
```

- `OpenSSH` is the UFW application profile for standard SSH port `22`.
- Port `80` is required for HTTP-to-HTTPS redirects and ACME certificate challenges.
- Port `443` is required for public HTTPS.
- `--force` enables UFW without an interactive confirmation prompt.

Keeping SSH on port `22` is safe when key-only authentication, disabled root login, strict key permissions, monitoring, and rotation are enforced. Moving SSH to a nonstandard port reduces automated log noise but does not prevent port scanning and would add workflow and recovery complexity.

After enabling UFW, verify both access paths from separate local PowerShell windows:

```powershell
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin" ubuntu@141.94.94.198
ssh -i "$env:USERPROFILE\.ssh\nevgiu_hr_production" deploy@141.94.94.198
```

Both connections succeeded through the active firewall. Do not expose PostgreSQL, backend port `8080`, or any Docker service port directly.

Validated UFW state:

```text
Status: active
Logging: on (low)
Default: deny incoming, allow outgoing, deny routed
Allowed: OpenSSH/22, HTTP/80, HTTPS/443
Address families: IPv4 and IPv6
Startup: enabled
```

The repeated `Rules updated` messages report creation of the corresponding IPv4 and IPv6 rules; they do not indicate additional application ports. `ufw status verbose` is the authoritative effective-rule summary.

### 10. Disable password and root SSH login

Keep all verified sessions open during this change. On the production VPS as `ubuntu`:

```bash
printf '%s\n' \
  'PasswordAuthentication no' \
  'KbdInteractiveAuthentication no' \
  'PermitRootLogin no' \
  'PubkeyAuthentication yes' \
  | sudo tee /etc/ssh/sshd_config.d/00-nevgiu-hardening.conf
```

Validate syntax before reloading:

```bash
sudo sshd -t
```

No output means the syntax is valid. Only then apply it without terminating existing sessions:

```bash
sudo systemctl reload ssh
```

Inspect the effective configuration:

```bash
sudo sshd -T | grep -E 'passwordauthentication|kbdinteractiveauthentication|permitrootlogin|pubkeyauthentication'
```

Expected values:

```text
permitrootlogin no
pubkeyauthentication yes
passwordauthentication no
kbdinteractiveauthentication no
```

The corrected production configuration returned all four expected effective values.

The first production attempt used `99-nevgiu-hardening.conf`. Syntax validation succeeded, but the effective result still showed `passwordauthentication yes` because Ubuntu's earlier cloud-init drop-in was evaluated first. OpenSSH uses the first obtained value for a setting, so a lexically later file does not necessarily override it. The corrected `00-` filename loads before `50-cloud-init.conf`.

If the old file exists, replace it rather than keeping conflicting NevGiU drop-ins:

```bash
sudo rm -f /etc/ssh/sshd_config.d/99-nevgiu-hardening.conf
```

After the expected effective values appear, fresh key-based sessions for both `ubuntu` and `deploy` were tested successfully. A password-only attempt with public-key and keyboard-interactive authentication disabled returned `Permission denied (publickey)`. Production SSH hardening is complete while the original recovery session remains available.

### 11. Set the production hostname and prepare directories

After completing the staging SSH audit, return to the production `ubuntu` session:

```bash
sudo hostnamectl set-hostname nevgiu-hr-production
sudo mkdir -p /opt/nevgiu/deploy
sudo mkdir -p /opt/nevgiu/backups
sudo chown -R deploy:deploy /opt/nevgiu
sudo chmod 755 /opt/nevgiu
```

- `hostnamectl` gives the server an environment-specific operational identity.
- `/opt/nevgiu/deploy` stores the tracked deployment bundle plus private `.env` and generated `.images.env` files.
- `/opt/nevgiu/backups` is reserved for local backup staging; durable backups must also be encrypted and copied off the VPS.
- Recursive `deploy:deploy` ownership lets the deployment account update its bundle and operate Compose without `sudo`.
- Mode `755` permits directory traversal while restricting modifications to the owner.

Verify:

```bash
hostname
ls -ld /opt/nevgiu /opt/nevgiu/deploy /opt/nevgiu/backups
```

The hostname returned `nevgiu-hr-production`, and all three application paths were confirmed with `deploy` ownership.

### 12. Configure and verify production DNS

In Squarespace DNS for `nevgiuai.com`, create:

| Type | Squarespace host | Fully qualified name | Target | Setup TTL |
| --- | --- | --- | --- | --- |
| A | `hr` | `hr.nevgiuai.com` | `141.94.94.198` | `300` |
| A | `api.hr` | `api.hr.nevgiuai.com` | `141.94.94.198` | `300` |

Do not alter Google Workspace MX or TXT records. Use the five-minute TTL while provisioning so an incorrect address can be corrected quickly.

Verify from local PowerShell:

```powershell
Resolve-DnsName hr.nevgiuai.com
Resolve-DnsName api.hr.nevgiuai.com
```

Both production names resolved to `141.94.94.198`. Keep TTL `300` until Caddy HTTPS, automated deployment, smoke tests, and functional validation succeed; then increase it to `3600`.

### 13. Copy the tracked deployment bundle

Use the active, current repository checkout. Repository transfers do not rename existing local directories, so verify the checkout through `git remote -v` and `git status` instead of relying on its folder name. Do not copy deployment files from a stale or unsynchronized clone.

From local PowerShell at the active repository root:

```powershell
scp -i "$env:USERPROFILE\.ssh\nevgiu_hr_production" `
  "deploy/.env.example" `
  "deploy/Caddyfile" `
  "deploy/compose.yml" `
  "deploy/deploy.sh" `
  "deploy/README.md" `
  deploy@141.94.94.198:/opt/nevgiu/deploy/
```

This copies only tracked, non-secret deployment files. Verify as `deploy`:

```bash
ls -la /opt/nevgiu/deploy
```

Expected files are `.env.example`, `Caddyfile`, `compose.yml`, `deploy.sh`, and `README.md`. Do not copy staging `.env`, `.images.env`, credentials, volumes, or data.

### 14. Create and validate the private production environment

On the production VPS as `deploy`:

```bash
cd /opt/nevgiu/deploy
cp .env.example .env
chmod 600 .env
openssl rand -hex 32
nano .env
```

Store the generated PostgreSQL password in the approved password manager and do not transmit it through chat, logs, screenshots, or documentation. Configure production-only values:

```env
FRONTEND_HOST=hr.nevgiuai.com
API_HOST=api.hr.nevgiuai.com
FRONTEND_URL=https://hr.nevgiuai.com
ACME_EMAIL=<monitored operational email>

POSTGRES_DB=hr_ai
POSTGRES_USER=hr_user
POSTGRES_PASSWORD=<new production-only password>
OPENAI_API_KEY=<production-only OpenAI key>

SPRING_JPA_HIBERNATE_DDL_AUTO=update
INITIAL_IMPORT_ENABLED=false
```

Remove the staging-manual `BACKEND_IMAGE` and `FRONTEND_IMAGE` lines from production `.env`. The release workflow supplies immutable production image references through the generated `.images.env` file.

Verify permissions without displaying content:

```bash
stat -c '%U:%G %a %n' .env
```

Expected: `deploy:deploy 600 .env`. Validate required values without printing them:

```bash
for name in \
  FRONTEND_HOST API_HOST FRONTEND_URL ACME_EMAIL \
  POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD OPENAI_API_KEY \
  SPRING_JPA_HIBERNATE_DDL_AUTO INITIAL_IMPORT_ENABLED
do
  grep -q "^${name}=.\+" .env || echo "Missing or empty: $name"
done
```

Validate environment-specific safety settings:

```bash
grep -q '^FRONTEND_HOST=hr\.nevgiuai\.com$' .env &&
grep -q '^API_HOST=api\.hr\.nevgiuai\.com$' .env &&
grep -q '^FRONTEND_URL=https://hr\.nevgiuai\.com$' .env &&
grep -q '^INITIAL_IMPORT_ENABLED=false$' .env &&
! grep -q 'staging' .env &&
! grep -q 'staging-manual' .env &&
echo "Production environment validation passed"
```

Permissions and all safety checks passed. No environment value was added to Git or displayed during validation. Do not start Compose until logging, backup readiness, GitHub production protection, and release prerequisites are addressed.

### 15. Configure bounded Docker log rotation

Before starting any production containers, check whether Docker already has daemon configuration:

```bash
sudo test -e /etc/docker/daemon.json \
  && echo "Docker daemon configuration exists" \
  || echo "Docker daemon configuration is absent"
```

The clean production VPS reported that the file was absent. Create it as `ubuntu`:

```bash
printf '%s\n' \
  '{' \
  '  "log-driver": "local",' \
  '  "log-opts": {' \
  '    "max-size": "10m",' \
  '    "max-file": "5"' \
  '  }' \
  '}' \
  | sudo tee /etc/docker/daemon.json
```

This selects Docker's rotating `local` logging driver and bounds each container to approximately five 10 MB files. It prevents application logs from consuming the VPS disk without limit; it does not replace off-server aggregation or retention policy.

Validate and restart Docker before containers exist:

```bash
sudo dockerd --validate --config-file=/etc/docker/daemon.json
sudo systemctl restart docker
sudo systemctl is-active docker
```

Expected results are `configuration OK` and `active`. Verify the effective default:

```bash
sudo docker info --format '{{.LoggingDriver}}'
```

Expected: `local`. Running `docker info` without `sudo` as `ubuntu` returns permission denied because only `deploy` belongs to the Docker group. This is intentional; do not add the human administrative account to that root-equivalent group. A fresh `deploy` session can run the same Docker command without `sudo`.

### 16. Bound system journal retention and access

On the production VPS as `ubuntu`:

```bash
sudo mkdir -p /etc/systemd/journald.conf.d
printf '%s\n' \
  '[Journal]' \
  'SystemMaxUse=500M' \
  'RuntimeMaxUse=100M' \
  'MaxRetentionSec=14day' \
  'Compress=yes' \
  | sudo tee /etc/systemd/journald.conf.d/00-nevgiu-retention.conf
```

- `SystemMaxUse=500M` bounds persistent journal storage.
- `RuntimeMaxUse=100M` bounds temporary runtime journal storage.
- `MaxRetentionSec=14day` removes entries older than fourteen days subject to journal rotation.
- `Compress=yes` compresses eligible older journal objects.

Apply and verify:

```bash
sudo systemctl restart systemd-journald
sudo systemctl is-active systemd-journald
sudo systemd-analyze cat-config systemd/journald.conf |
  grep -E 'SystemMaxUse|RuntimeMaxUse|MaxRetentionSec|Compress'
sudo journalctl --disk-usage
```

The service returned `active`, and all four configured limits appeared in the effective configuration.

Verify system-log group membership:

```bash
id deploy
getent group adm
getent group systemd-journal
```

Validated state:

```text
deploy groups: deploy, users, docker
adm explicit member: syslog
systemd-journal explicit members: none
```

`deploy` has not been added to `adm` or `systemd-journal`. Its Docker membership is nevertheless root-equivalent, so the deployment key remains privileged. Local rotation and access boundaries do not replace encrypted off-server log collection, redaction, alerting, or an incident-export policy.

### 17. Prepare private off-server PostgreSQL backup storage

The production VPS is located in OVHcloud `RBX`. PostgreSQL backups must therefore be copied to a private S3-compatible Object Storage bucket in a different region; `GRA` was selected to avoid keeping the operational server and its database backups in the same location.

Selected bucket safeguards:

- S3-compatible Object Storage in `GRA`.
- Private visibility; anonymous or public access is prohibited.
- Version management enabled to recover from accidental replacement or deletion.
- Object Lock enabled when the bucket is created because it cannot be added later.
- Governance retention targeted at seven days; do not use Compliance mode without an approved legal and data-retention requirement.
- No Legal Hold by default.
- Lifecycle rules will remove expired current versions, non-current versions, and delete markers according to the approved candidate-data retention policy.

Object Lock automatically enables versioning. Governance mode protects backups against routine deletion while permitting a specifically authorized recovery administrator to bypass retention when deletion is legally required. Compliance mode is deliberately excluded because even administrators cannot delete protected CV-related data during its retention period.

Validated Control Panel state on 2026-08-04:

- Bucket: `nevgiu-hr-production-db-backups`.
- Region: Gravelines (`GRA`), separate from the `RBX` production VPS.
- Endpoint: `https://s3.gra.io.cloud.ovh.net`.
- Version management: enabled.
- Object Lock: enabled.
- Default mode: Governance.
- The Control Panel incorrectly changed a selected seven-day duration to one year and did not allow an unconfigured default. The bucket remains empty. Do not upload backups until an authenticated S3 API call replaces the bucket default with `Days: 7` and a follow-up read verifies the effective configuration.

Create a dedicated Object Storage service user named `hr-production-backup` with the `ObjectStore Operator` role. Do not reuse the OVHcloud administrator, a personal identity, or staging credentials. After bucket creation, restrict this identity to only the production backup bucket using an OVHcloud IAM user policy or bucket policy; the initial project-wide operator role is broader than the steady-state requirement.

The generated access key and secret access key are production credentials. Save them immediately in the approved password manager because the secret may only be displayed once. Never place them in Git, Markdown, screenshots, terminal transcripts, chat, Compose output, or GitHub Actions. They will be installed in a root-readable credential file on the production VPS and used only by the database backup process.

Provisioning this bucket does not complete backup readiness. Before production receives real candidate data, the remaining work is to:

1. Replace the erroneous one-year Control Panel default with seven-day Governance retention through the S3 API and verify it before uploading any object.
2. Restrict the service user to that bucket.
3. Generate and protect a client-side encryption recovery key.
4. Install an application-consistent PostgreSQL dump and encrypted upload script.
5. Schedule it and monitor failures.
6. Configure lifecycle retention.
7. Restore a backup into an isolated PostgreSQL instance and record the result.

Official references:

- [OVHcloud Object Lock guide](https://help.ovhcloud.com/csm/de-public-cloud-storage-s3-managing-object-lock?id=kb_article_view&sysparm_article=KB0034736)
- [OVHcloud Object Storage versioning guide](https://help.ovhcloud.com/csm/es-public-cloud-storage-s3-versioning?id=kb_article_view&sysparm_article=KB0063862)
- [OVHcloud Object Storage lifecycle guide](https://help.ovhcloud.com/csm/en-public-cloud-storage-s3-bucket-lifecycle?id=kb_article_view&sysparm_article=KB0066009)

The next production step is verifying bucket configuration and securely connecting the production VPS before GitHub production deployment is enabled.

## Production checklist

Repeat the staging procedure with these changes:

- [x] Use public IP `141.94.94.198`.
- [x] Set hostname `nevgiu-hr-production`.
- [x] Generate a separate production deployment SSH key.
- [x] Do not copy staging private keys or credentials.
- [x] Install Docker Engine and Docker Compose and verify access as `deploy` without `sudo`.
- [x] Configure and verify a separate passphrase-protected production administrative key for `ubuntu`.
- [x] Enable UFW for OpenSSH, HTTP, and HTTPS and verify both production SSH keys still connect.
- [x] Verify production effective SSH settings disable password, keyboard-interactive, and root login while allowing public keys.
- [x] Audit and correct the staging SSH drop-in ordering; verify both key paths and rejection of password-only login.
- [x] Create Squarespace A records `hr` and `api.hr` targeting the production IP.
- [x] Verify `hr.nevgiuai.com` and `api.hr.nevgiuai.com` resolve correctly.
- [x] Use production-only OpenAI and PostgreSQL credentials.
- [x] Set `FRONTEND_HOST=hr.nevgiuai.com`.
- [x] Set `API_HOST=api.hr.nevgiuai.com`.
- [x] Set `FRONTEND_URL=https://hr.nevgiuai.com`.
- [x] Set `INITIAL_IMPORT_ENABLED=false`.
- [x] Order OVHcloud Premium automated backup for seven rolling daily VPS restore points.
- [x] Confirm OVHcloud Premium automated backup is active in the Control Panel.
- [ ] Verify daily restore points begin appearing in the OVHcloud Control Panel.
- [x] Verify the private `GRA` backup bucket endpoint, versioning, and Object Lock settings.
- [ ] Replace the erroneous one-year Governance default with seven days through the S3 API and verify the result before uploading data.
- [ ] Restrict the `hr-production-backup` service user to the production backup bucket.
- [ ] Configure encrypted off-server PostgreSQL backups and test restoration.
- [ ] Configure the GitHub `production` environment with separate variables and secrets.
- [ ] Require production deployment approval where supported.
- [ ] Verify public HTTPS smoke-test failure automatically restores the previous production images.
- [ ] Verify recoverable Compose validation, pull, and startup failures enter the rollback path.
- [ ] Version and restore compatible Compose and Caddy configuration during rollback.
- [ ] Run a controlled staging rollback drill before enabling the production release workflow.
- [x] Configure Docker container log rotation with a documented per-container disk limit.
- [x] Configure bounded systemd journal retention and verify restricted system-log access.
- [ ] Configure restricted, encrypted off-server production log collection and retention.
- [ ] Verify logs exclude CV text, prompts, credentials, tokens, and unnecessary personal data before accepting real candidates.
- [ ] Document who may access or export production logs and how incident extracts are redacted.
- [ ] Do not perform the first production deployment until a release commit has a successful staging-validation image alias.
- [ ] Run production-safe HTTPS and read-only smoke tests after deployment.

## Completion criteria

A VPS is ready for GitHub Actions only when:

- Key-only SSH works for `deploy`.
- Administrative recovery access works for `ubuntu`.
- Password and root SSH login are disabled.
- UFW exposes only SSH, HTTP, and HTTPS.
- Docker and Compose work for `deploy` without `sudo`.
- `/opt/nevgiu/deploy/.env` exists, belongs to `deploy`, has mode 600, and contains only that environment's credentials.
- PostgreSQL and backend ports are not public.
- Both DNS names resolve to the correct VPS.
- Caddy serves valid HTTPS.
- Database, backend, and frontend are healthy; Caddy is running.
- Functional smoke tests pass and persistence survives a container restart.
- GitHub environment host, SSH, known-host, and GHCR pull credentials are configured separately for that environment.
