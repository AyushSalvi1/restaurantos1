#!/usr/bin/env bash
# Dumps the MySQL database to a timestamped file and applies a retention window.
set -euo pipefail

cd "$(dirname "$0")/.."

backup_dir="${BACKUP_DIR:-./backups}"
keep="${BACKUP_KEEP:-14}"
mkdir -p "$backup_dir"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a && . ./.env && set +a
fi

host="${MYSQL_HOST:-127.0.0.1}"
port="${MYSQL_PORT:-3306}"
database="${MYSQL_DATABASE:-lifeos}"
user="${MYSQL_USER:-lifeos}"

if [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "MYSQL_PASSWORD is not set. Source .env or export it before running this script." >&2
  exit 1
fi

stamp="$(date +%Y%m%d-%H%M%S)"
target="$backup_dir/lifeos-$stamp.sql.gz"

echo "Backing up '$database' from $host:$port to $target"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  --host="$host" --port="$port" --user="$user" \
  --single-transaction --quick --routines --events \
  "$database" | gzip -9 > "$target"

echo "Wrote $(du -h "$target" | cut -f1) to $target"

# Retention: delete the oldest dumps beyond the keep count.
count=$(find "$backup_dir" -maxdepth 1 -name 'lifeos-*.sql.gz' | wc -l | tr -d ' ')
if [ "$count" -gt "$keep" ]; then
  echo "Pruning to the newest $keep backups"
  find "$backup_dir" -maxdepth 1 -name 'lifeos-*.sql.gz' -print0 \
    | xargs -0 ls -1t \
    | tail -n "+$((keep + 1))" \
    | while read -r old; do rm -f "$old"; echo "  removed $old"; done
fi
