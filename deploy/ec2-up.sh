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

echo "Pulling ${IMAGE_TAG} and starting Caddy + API + web..."
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml restart caddy
docker image prune -f >/dev/null

echo
docker compose --profile prod -f docker-compose.yml -f docker-compose.prod.yml ps
echo
echo "Site: https://mobistack.prabhixtechnologies.com"
echo "First boot: Caddy needs ports 80 and 443 open so Let's Encrypt can issue a cert."
