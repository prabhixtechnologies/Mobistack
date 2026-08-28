#!/usr/bin/env bash
# Pull Hub images and start the HTTPS stack on EC2.
# Run from the cloned repo after `.env` exists at the repo root.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
  echo "Missing .env at $(pwd). Copy deploy/.env.prod.example and fill secrets." >&2
  exit 1
fi

export IMAGE_TAG="${IMAGE_TAG:-latest}"

# A new image runs its Flyway migrations on boot, so this is the moment the
# database is most likely to change shape. Take a dump first while rolling back
# is still an option. SKIP_BACKUP=1 for the very first deploy, when there is no
# database yet.
if [[ "${SKIP_BACKUP:-0}" != "1" ]] && docker ps --format '{{.Names}}' | grep -qx mobistack-postgres; then
  echo "Backing up before migrations..."
  # Invoked through bash rather than executed directly: the file arrives from a
  # Windows checkout without an exec bit, and a permission error here would abort
  # the deploy under `set -e`.
  bash deploy/db-backup.sh
  echo
fi

echo "Pulling ${IMAGE_TAG} and starting Caddy + API + web..."
# The Hub repositories are private, and a pull without credentials fails with a
# bare "pull access denied" that reads like the tag is missing. Say what it
# actually means before handing the error back.
if ! docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml pull; then
  echo >&2
  echo "Pull failed. If the message mentions access or authorisation, this host is" >&2
  echo "not signed in to Docker Hub and the images are private. Fix with:" >&2
  echo "  docker login -u <hub-user>            # paste an access token, not a password" >&2
  echo "Nothing was restarted, so the site is still serving the previous build." >&2
  exit 1
fi
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml restart caddy
docker image prune -f >/dev/null

echo
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml ps
echo
echo "Site: https://mobistack.prabhixtechnologies.com"
echo "First boot: Caddy needs ports 80 and 443 open so Let's Encrypt can issue a cert."
