#!/usr/bin/env bash
# Build, tag, and push MobiStack images to Amazon ECR.
#
# Pushing to Docker Hub is gone. ECR is the only registry, and this script is the manual
# equivalent of the push job in .github/workflows/build.yml — reach for it when CI is unavailable,
# not as the usual route, because a laptop build is not reproducible the way the workflow is.
#
# Unlike CI, which assumes a role through GitHub's OIDC provider, this uses whatever AWS
# credentials are already on the machine. They need ecr:GetAuthorizationToken plus push rights on
# prabhix/mobistack-*; the PrabhixPlatformDeployer policy grants both.
set -euo pipefail
cd "$(dirname "$0")/.."

AWS_REGION="${AWS_REGION:-ap-south-1}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-029096972251}"
REGISTRY="${REGISTRY:-${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com}"
TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
SKIP_TESTS="${SKIP_TESTS:-0}"

BACKEND="${REGISTRY}/prabhix/mobistack-backend"
WEB="${REGISTRY}/prabhix/mobistack-web"

echo "Registry : ${REGISTRY}"
echo "Tag      : ${TAG}  (also latest)"

if [[ "${SKIP_TESTS}" != "1" ]]; then
  echo "Running backend tests..."
  (cd backend && mvn -q -B test)
  echo "Typechecking web..."
  (cd web && { [[ -d node_modules ]] || npm ci; npx tsc --noEmit; })
fi

echo "Logging in to ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${REGISTRY}"

echo "Building images..."
docker build -t "${BACKEND}:${TAG}" -t "${BACKEND}:latest" ./backend
docker build -t "${WEB}:${TAG}" -t "${WEB}:latest" ./web

echo "Pushing to ECR..."
docker push "${BACKEND}:${TAG}"
docker push "${BACKEND}:latest"
docker push "${WEB}:${TAG}"
docker push "${WEB}:latest"

echo
echo "Published ${BACKEND}:${TAG} and ${WEB}:${TAG} (and latest)."
echo "On EC2: IMAGE_TAG=${TAG} ./deploy/ec2-up.sh"
