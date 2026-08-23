$mobile = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $mobile -Parent
$web = Join-Path $repo "web"
Push-Location $web
try {
  if (-not (Test-Path (Join-Path $web "node_modules\sharp"))) {
    npm install --no-save sharp
  }
  node (Join-Path $repo "brand\render.mjs")
} finally {
  Pop-Location
}
