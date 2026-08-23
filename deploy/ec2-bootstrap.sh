#!/usr/bin/env bash
# First-time Amazon Linux 2023 setup. Safe to re-run.
set -euo pipefail

if [[ ! -f /swapfile ]]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user || true

# AL2023 repos do not ship docker-compose-plugin.
COMPOSE_PLUGIN=/usr/local/lib/docker/cli-plugins/docker-compose
if [[ ! -x "$COMPOSE_PLUGIN" ]]; then
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -fsSL "https://github.com/docker/compose/releases/download/v2.39.2/docker-compose-linux-$(uname -m)" \
    -o "$COMPOSE_PLUGIN"
  chmod +x "$COMPOSE_PLUGIN"
fi

mkdir -p /opt/mobistack
chown ec2-user:ec2-user /opt/mobistack

docker --version
docker compose version
free -h
echo "Bootstrap done."
