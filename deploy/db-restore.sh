#!/usr/bin/env bash
# Restore a dump written by db-backup.sh, replacing the live database.
#
# This is destructive and there is no undo, so it asks for the database name to
# be typed out. Stop the API first: Flyway and the running app will both fight a
# restore in progress.
#
# Usage:
#   ./deploy/db-restore.sh backups/mobistack-fixflow-20260827T021500Z.sql.gz
set -euo pipefail
cd "$(dirname "$0")/.."

dump="${1:-}"
if [[ -z "${dump}" || ! -f "${dump}" ]]; then
  echo "Usage: ./deploy/db-restore.sh <dump.sql.gz>" >&2
  echo "Available:" >&2
  find "${BACKUP_DIR:-./backups}" -maxdepth 1 -name '*.sql.gz' 2>/dev/null | sort >&2 || true
  exit 1
fi

CONTAINER="${POSTGRES_CONTAINER:-mobistack-postgres}"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi
DB="${POSTGRES_DB:-fixflow}"
USER="${POSTGRES_USER:-fixflow}"

gzip -t "${dump}"

echo "About to overwrite database '${DB}' in container '${CONTAINER}' with:"
echo "  ${dump}"
echo "Everything currently in '${DB}' will be lost."
read -r -p "Type the database name to confirm: " typed
if [[ "${typed}" != "${DB}" ]]; then
  echo "Names did not match. Nothing was changed." >&2
  exit 1
fi

echo "Stopping the API so nothing writes during the restore..."
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop backend || true

echo "Restoring..."
# ON_ERROR_STOP makes psql exit on the first failed statement, so a restore that
# goes wrong halfway does not report success.
gunzip -c "${dump}" | docker exec -i "${CONTAINER}" psql \
  --username="${USER}" \
  --dbname="${DB}" \
  --set ON_ERROR_STOP=on \
  --quiet

echo "Starting the API..."
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml up -d backend

echo "Restored ${DB} from ${dump}."
echo "Check the site, then confirm the Flyway history matches this image:"
echo "  docker exec ${CONTAINER} psql -U ${USER} -d ${DB} -c 'select version, description, success from flyway_schema_history order by installed_rank desc limit 5;'"
