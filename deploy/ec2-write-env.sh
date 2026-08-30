#!/usr/bin/env bash
# Creates /opt/mobistack/.env once. Does not print secrets.
set -euo pipefail
cd /opt/mobistack
umask 077
if [[ -f .env ]]; then
  echo ".env already exists"
  exit 0
fi
JWT=$(openssl rand -base64 64 | tr -d '\n')
PG=$(openssl rand -base64 24 | tr -d '\n')
cat > .env <<EOF
REGISTRY=029096972251.dkr.ecr.ap-south-1.amazonaws.com
IMAGE_TAG=latest
POSTGRES_DB=mobistack
POSTGRES_USER=mobistack
POSTGRES_PASSWORD=${PG}
FIXFLOW_SECURITY_JWT_SECRET=${JWT}
ACME_EMAIL=ops@prabhixtechnologies.com
FIXFLOW_WEB_ORIGIN=https://mobistack.prabhixtechnologies.com
FIXFLOW_PUBLIC_ORIGIN=https://mobistack.prabhixtechnologies.com
FIXFLOW_API_ORIGIN=https://mobistack.prabhixtechnologies.com
JAVA_TOOL_OPTIONS=-Xms256m -Xmx512m
SMTP_PORT=587
SMTP_FROM=noreply@prabhixtechnologies.com
SMTP_AUTH=true
SMTP_STARTTLS=true
EOF
chmod 600 .env
echo "Wrote /opt/mobistack/.env"
