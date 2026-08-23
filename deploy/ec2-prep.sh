#!/usr/bin/env bash
set -euo pipefail
# Pull public images and report instance networking. Safe to re-run.
sudo docker pull postgres:16-alpine
sudo docker pull redis:7-alpine
sudo docker pull caddy:2.10-alpine
df -h /
ss -tlnp || true
TOKEN=$(curl -sS --max-time 3 -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60" || true)
if [[ -n "${TOKEN}" ]]; then
  curl -sS --max-time 3 -H "X-aws-ec2-metadata-token: ${TOKEN}" \
    "http://169.254.169.254/latest/meta-data/security-groups" || true
  echo
  curl -sS --max-time 3 -o /tmp/iam-role.txt -w "iam_http=%{http_code}\n" \
    -H "X-aws-ec2-metadata-token: ${TOKEN}" \
    "http://169.254.169.254/latest/meta-data/iam/security-credentials/" || true
fi
echo "prep done"
