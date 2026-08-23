#!/usr/bin/env bash
# Build, tag, and push MobiStack images to Docker Hub.
set -euo pipefail
cd "$(dirname "$0")/.."

NAMESPACE="${DOCKERHUB_NAMESPACE:-prabhixtechnologies}"
TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
SKIP_TESTS="${SKIP_TESTS:-0}"

BACKEND="${NAMESPACE}/mobistack-backend"
WEB="${NAMESPACE}/mobistack-web"

echo "Namespace : ${NAMESPACE}"
echo "Tag       : ${TAG}  (also latest)"

if [[ "${SKIP_TESTS}" != "1" ]]; then
  echo "Running backend tests..."
  (cd backend && mvn -q -B test)
  echo "Typechecking web..."
  (cd web && { [[ -d node_modules ]] || npm ci; npx tsc --noEmit; })
fi

echo "Building images..."
docker build -t "${BACKEND}:${TAG}" -t "${BACKEND}:latest" ./backend
docker build -t "${WEB}:${TAG}" -t "${WEB}:latest" ./web

echo "Pushing to Docker Hub..."
docker push "${BACKEND}:${TAG}"
docker push "${BACKEND}:latest"
docker push "${WEB}:${TAG}"
docker push "${WEB}:latest"

echo
echo "Published ${BACKEND}:${TAG} and ${WEB}:${TAG} (and latest)."
echo "On EC2: IMAGE_TAG=${TAG} ./deploy/ec2-up.sh"
