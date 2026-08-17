#!/bin/sh
set -eu

if [ "$#" -eq 1 ] && [ "$1" = "--rollback" ]; then
  rollback_requested=true
elif [ "$#" -eq 2 ]; then
  rollback_requested=false
else
  echo "Usage: $0 <backend-image> <frontend-image> | --rollback" >&2
  exit 2
fi

deploy_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$deploy_dir"

if [ ! -r .env ]; then
  echo "Missing readable $deploy_dir/.env" >&2
  exit 1
fi

compose() {
  docker compose --env-file .env --env-file .images.env "$@"
}

rollback() {
  if [ ! -r .images.env.previous ]; then
    echo "No previous application image manifest is available for rollback" >&2
    return 1
  fi

  cp .images.env.previous .images.env.rollback
  chmod 600 .images.env.rollback
  mv .images.env.rollback .images.env

  if ! compose up -d --remove-orphans; then
    echo "Rollback startup failed; manual intervention is required" >&2
    return 1
  fi

  if wait_for_stack; then
    echo "Rollback completed" >&2
    compose ps
    return 0
  fi

  echo "Rollback health validation failed; manual intervention is required" >&2
  compose logs --tail 100 >&2 || true
  return 1
}

service_state() {
  container_id=$(compose ps -q "$1")
  if [ -z "$container_id" ]; then
    echo "missing"
    return
  fi

  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id"
}

wait_for_stack() {
  attempts=36
  while [ "$attempts" -gt 0 ]; do
    db_state=$(service_state db)
    redis_state=$(service_state redis)
    backend_state=$(service_state backend)
    frontend_state=$(service_state frontend)
    caddy_state=$(service_state caddy)

    if [ "$db_state" = "healthy" ] \
      && [ "$redis_state" = "healthy" ] \
      && [ "$backend_state" = "healthy" ] \
      && [ "$frontend_state" = "healthy" ] \
      && [ "$caddy_state" = "running" ]; then
      return 0
    fi

    attempts=$((attempts - 1))
    sleep 5
  done

  return 1
}

if [ "$rollback_requested" = true ]; then
  rollback
  exit $?
fi

backend_image=$1
frontend_image=$2

if [ ! -r .images.env ]; then
  printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$backend_image" "$frontend_image" > .images.env
  chmod 600 .images.env
fi

previous_backend_id=$(compose ps -q backend)
previous_frontend_id=$(compose ps -q frontend)
previous_backend=$(docker inspect --format '{{.Config.Image}}' "$previous_backend_id" 2>/dev/null || true)
previous_frontend=$(docker inspect --format '{{.Config.Image}}' "$previous_frontend_id" 2>/dev/null || true)

new_images=.images.env.new
printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$backend_image" "$frontend_image" > "$new_images"
chmod 600 "$new_images"

docker compose --env-file .env --env-file "$new_images" config --quiet
docker pull "$backend_image"
docker pull "$frontend_image"

if [ -n "$previous_backend" ] && [ -n "$previous_frontend" ]; then
  printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$previous_backend" "$previous_frontend" > .images.env.previous.new
  chmod 600 .images.env.previous.new
  mv .images.env.previous.new .images.env.previous
fi

mv "$new_images" .images.env

if ! compose up -d --remove-orphans; then
  echo "Deployment startup failed; attempting application rollback" >&2
  rollback || true
  exit 1
fi

if wait_for_stack; then
  compose ps
  exit 0
fi

echo "Deployment failed health validation; attempting application rollback" >&2
compose logs --tail 100 >&2 || true

rollback || true

exit 1
