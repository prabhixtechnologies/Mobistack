# Build, tag, and push MobiStack images to Amazon ECR from this laptop.
#
# Pushing to Docker Hub is gone. ECR is the only registry, and this script is the manual
# equivalent of the push job in .github/workflows/build.yml — reach for it when CI is unavailable,
# not as the usual route, because a laptop build is not reproducible the way the workflow is.
#
# Unlike CI, which assumes a role through GitHub's OIDC provider, this uses whatever AWS
# credentials are already on the machine. They need ecr:GetAuthorizationToken plus push rights on
# prabhix/mobistack-*; the PrabhixPlatformDeployer policy grants both.
#
# Usage (from repo root):
#   .\deploy\publish.ps1
#   .\deploy\publish.ps1 -SkipTests
#   .\deploy\publish.ps1 -Tag abc1234

param(
    [string]$Region = $(if ($env:AWS_REGION) { $env:AWS_REGION } else { "ap-south-1" }),
    [string]$AccountId = $(if ($env:AWS_ACCOUNT_ID) { $env:AWS_ACCOUNT_ID } else { "029096972251" }),
    [string]$Tag = "",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $Tag) {
    $Tag = (git rev-parse --short HEAD).Trim()
}

$registry = "$AccountId.dkr.ecr.$Region.amazonaws.com"
$backend = "$registry/prabhix/mobistack-backend"
$web = "$registry/prabhix/mobistack-web"

Write-Host "Registry : $registry"
Write-Host "Tag      : $Tag  (also latest)"

if (-not $SkipTests) {
    Write-Host "Running backend tests..."
    Push-Location backend
    try {
        mvn -q -B test
        if ($LASTEXITCODE -ne 0) { throw "Backend tests failed" }
    } finally {
        Pop-Location
    }

    Write-Host "Typechecking web..."
    Push-Location web
    try {
        if (-not (Test-Path node_modules)) { npm ci }
        npx tsc --noEmit
        if ($LASTEXITCODE -ne 0) { throw "Web typecheck failed" }
    } finally {
        Pop-Location
    }
}

Write-Host "Logging in to ECR..."
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) { throw "ECR login failed" }

Write-Host "Building images..."
docker build -t "${backend}:${Tag}" -t "${backend}:latest" ./backend
if ($LASTEXITCODE -ne 0) { throw "Backend image build failed" }
docker build -t "${web}:${Tag}" -t "${web}:latest" ./web
if ($LASTEXITCODE -ne 0) { throw "Web image build failed" }

Write-Host "Pushing to ECR..."
docker push "${backend}:${Tag}"
docker push "${backend}:latest"
docker push "${web}:${Tag}"
docker push "${web}:latest"

Write-Host ""
Write-Host "Published:"
Write-Host "  ${backend}:${Tag}"
Write-Host "  ${web}:${Tag}"
Write-Host ""
Write-Host "On EC2: IMAGE_TAG=$Tag ./deploy/ec2-up.sh"
Write-Host "Or leave IMAGE_TAG=latest to pull this build."
