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
- Governance retention confirmed at seven days through the authenticated S3 API; do not use Compliance mode without an approved legal and data-retention requirement.
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
- The Control Panel displayed one year after seven days was selected, but an authenticated `get-object-lock-configuration` call through the S3 endpoint confirmed the effective default is `GOVERNANCE` with `Days: 7`. The discrepancy was a Control Panel display bug; the API result is authoritative.

The reproducible Control Panel path is:

1. Open `Public Cloud` and select the dedicated `NevGiU AI Production Backups` project.
2. Open `Storage` > `Object Storage`.
3. Create the private S3-compatible container `nevgiu-hr-production-db-backups` in Gravelines (`GRA`) using Standard storage.
4. Enable version management and Object Lock during creation; Object Lock cannot be added later.
5. Select Governance mode and seven days. Do not select Compliance or Legal Hold.
6. After creation, use the authenticated S3 API—not the displayed duration alone—to verify the effective configuration.

The project home page also displays tiles for instances, Kubernetes, block storage, networks, and databases. Labels such as `Create an instance` are service shortcuts, not evidence that those resources exist. Keep this project dedicated to production backups.

The bucket lives in the dedicated Public Cloud project `NevGiU AI Production Backups`. The project contains no unrelated resources; the service tiles on its home page are creation shortcuts rather than running instances, databases, networks, or volumes.

Two Object Storage identities separate administration from unattended upload:

| Identity | Effective purpose | Credential location |
| --- | --- | --- |
| `hr-production-backup` / `user-DhAjQNmpDxX7` | Bucket owner, retention administration, and controlled recovery | Approved password manager and an administrator's local AWS CLI profile only |
| `hr-production-backup-writer` | Automated production backup uploads | Approved password manager initially; later a root-readable production VPS credential file |

The owner description cannot be renamed in the Control Panel. Treat it operationally as the backup administrator and never install its credentials on the VPS. The writer was created separately, enabled for S3 in `Object Storage` > `Users`, and assigned an imported S3 user policy limited to:

- `s3:GetBucketLocation`, `s3:ListBucket`, and `s3:ListBucketMultipartUploads` on `arn:aws:s3:::nevgiu-hr-production-db-backups`.
- `s3:PutObject`, `s3:AbortMultipartUpload`, and `s3:ListMultipartUploadParts` on `arn:aws:s3:::nevgiu-hr-production-db-backups/*`.

The writer policy deliberately excludes object download and deletion, ACL changes, retention changes, and bucket administration. A local writer profile successfully completed `head-bucket`, while `get-object-lock-configuration` returned the expected `AccessDenied`. This proves required bucket access and rejected retention administration. Keep the dedicated project free of unrelated resources as an additional isolation boundary.

In the current Control Panel, create project identities under `Project management` > `Users & Roles`. S3 enablement, credentials, and JSON policies are managed separately under `Storage` > `Object Storage` > `Users`. A project user initially shows `S3 disabled`; enable S3 there before retrieving its access key and secret key. Import the following policy into the writer only, never the bucket owner:

```json
{
  "Statement": [
    {
      "Sid": "ListProductionBackupBucket",
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads"
      ],
      "Resource": [
        "arn:aws:s3:::nevgiu-hr-production-db-backups"
      ]
    },
    {
      "Sid": "UploadProductionBackups",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:AbortMultipartUpload",
        "s3:ListMultipartUploadParts"
      ],
      "Resource": [
        "arn:aws:s3:::nevgiu-hr-production-db-backups/*"
      ]
    }
  ]
}
```

The owner was configured locally as `nevgiu-hr-production-backup`. The writer uses a distinct profile:

```powershell
msiexec.exe /i https://awscli.amazonaws.com/AWSCLIV2-User.msi

aws configure --profile nevgiu-hr-production-backup-writer
```

For each profile, enter the corresponding access key and secret key interactively, region `gra`, and output format `json`. Never place credentials directly in a command because shell history may retain them. Verify the writer's allowed operation:

```powershell
aws s3api head-bucket `
  --bucket nevgiu-hr-production-db-backups `
  --endpoint-url https://s3.gra.io.cloud.ovh.net `
  --profile nevgiu-hr-production-backup-writer
```

Success produces no output. Verify rejected administration:

```powershell
aws s3api get-object-lock-configuration `
  --bucket nevgiu-hr-production-db-backups `
  --endpoint-url https://s3.gra.io.cloud.ovh.net `
  --profile nevgiu-hr-production-backup-writer
```

Expected: `AccessDenied`. Use the offline owner profile for controlled retention inspection:

```powershell
aws s3api get-object-lock-configuration `
  --bucket nevgiu-hr-production-db-backups `
  --endpoint-url https://s3.gra.io.cloud.ovh.net `
  --profile nevgiu-hr-production-backup
```

The effective output must contain `ObjectLockEnabled`, mode `GOVERNANCE`, and `Days: 7`.

Both access-key pairs are production credentials. Save them in the approved password manager because a secret may only be displayed once. Never place them in Git, Markdown, screenshots, terminal transcripts, chat, Compose output, or GitHub Actions.

Provisioning this bucket does not complete backup readiness. Before production receives real candidate data, the remaining work is to:

1. Generate and protect a client-side encryption recovery key.
2. Install an application-consistent PostgreSQL dump and encrypted upload script.
3. Schedule it and monitor failures.
4. Configure lifecycle retention.
5. Restore a backup into an isolated PostgreSQL instance and record the result.

Official references:

- [OVHcloud Object Lock guide](https://help.ovhcloud.com/csm/de-public-cloud-storage-s3-managing-object-lock?id=kb_article_view&sysparm_article=KB0034736)
- [OVHcloud Object Storage versioning guide](https://help.ovhcloud.com/csm/es-public-cloud-storage-s3-versioning?id=kb_article_view&sysparm_article=KB0063862)
- [OVHcloud Object Storage lifecycle guide](https://help.ovhcloud.com/csm/en-public-cloud-storage-s3-bucket-lifecycle?id=kb_article_view&sysparm_article=KB0066009)

### 18. Generate and install the client-side encryption recipient

Use `age` for client-side backup encryption. Generate the recovery identity on the administrator workstation, protect it with a strong passphrase, and store both the encrypted identity file and its passphrase in approved recovery storage. Never copy the private identity or recovery passphrase to the VPS, Object Storage, Git, GitHub Actions, documentation, or chat.

Install the official Windows package and reopen PowerShell so `PATH` refreshes:

```powershell
winget install --id FiloSottile.age --exact
age --version
age-keygen --version
```

Validated local version: `1.3.1`. Generate the identity as an encrypted stream so an unencrypted private identity is never written to disk:

```powershell
$recoveryDir = Join-Path $env:USERPROFILE "Documents\NevGiU-Recovery"
New-Item -ItemType Directory -Force -Path $recoveryDir

age-keygen |
  age --passphrase --output "$recoveryDir\hr-production-backup-identity.age"
```

Enter a strong, unique passphrase interactively and store it separately in the approved password manager. Attach a protected copy of the encrypted identity file to the recovery record or retain another approved offline copy. Losing the encrypted file or its passphrase without a second protected copy makes all backups encrypted to this identity unrecoverable.

Derive the public recipient without writing an unencrypted private identity to disk:

```powershell
age --decrypt "$recoveryDir\hr-production-backup-identity.age" |
  age-keygen -y |
  Set-Content -Encoding ascii "$recoveryDir\hr-production-backup-recipient.txt"

$recipient = (Get-Content "$recoveryDir\hr-production-backup-recipient.txt" -Raw).Trim()

if ($recipient -match '^age1[0-9a-z]+$') {
    "Valid age recipient"
} else {
    "Invalid recipient - stop"
}
```

Expected: `Valid age recipient`. The decryption passphrase unlocks only the local encrypted recovery identity; do not confuse it with either production SSH-key passphrase.

The validated local artifacts are:

```text
hr-production-backup-identity.age   passphrase-encrypted private recovery identity
hr-production-backup-recipient.txt public recipient used only for encryption
```

Only the public recipient was copied with the passphrase-protected production administrative SSH key. It was installed on the production VPS as:

```text
root:root 644 /etc/nevgiu/backup-recipient.txt
```

Transfer only the public recipient from local PowerShell:

```powershell
scp -i "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin" `
  "$recoveryDir\hr-production-backup-recipient.txt" `
  ubuntu@141.94.94.198:/tmp/hr-production-backup-recipient.txt
```

The prompt here requests the production administrative SSH-key passphrase, not the `age` recovery passphrase. Install the public recipient on the VPS as `ubuntu`:

```bash
sudo install -d -m 700 -o root -g root /etc/nevgiu
sudo install -m 644 -o root -g root \
  /tmp/hr-production-backup-recipient.txt \
  /etc/nevgiu/backup-recipient.txt
rm /tmp/hr-production-backup-recipient.txt
```

PowerShell wrote the public recipient using a Windows CRLF line ending. The initial Linux regular-expression validation therefore produced no success message. The public file was normalized safely on the VPS:

```bash
sudo sed -i 's/\r$//' /etc/nevgiu/backup-recipient.txt
```

Validation then passed:

```bash
sudo grep -Eq '^age1[0-9a-z]+$' /etc/nevgiu/backup-recipient.txt \
  && echo "Production backup recipient is valid"
sudo stat -c '%U:%G %a %n' /etc/nevgiu/backup-recipient.txt
```

The public recipient can encrypt new backups but cannot decrypt existing backups. Recovery requires both the separately stored encrypted private identity and its passphrase. Losing either recovery component without another protected copy would make every encrypted database backup unrecoverable.

### 19. Install backup tools and the root-only writer profile

Install the encryption and S3 clients from Ubuntu packages:

```bash
sudo apt update
sudo apt install -y age awscli
age --version
aws --version
```

Validated production versions:

```text
age 1.2.1
aws-cli 2.31.35
```

The scheduled service runs as root because it must access the Docker API, the Compose-managed PostgreSQL container, the protected recipient file, and root-only Object Storage credentials. Configure only the upload-only writer profile under `/root/.aws`; never install the bucket-owner credentials on the VPS:

```bash
sudo install -d -m 700 -o root -g root /root/.aws
sudo aws configure --profile nevgiu-hr-production-backup-writer
sudo chmod 600 /root/.aws/credentials /root/.aws/config
```

Validated permissions:

```text
root:root 600 /root/.aws/credentials
root:root 600 /root/.aws/config
```

The root profile successfully completed `head-bucket` against `https://s3.gra.io.cloud.ovh.net`. Its `get-object-lock-configuration` request returned the expected `AccessDenied`, proving the unattended VPS identity can reach the bucket but cannot administer retention.

### 20. Install the tracked streaming backup service

The repository contains:

```text
deploy/backup-postgres.sh
deploy/systemd/nevgiu-postgres-backup.service
deploy/systemd/nevgiu-postgres-backup.timer
```

The script verifies the Compose database is healthy, streams a custom-format compressed `pg_dump` directly into `age`, writes only the temporary encrypted artifact and its SHA-256 checksum, uploads both with the writer profile, and removes its temporary files on every exit path. No plaintext database dump is written to disk.

From local PowerShell at the current repository root, copy the three tracked files to `/tmp` with the production administrative SSH key:

```powershell
scp -i "$env:USERPROFILE\.ssh\nevgiu_hr_production_admin" `
  "deploy/backup-postgres.sh" `
  "deploy/systemd/nevgiu-postgres-backup.service" `
  "deploy/systemd/nevgiu-postgres-backup.timer" `
  ubuntu@141.94.94.198:/tmp/
```

Install the script outside the deployment-writable directory so the root service never executes a mutable file from `/opt/nevgiu/deploy`:

```bash
sudo install -m 750 -o root -g root \
  /tmp/backup-postgres.sh \
  /usr/local/sbin/nevgiu-postgres-backup

sudo install -m 644 -o root -g root \
  /tmp/nevgiu-postgres-backup.service \
  /etc/systemd/system/nevgiu-postgres-backup.service

sudo install -m 644 -o root -g root \
  /tmp/nevgiu-postgres-backup.timer \
  /etc/systemd/system/nevgiu-postgres-backup.timer

rm \
  /tmp/backup-postgres.sh \
  /tmp/nevgiu-postgres-backup.service \
  /tmp/nevgiu-postgres-backup.timer

sudo systemctl daemon-reload
sudo systemd-analyze verify \
  /etc/systemd/system/nevgiu-postgres-backup.service \
  /etc/systemd/system/nevgiu-postgres-backup.timer
```

Expected permissions:

```text
root:root 750 /usr/local/sbin/nevgiu-postgres-backup
root:root 644 /etc/systemd/system/nevgiu-postgres-backup.service
root:root 644 /etc/systemd/system/nevgiu-postgres-backup.timer
```

The timer targets 02:30 UTC daily, adds up to thirty minutes of randomized delay, and uses `Persistent=true` so a missed run occurs after the VPS returns. Do not enable or start it until the production Compose stack exists, `.images.env` has been generated, a manual encrypted upload succeeds, and an isolated restoration test proves recoverability.

The script and units were installed with the expected root ownership and modes. `systemd-analyze verify` also printed warnings that `CPUAccounting=` is removed and ignored in Ubuntu's packaged `xfs_scrub_all.service` and `system-xfs_scrub.slice`. Those warnings originate from unrelated XFS maintenance units and do not invalidate the NevGiU backup service or timer. Verify their own load state directly:

```bash
sudo systemctl show \
  -p LoadState \
  -p UnitFileState \
  nevgiu-postgres-backup.service \
  nevgiu-postgres-backup.timer
```

Expected before activation: both units are `loaded` and `disabled`. The disabled state is intentional.

The next production step is installing and verifying these tracked service files without enabling the timer.

### 21. Credential and recovery-key rotation boundaries

Writer credential rotation must preserve service continuity without ever installing owner credentials on the VPS:

1. Generate replacement S3 credentials for the upload-only writer through the Object Storage `Users` interface and store them in the password manager.
2. Configure a temporary local AWS CLI profile and repeat both the allowed `head-bucket` test and denied Object Lock administration test.
3. Replace `/root/.aws/credentials` through an interactive root-owned configuration step without printing either key.
4. Run a manual encrypted backup and confirm upload success.
5. Revoke the previous writer credentials only after the new credentials pass validation.
6. Record the rotation date and operator without recording secret values.

If OVHcloud regenerates rather than adds credentials, arrange a controlled backup window because regeneration may revoke the existing pair immediately. Never rotate the bucket owner and writer simultaneously.

Recovery-identity rotation is different: changing the public recipient does not make old backups decryptable with the new identity. Before rotating it:

1. Confirm the old encrypted identity and passphrase remain recoverable for every retained old backup.
2. Generate and protect a new encrypted identity using the same no-plaintext procedure.
3. Install only its public recipient on the VPS.
4. Run and restore-test a new backup.
5. Keep the old recovery identity until every backup encrypted to it has expired and been deleted according to policy.

The precise lifecycle policy, manual backup verification, Object Lock inspection on uploaded objects, isolated restore procedure, monitoring, and timer activation remain pending. Add their validated commands and results to this runbook as those steps are performed; do not mark backup readiness complete merely because the script and timer files exist.

### 22. Configure rolling Object Storage lifecycle retention

The initial production policy keeps approximately thirty encrypted daily database recovery points. Seven-day Governance Object Lock prevents early deletion, while lifecycle expiration limits long-term storage of CV-related database data and controls cost.

Apply lifecycle changes only from local PowerShell with the offline bucket-owner profile. Never install owner credentials on the VPS. The validated policy applies only to the `postgresql/` prefix:

```json
{
  "Rules": [
    {
      "ID": "ExpireProductionDatabaseBackups",
      "Status": "Enabled",
      "Filter": {
        "Prefix": "postgresql/"
      },
      "Expiration": {
        "Days": 30
      },
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 1
      },
      "AbortIncompleteMultipartUpload": {
        "DaysAfterInitiation": 1
      }
    },
    {
      "ID": "RemoveExpiredDeleteMarkers",
      "Status": "Enabled",
      "Filter": {
        "Prefix": "postgresql/"
      },
      "Expiration": {
        "ExpiredObjectDeleteMarker": true
      }
    }
  ]
}
```

The configuration was accepted and read back successfully through the authenticated S3 API. Its effects are:

- Current encrypted backups and checksum objects expire after thirty days.
- A current-version expiration in the versioned bucket creates a delete marker; the underlying non-current version becomes eligible for removal after one additional day, subject to Object Lock.
- Incomplete multipart-upload fragments are removed after one day.
- Delete markers with no retained versions are cleaned up.

Object Lock takes precedence over lifecycle deletion. OVHcloud will not permanently delete a protected object version before its seven-day Governance retention expires. Longer weekly or monthly archives are deliberately postponed until candidate-data retention and deletion policy is formally approved.

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
- [x] Verify through the authenticated S3 API that the effective Object Lock default is seven days in Governance mode.
- [x] Separate the offline bucket-owner identity from the unattended upload-only writer identity.
- [x] Import and verify the writer policy: bucket access succeeds and Object Lock administration is denied.
- [x] Generate and protect the encrypted client-side recovery identity off-server.
- [x] Install and validate only the public `age` recipient on the production VPS.
- [x] Install `age` and AWS CLI and verify their production versions.
- [x] Install the upload-only writer credentials as a root-only AWS profile and recheck allowed and denied operations.
- [x] Install and validate the root-owned streaming backup script and systemd units without enabling the timer.
- [x] Configure and read back thirty-day lifecycle retention for encrypted PostgreSQL backups.
- [ ] Run a manual encrypted backup, verify Object Lock, and restore it in isolation before enabling the timer.
- [ ] Configure encrypted off-server PostgreSQL backups and test restoration.
- [x] Configure the GitHub `production` environment with separate variables and secrets.
- [x] Require production deployment approval where supported.
- [x] Configure public HTTPS smoke-test failure to restore the previous application images and verify the restored public endpoints.
- [x] Validate Compose and pull candidate application images before changing the active manifest; route initial startup failure through image rollback.
- [ ] Version and restore compatible Compose and Caddy configuration during rollback.
- [x] Run a controlled staging rollback drill before enabling the production release workflow.
- [x] Configure Docker container log rotation with a documented per-container disk limit.
- [x] Configure bounded systemd journal retention and verify restricted system-log access.
- [ ] Configure restricted, encrypted off-server production log collection and retention.
- [ ] Verify logs exclude CV text, prompts, credentials, tokens, and unnecessary personal data before accepting real candidates.
- [ ] Document who may access or export production logs and how incident extracts are redacted.
- [x] Do not perform the first production deployment until a release commit has a successful staging-validation image alias.
- [x] Run production-safe HTTPS and read-only smoke tests after deployment.

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
