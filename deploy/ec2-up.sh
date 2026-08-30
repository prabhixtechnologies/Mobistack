#!/usr/bin/env bash
# Pull images from Amazon ECR and start the HTTPS stack on EC2.
# Run from the cloned repo after `.env` exists at the repo root.
set -euo pipefail
cd "$(dirname "$0")/.."

# Announce each phase and name the one that failed. Remote deploy logs get
# collapsed and truncated by the viewer, and a bare "exit 1" three screens down
# tells you nothing about which step produced it.
step="starting up"
trap 'echo; echo "DEPLOY FAILED during: ${step}" >&2' ERR
mark() {
  step="$1"
  echo ">>> ${step}"
}

mark "checking .env"
if [[ ! -f .env ]]; then
  echo "Missing .env at $(pwd). Copy deploy/.env.prod.example and fill secrets." >&2
  exit 1
fi

export IMAGE_TAG="${IMAGE_TAG:-latest}"
COMPOSE=(docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml)

# Resolve the compose files against `.env` before touching anything. A missing
# required variable fails here, where the message names it, instead of halfway
# through a restart.
mark "validating compose config against .env"
if ! "${COMPOSE[@]}" config >/dev/null; then
  echo >&2
  echo "Compose could not resolve its config. The error above names the setting;" >&2
  echo "add it to $(pwd)/.env. Nothing was restarted." >&2
  exit 1
fi

# A new image runs its Flyway migrations on boot, so this is the moment the
# database is most likely to change shape. Take a dump first while rolling back
# is still an option. SKIP_BACKUP=1 for the very first deploy, when there is no
# database yet.
#
# Asked of docker directly rather than piped into `grep -q`: grep exits on the
# first match, docker then takes SIGPIPE, and under `pipefail` the whole test
# reads as false -- which would skip the backup, silently, on exactly the deploy
# that is about to migrate the database.
postgres_running=$(docker ps --filter 'name=^mobistack-postgres$' --format '{{.Names}}')
if [[ "${SKIP_BACKUP:-0}" != "1" && -n "${postgres_running}" ]]; then
  mark "backing up the database before migrations"
  # Invoked through bash rather than executed directly: the file arrives from a
  # Windows checkout without an exec bit, and a permission error here would abort
  # the deploy under `set -e`.
  bash deploy/db-backup.sh
  echo
fi

# ECR tokens last twelve hours, so a box that deployed yesterday is signed out today. Logging in
# every time is cheaper than diagnosing the expiry, and needs no stored credential: the instance
# role supplies them.
mark "logging in to ECR"
AWS_REGION="${AWS_REGION:-ap-south-1}"
# `sed -n` rather than `grep`: grep exits 1 when it matches nothing, and under `set -e` a failing
# command substitution in an assignment aborts the script. A .env with no REGISTRY line is a
# perfectly ordinary case that should fall through to the default, not end the deploy.
REGISTRY="${REGISTRY:-$(sed -n 's/^REGISTRY=//p' .env | head -1 | tr -d "\"'")}"
REGISTRY="${REGISTRY:-029096972251.dkr.ecr.${AWS_REGION}.amazonaws.com}"
if ! aws ecr get-login-password --region "${AWS_REGION}" \
     | docker login --username AWS --password-stdin "${REGISTRY}"; then
  echo >&2
  echo "Could not log in to ${REGISTRY}." >&2
  echo "This instance authenticates with its IAM role, so there is no password to fix:" >&2
  echo "check that the role is still attached and still carries ecr:GetAuthorizationToken." >&2
  echo "Nothing was restarted, so the site is still serving the previous build." >&2
  exit 1
fi

mark "pulling images tagged ${IMAGE_TAG}"
if ! "${COMPOSE[@]}" pull; then
  echo >&2
  echo "Pull failed. If the message mentions access or authorisation, the instance role" >&2
  echo "has lost pull rights on prabhix/mobistack-* in ECR." >&2
  echo "If it mentions the tag or manifest, ${IMAGE_TAG} was never pushed." >&2
  echo "Nothing was restarted, so the site is still serving the previous build." >&2
  exit 1
fi

mark "starting containers"
"${COMPOSE[@]}" up -d --remove-orphans
mark "reloading Caddy"
"${COMPOSE[@]}" restart caddy
mark "pruning old images"
docker image prune -f >/dev/null

echo
"${COMPOSE[@]}" ps
echo
echo "Site: https://mobistack.prabhixtechnologies.com"
echo "First boot: Caddy needs ports 80 and 443 open so Let's Encrypt can issue a cert."
