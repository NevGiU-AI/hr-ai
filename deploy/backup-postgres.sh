#!/usr/bin/env bash
set -Eeuo pipefail

readonly DEPLOY_DIR="${DEPLOY_DIR:-/opt/nevgiu/deploy}"
readonly BACKUP_DIR="${BACKUP_DIR:-/opt/nevgiu/backups}"
readonly RECIPIENT_FILE="${RECIPIENT_FILE:-/etc/nevgiu/backup-recipient.txt}"
readonly S3_BUCKET="${S3_BUCKET:-nevgiu-hr-production-db-backups}"
readonly S3_ENDPOINT="${S3_ENDPOINT:-https://s3.gra.io.cloud.ovh.net}"
readonly AWS_PROFILE="${AWS_PROFILE:-nevgiu-hr-production-backup-writer}"

timestamp=$(date --utc +'%Y-%m-%dT%H-%M-%SZ')
year=$(date --utc +'%Y')
month=$(date --utc +'%m')
object_key="postgresql/${year}/${month}/hr-ai-${timestamp}.dump.age"
encrypted_backup="${BACKUP_DIR}/.${timestamp}.dump.age"
checksum_file="${encrypted_backup}.sha256"

cleanup() {
  rm -f -- "$encrypted_backup" "$checksum_file"
}
trap cleanup EXIT

require_file() {
  if [[ ! -r "$1" ]]; then
    echo "Required file is not readable: $1" >&2
    exit 1
  fi
}

if [[ "${EUID}" -ne 0 ]]; then
  echo "This backup must run as root" >&2
  exit 1
fi

require_file "${DEPLOY_DIR}/.env"
require_file "${DEPLOY_DIR}/.images.env"
require_file "$RECIPIENT_FILE"

install -d -m 700 -o root -g root "$BACKUP_DIR"

compose=(
  docker compose
  --project-directory "$DEPLOY_DIR"
  --env-file "${DEPLOY_DIR}/.env"
  --env-file "${DEPLOY_DIR}/.images.env"
  -f "${DEPLOY_DIR}/compose.yml"
)

db_container=$("${compose[@]}" ps -q db)
if [[ -z "$db_container" ]]; then
  echo "PostgreSQL container is not running" >&2
  exit 1
fi

db_health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$db_container")
if [[ "$db_health" != "healthy" ]]; then
  echo "PostgreSQL container is not healthy: $db_health" >&2
  exit 1
fi

echo "Creating encrypted PostgreSQL backup for ${timestamp}"
"${compose[@]}" exec -T db sh -c \
  'exec pg_dump --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --format=custom --compress=9 --no-owner --no-privileges' \
  | age --recipients-file "$RECIPIENT_FILE" --output "$encrypted_backup"

if [[ ! -s "$encrypted_backup" ]]; then
  echo "Encrypted backup is empty" >&2
  exit 1
fi

sha256sum "$encrypted_backup" | awk '{print $1}' > "$checksum_file"

aws s3 cp \
  "$encrypted_backup" \
  "s3://${S3_BUCKET}/${object_key}" \
  --endpoint-url "$S3_ENDPOINT" \
  --profile "$AWS_PROFILE" \
  --only-show-errors

aws s3 cp \
  "$checksum_file" \
  "s3://${S3_BUCKET}/${object_key}.sha256" \
  --endpoint-url "$S3_ENDPOINT" \
  --profile "$AWS_PROFILE" \
  --only-show-errors

echo "Encrypted PostgreSQL backup uploaded: s3://${S3_BUCKET}/${object_key}"
