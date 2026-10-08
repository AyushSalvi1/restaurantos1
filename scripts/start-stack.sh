#!/usr/bin/env bash
# Starts the full stack with Docker Compose and prints where to look.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required but was not found on PATH." >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "No .env found. Copy .env.example to .env and set JWT_SECRET before starting." >&2
  exit 1
fi

if ! grep -qE '^JWT_SECRET=.+' .env; then
  echo "JWT_SECRET is empty in .env." >&2
  echo "Generate one with: openssl rand -base64 48" >&2
  exit 1
fi

profile="${1:-}"

echo "Starting LIFEOS${profile:+ (profile: $profile)}..."
if [ -n "$profile" ]; then
  docker compose --profile "$profile" up -d --build
else
  docker compose up -d --build
fi

echo
echo "Waiting for the backend to report ready..."
for attempt in $(seq 1 60); do
  if curl -fsS http://localhost:"${BACKEND_PORT:-8080}"/actuator/health/readiness >/dev/null 2>&1; then
    echo "Backend is ready."
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "Backend did not become ready in time. Recent logs:" >&2
    docker compose logs --tail 50 backend >&2
    exit 1
  fi
  sleep 2
done

echo
echo "Web UI:      http://localhost:${FRONTEND_PORT:-8081}"
echo "API:         http://localhost:${BACKEND_PORT:-8080}/api"
echo "API docs:    http://localhost:${BACKEND_PORT:-8080}/api/docs"
echo "Health:      http://localhost:${BACKEND_PORT:-8080}/actuator/health"
