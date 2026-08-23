#!/usr/bin/env bash
# Start the prod stack from images already loaded on the instance.
set -euo pipefail
cd /opt/mobistack
if [[ ! -f .env ]]; then
  echo "Missing /opt/mobistack/.env" >&2
  exit 1
fi
sudo docker compose --profile prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.ec2-loaded.yml \
  up -d --remove-orphans
sudo docker compose --profile prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.ec2-loaded.yml \
  ps
