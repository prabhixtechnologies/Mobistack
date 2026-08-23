#!/usr/bin/env bash
# Recreate backend so compose re-reads /opt/mobistack/.env. Never prints secrets.
set -euo pipefail
cd /opt/mobistack
if [[ ! -f .env ]]; then
  echo "missing /opt/mobistack/.env" >&2
  exit 1
fi
key_kind=$(awk -F= '/^RAZORPAY_KEY_ID=/{print substr($2,1,8)}' .env)
secret_set=$(awk -F= '/^RAZORPAY_KEY_SECRET=/{print (length($2)>8)?"yes":"no"}' .env)
echo "env_key_kind=$key_kind secret_set=$secret_set"
if [[ "$key_kind" != "rzp_live" && "$key_kind" != "rzp_test" ]]; then
  echo "RAZORPAY_KEY_ID is missing or invalid" >&2
  exit 1
fi
if [[ "$secret_set" != "yes" ]]; then
  echo "RAZORPAY_KEY_SECRET is empty" >&2
  exit 1
fi
echo "container_key_before=$(sudo docker exec fixflow-backend printenv RAZORPAY_KEY_ID | cut -c1-8 || true)"
sudo docker compose --profile prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.ec2-loaded.yml \
  up -d --force-recreate --no-deps backend
echo "recreated=backend"
