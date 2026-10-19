#!/usr/bin/env bash
set -Eeuo pipefail

# Creates one application-consistent recovery set. The backend is paused so the
# database metadata cannot change while its corresponding original files are archived.
readonly DEPLOY_DIR="${DEPLOY_DIR:-/opt/nevgiu/deploy}"
readonly BACKUP_DIR="${BACKUP_DIR:-/opt/nevgiu/backups}"
readonly RECIPIENT_FILE="${RECIPIENT_FILE:-/etc/nevgiu/backup-recipient.txt}"
readonly BACKUP_ENVIRONMENT="${BACKUP_ENVIRONMENT:-production}"
readonly CV_VOLUME_NAME="${CV_VOLUME_NAME:-nevgiu-hr-ai_cv-originals}"
readonly S3_BUCKET="${S3_BUCKET:-nevgiu-hr-production-db-backups}"
readonly S3_ENDPOINT="${S3_ENDPOINT:-https://s3.gra.io.cloud.ovh.net}"
readonly AWS_PROFILE="${AWS_PROFILE:-nevgiu-hr-production-backup-writer}"

timestamp=$(date --utc +'%Y-%m-%dT%H-%M-%SZ')
year=$(date --utc +'%Y')
month=$(date --utc +'%m')
set_name="hr-ai-${BACKUP_ENVIRONMENT}-${timestamp}"
object_prefix="recovery-sets/${BACKUP_ENVIRONMENT}/${year}/${month}/${set_name}"
database_artifact="${BACKUP_DIR}/${set_name}-postgres.dump.age"
cv_artifact="${BACKUP_DIR}/${set_name}-cv-originals.tar.gz.age"
manifest_file="${BACKUP_DIR}/${set_name}-manifest.txt"
backend_stopped=false

# A persistent timer can fire soon after a manually started backup. The lock
# prevents two maintenance windows and two writers from producing the same set.
exec 9>/run/lock/nevgiu-paired-backup.lock
if ! flock --nonblock 9; then
  echo "Another paired backup is already running" >&2
  exit 1
fi

compose=(
  docker compose
  --project-directory "$DEPLOY_DIR"
  --env-file "${DEPLOY_DIR}/.env"
  --env-file "${DEPLOY_DIR}/.images.env"
  -f "${DEPLOY_DIR}/compose.yml"
)

restart_backend() {
  if [[ "$backend_stopped" == true ]]; then
    "${compose[@]}" start backend >/dev/null
    backend_stopped=false
  fi
}

cleanup() {
  exit_status=$?
  trap - EXIT
  if ! restart_backend; then
    echo "CRITICAL: backend restart failed during backup cleanup" >&2
    exit_status=1
  fi
  rm -f -- "$database_artifact" "$cv_artifact" "$manifest_file"
  exit "$exit_status"
}
trap cleanup EXIT

require_file() {
  if [[ ! -r "$1" ]]; then
    echo "Required file is not readable: $1" >&2
    exit 1
  fi
}

container_health() {
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$1"
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "This backup must run as root" >&2
  exit 1
fi

case "$BACKUP_ENVIRONMENT" in
  staging|production) ;;
  *) echo "BACKUP_ENVIRONMENT must be staging or production" >&2; exit 1 ;;
esac

require_file "${DEPLOY_DIR}/.env"
require_file "${DEPLOY_DIR}/.images.env"
require_file "$RECIPIENT_FILE"
command -v age >/dev/null
command -v aws >/dev/null
command -v docker >/dev/null
command -v flock >/dev/null

install -d -m 700 -o root -g root "$BACKUP_DIR"

# The helper image is checked before the maintenance window; the backup must not
# unexpectedly pull an image while application writes are paused.
docker image inspect alpine:3.20 >/dev/null
docker volume inspect "$CV_VOLUME_NAME" >/dev/null

db_container=$("${compose[@]}" ps -q db)
backend_container=$("${compose[@]}" ps -q backend)
if [[ -z "$db_container" || -z "$backend_container" ]]; then
  echo "PostgreSQL and backend containers must be running" >&2
  exit 1
fi
if [[ "$(container_health "$db_container")" != "healthy" ]]; then
  echo "PostgreSQL container is not healthy" >&2
  exit 1
fi
if [[ "$(container_health "$backend_container")" != "healthy" ]]; then
  echo "Backend container is not healthy" >&2
  exit 1
fi

echo "Pausing backend writes for recovery set ${set_name}"
"${compose[@]}" stop backend >/dev/null
backend_stopped=true

# Both plaintext streams go directly into age. No unencrypted dump or CV archive
# is ever written to the host filesystem.
"${compose[@]}" exec -T db sh -c \
  'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --compress=9 --no-owner --no-privileges' \
  | age --recipients-file "$RECIPIENT_FILE" --output "$database_artifact"

docker run --rm \
  --volume "${CV_VOLUME_NAME}:/source:ro" \
  alpine:3.20 \
  tar -C /source -czf - . \
  | age --recipients-file "$RECIPIENT_FILE" --output "$cv_artifact"

test -s "$database_artifact"
test -s "$cv_artifact"

restart_backend
backend_container=$("${compose[@]}" ps -q backend)
backend_health=""
for attempt in $(seq 1 24); do
  backend_health=$(container_health "$backend_container" 2>/dev/null || true)
  [[ "$backend_health" == "healthy" ]] && break
  sleep 5
done
if [[ "$backend_health" != "healthy" ]]; then
  echo "Backend did not become healthy after backup" >&2
  exit 1
fi

database_name=$(basename "$database_artifact")
cv_name=$(basename "$cv_artifact")
database_sha256=$(sha256sum "$database_artifact" | awk '{print $1}')
cv_sha256=$(sha256sum "$cv_artifact" | awk '{print $1}')
cat > "$manifest_file" <<EOF
format_version=1
created_at=${timestamp}
environment=${BACKUP_ENVIRONMENT}
database_artifact=${database_name}
database_sha256=${database_sha256}
cv_artifact=${cv_name}
cv_sha256=${cv_sha256}
EOF
chmod 600 "$database_artifact" "$cv_artifact" "$manifest_file"

upload() {
  aws s3 cp "$1" "s3://${S3_BUCKET}/${object_prefix}/$2" \
    --endpoint-url "$S3_ENDPOINT" \
    --profile "$AWS_PROFILE" \
    --only-show-errors
}

upload "$database_artifact" "$database_name"
upload "$cv_artifact" "$cv_name"

# Uploading the manifest last is the completion marker. Restore tooling ignores
# a partially uploaded prefix that has no manifest.
upload "$manifest_file" manifest.txt

echo "Encrypted paired recovery set uploaded: s3://${S3_BUCKET}/${object_prefix}/"
