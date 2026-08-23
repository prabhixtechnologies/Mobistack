#!/usr/bin/env bash
set -euo pipefail
ENV_FILE=/opt/mobistack/.env
KEYS=/tmp/ms-rzp.env
if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE" >&2
  exit 1
fi
if [[ -f "$KEYS" ]]; then
  sed -i 's/\r$//' "$KEYS"
  if ! grep -q '^RAZORPAY_KEY_ID=' "$ENV_FILE"; then
    cat "$KEYS" >> "$ENV_FILE"
  fi
  rm -f "$KEYS"
fi
chmod 600 "$ENV_FILE"
echo "env_ok keys=$(grep -c '^RAZORPAY_KEY_' "$ENV_FILE") jwtlen=$(awk -F= '/^FIXFLOW_SECURITY_JWT_SECRET=/{print length($2)}' "$ENV_FILE") lines=$(wc -l < "$ENV_FILE")"
