# Build, tag, and push MobiStack images to Docker Hub from this laptop.
# Usage (from repo root):
#   docker login
#   .\deploy\publish.ps1
#   .\deploy\publish.ps1 -SkipTests
#   .\deploy\publish.ps1 -Namespace mydockerhub -Tag abc1234

param(
    [string]$Namespace = $(if ($env:DOCKERHUB_NAMESPACE) { $env:DOCKERHUB_NAMESPACE } else { "prabhixtechnologies" }),
    [string]$Tag = "",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $Tag) {
    $Tag = (git rev-parse --short HEAD).Trim()
}

$backend = "$Namespace/mobistack-backend"
$web = "$Namespace/mobistack-web"

Write-Host "Namespace : $Namespace"
Write-Host "Tag       : $Tag  (also latest)"

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

Write-Host "Building images..."
docker build -t "${backend}:${Tag}" -t "${backend}:latest" ./backend
if ($LASTEXITCODE -ne 0) { throw "Backend image build failed" }
docker build -t "${web}:${Tag}" -t "${web}:latest" ./web
if ($LASTEXITCODE -ne 0) { throw "Web image build failed" }

Write-Host "Pushing to Docker Hub..."
docker push "${backend}:${Tag}"
docker push "${backend}:latest"
docker push "${web}:${Tag}"
docker push "${web}:latest"

Write-Host ""
Write-Host "Published:"
Write-Host "  ${backend}:${Tag}"
Write-Host "  ${backend}:latest"
Write-Host "  ${web}:${Tag}"
Write-Host "  ${web}:latest"
Write-Host ""
Write-Host "On EC2: IMAGE_TAG=$Tag ./deploy/ec2-up.sh"
Write-Host "Or leave IMAGE_TAG=latest to pull this build."
