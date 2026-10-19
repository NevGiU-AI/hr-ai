#!/usr/bin/env bash
set -Eeuo pipefail

# Restores a downloaded recovery set only into newly named, isolated targets.
# It deliberately refuses the live database and live original-file volume.
readonly DEPLOY_DIR="${DEPLOY_DIR:-/opt/nevgiu/deploy}"
readonly BACKUP_SET_DIR="${BACKUP_SET_DIR:?BACKUP_SET_DIR is required}"
readonly AGE_IDENTITY_FILE="${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE is required}"
readonly RESTORE_DATABASE="${RESTORE_DATABASE:?RESTORE_DATABASE is required}"
readonly RESTORE_VOLUME="${RESTORE_VOLUME:?RESTORE_VOLUME is required}"
readonly LIVE_CV_VOLUME_NAME="${CV_VOLUME_NAME:-nevgiu-hr-ai_cv-originals}"
readonly CONFIRMATION="${RESTORE_CONFIRMATION:-}"

compose=(
  docker compose
  --project-directory "$DEPLOY_DIR"
  --env-file "${DEPLOY_DIR}/.env"
  --env-file "${DEPLOY_DIR}/.images.env"
  -f "${DEPLOY_DIR}/compose.yml"
)

manifest="${BACKUP_SET_DIR}/manifest.txt"
if [[ "$CONFIRMATION" != "RESTORE_TO_ISOLATED_TARGETS" ]]; then
  echo "Set RESTORE_CONFIRMATION=RESTORE_TO_ISOLATED_TARGETS" >&2
  exit 1
fi
for required in "$manifest" "$AGE_IDENTITY_FILE"; do
  [[ -r "$required" ]] || { echo "Required file is not readable: $required" >&2; exit 1; }
done
[[ "$RESTORE_VOLUME" != "$LIVE_CV_VOLUME_NAME" ]] || { echo "Refusing live CV volume" >&2; exit 1; }

read_manifest_value() {
  sed -n "s/^$1=//p" "$manifest"
}

[[ "$(read_manifest_value format_version)" == "1" ]] || { echo "Unsupported manifest format" >&2; exit 1; }
database_name=$(read_manifest_value database_artifact)
database_sha256=$(read_manifest_value database_sha256)
cv_name=$(read_manifest_value cv_artifact)
cv_sha256=$(read_manifest_value cv_sha256)
[[ "$database_name" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ && "$cv_name" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] || {
  echo "Manifest contains an invalid artifact name" >&2
  exit 1
}
[[ "$database_sha256" =~ ^[a-f0-9]{64}$ && "$cv_sha256" =~ ^[a-f0-9]{64}$ ]] || {
  echo "Manifest contains an invalid SHA-256 digest" >&2
  exit 1
}
[[ "$RESTORE_DATABASE" =~ ^[a-zA-Z][a-zA-Z0-9_]*$ ]] || {
  echo "RESTORE_DATABASE must be a simple PostgreSQL identifier" >&2
  exit 1
}
database_artifact="${BACKUP_SET_DIR}/${database_name}"
cv_artifact="${BACKUP_SET_DIR}/${cv_name}"

[[ -f "$database_artifact" && -f "$cv_artifact" ]] || { echo "Recovery-set artifact is missing" >&2; exit 1; }
echo "${database_sha256}  ${database_artifact}" | sha256sum -c -
echo "${cv_sha256}  ${cv_artifact}" | sha256sum -c -

live_database=$("${compose[@]}" exec -T db sh -c 'printf %s "$POSTGRES_DB"')
database_user=$("${compose[@]}" exec -T db sh -c 'printf %s "$POSTGRES_USER"')
[[ "$RESTORE_DATABASE" != "$live_database" ]] || { echo "Refusing live database" >&2; exit 1; }
if "${compose[@]}" exec -T db psql --username "$database_user" --dbname postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = '${RESTORE_DATABASE}'" | grep -q 1; then
  echo "Restore database already exists: $RESTORE_DATABASE" >&2
  exit 1
fi
if docker volume inspect "$RESTORE_VOLUME" >/dev/null 2>&1; then
  echo "Restore volume already exists: $RESTORE_VOLUME" >&2
  exit 1
fi

temp_dir=$(mktemp -d)
cleanup() { rm -rf -- "$temp_dir"; }
trap cleanup EXIT
database_dump="${temp_dir}/database.dump"
cv_archive="${temp_dir}/cv-originals.tar.gz"

age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$database_dump" "$database_artifact"
age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$cv_archive" "$cv_artifact"
"${compose[@]}" exec -T db pg_restore --list < "$database_dump" >/dev/null
tar -tzf "$cv_archive" >/dev/null

"${compose[@]}" exec -T db createdb --username "$database_user" "$RESTORE_DATABASE"
"${compose[@]}" exec -T db pg_restore \
  --username "$database_user" \
  --dbname "$RESTORE_DATABASE" \
  --no-owner \
  --no-privileges \
  < "$database_dump"

docker volume create "$RESTORE_VOLUME" >/dev/null
docker run --rm \
  --volume "${RESTORE_VOLUME}:/target" \
  --volume "${temp_dir}:/restore:ro" \
  alpine:3.20 \
  tar -C /target -xzf /restore/cv-originals.tar.gz

echo "Isolated restore completed: database=${RESTORE_DATABASE} volume=${RESTORE_VOLUME}"
echo "Validate business counts and file references before explicitly removing these rehearsal targets."
