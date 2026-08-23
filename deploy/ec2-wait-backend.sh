#!/usr/bin/env bash
set -euo pipefail
cd /opt/mobistack
sudo docker exec fixflow-caddy caddy reload --config /etc/caddy/Caddyfile
echo "caddy_reload=ok"
for i in $(seq 1 18); do
  st=$(sudo docker inspect --format '{{.State.Health.Status}}' fixflow-backend)
  echo "backend=$st"
  if [[ "$st" == "healthy" || "$st" == "unhealthy" ]]; then
    break
  fi
  sleep 10
done
echo "container_key_after=$(sudo docker exec fixflow-backend printenv RAZORPAY_KEY_ID | cut -c1-8)"
sudo docker compose --profile prod \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.ec2-loaded.yml \
  ps
