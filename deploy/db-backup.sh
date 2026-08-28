#!/usr/bin/env bash
# Take a compressed dump of the live database.
#
# The stack keeps Postgres in a Docker volume, so a `docker volume rm` — or an
# EC2 instance that will not come back — takes the shop's whole ledger with it.
# This is the only copy of that data outside the volume.
#
# Usage:
#   ./deploy/db-backup.sh                  # write to ./backups
#   BACKUP_DIR=/mnt/backups ./deploy/db-backup.sh
#   KEEP=30 ./deploy/db-backup.sh          # keep 30 dumps instead of 14
#
# Nightly, via crontab -e on the server:
#   15 2 * * * cd /opt/mobistack && ./deploy/db-backup.sh >> /var/log/mobistack-backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP="${KEEP:-14}"
CONTAINER="${POSTGRES_CONTAINER:-mobistack-postgres}"

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "Postgres container '${CONTAINER}' is not running. Nothing was backed up." >&2
  exit 1
fi

# Ask the container what it was initialised with, rather than reading .env. That
# file follows Compose's rules, not the shell's: a legal value there such as
# "-Xms256m -Xmx512m" becomes a command when the file is sourced. The container
# is also the more truthful answer, since it is what Postgres actually started
# with even if .env has been edited since.
DB="$(docker exec "${CONTAINER}" printenv POSTGRES_DB 2>/dev/null || true)"
DB="${DB:-${POSTGRES_DB:-fixflow}}"
DB_USER="$(docker exec "${CONTAINER}" printenv POSTGRES_USER 2>/dev/null || true)"
DB_USER="${DB_USER:-${POSTGRES_USER:-fixflow}}"

mkdir -p "${BACKUP_DIR}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="${BACKUP_DIR}/mobistack-${DB}-${stamp}.sql.gz"

# Write to a .partial name first: a dump interrupted halfway through is worse
# than no dump, because it looks like a usable one in the directory listing.
partial="${target}.partial"
trap 'rm -f "${partial}"' ERR

echo "Dumping ${DB} from ${CONTAINER}..."
docker exec -i "${CONTAINER}" pg_dump \
  --username="${DB_USER}" \
  --dbname="${DB}" \
  --clean --if-exists --no-owner --no-privileges \
  | gzip -9 > "${partial}"

# gzip -t proves the archive is complete; a truncated pipe would otherwise be
# discovered only during a restore, which is the worst possible moment.
gzip -t "${partial}"

# pg_dump writes this line last, so finding it proves the dump ran to the end
# rather than dying midway with a valid gzip trailer. A byte-count threshold
# cannot tell the difference: gzip -9 takes a small but entirely real database
# down to a few hundred bytes, and the deploy would abort on a good backup.
#
# Matched with a case statement rather than piping into grep -q, because grep -q
# exits at the first match, tail then dies of SIGPIPE, and pipefail reports the
# whole pipeline as failed -- discarding a perfectly good dump now and then.
dump_tail="$(gunzip -c "${partial}" | tail -20)"
case "${dump_tail}" in
  *"PostgreSQL database dump complete"*) ;;
  *)
    echo "The dump has no completion marker from pg_dump, so it stopped early." >&2
    rm -f "${partial}"
    exit 1
    ;;
esac

mv "${partial}" "${target}"
trap - ERR
echo "Wrote ${target} ($(du -h "${target}" | cut -f1))"

# Rotation is oldest-first by filename, which sorts chronologically because the
# stamp is a fixed-width UTC timestamp.
mapfile -t dumps < <(find "${BACKUP_DIR}" -maxdepth 1 -name "mobistack-${DB}-*.sql.gz" | sort)
excess=$(( ${#dumps[@]} - KEEP ))
if (( excess > 0 )); then
  for ((i = 0; i < excess; i++)); do
    echo "Removing old dump ${dumps[i]}"
    rm -f "${dumps[i]}"
  done
fi

echo "${#dumps[@]} dump(s) on disk, keeping the newest ${KEEP}."
echo "Copy these off the instance. A backup on the same disk as the database is not a backup."
