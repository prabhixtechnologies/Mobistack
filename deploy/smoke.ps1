# End-to-end smoke against a running local stack (http://localhost:4173).
$ErrorActionPreference = "Stop"
$base = if ($env:SMOKE_BASE) { $env:SMOKE_BASE } else { "http://localhost:4173" }
$email = "owner@prabhixtechnologies.com"
$password = "Owner@123"

function Invoke-Json($method, $path, $body, $token) {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers.Authorization = "Bearer $token" }
    $params = @{
        Method = $method
        Uri = "$base$path"
        Headers = $headers
        UseBasicParsing = $true
    }
    if ($body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    return Invoke-RestMethod @params
}

Write-Host "Health..."
$health = Invoke-RestMethod -UseBasicParsing "http://localhost:8080/actuator/health"
if ($health.status -ne "UP") { throw "API not UP: $($health.status)" }

Write-Host "Login..."
$auth = Invoke-Json POST "/api/v1/auth/login" @{ email = $email; password = $password } $null
if (-not $auth.accessToken) { throw "Login did not return a token" }
$token = $auth.accessToken

$checks = @(
    @{ path = "/api/v1/dashboard"; name = "dashboard" },
    @{ path = "/api/v1/sales?size=5"; name = "sales" },
    @{ path = "/api/v1/variants?size=5"; name = "inventory" },
    @{ path = "/api/v1/customers?size=5"; name = "customers" },
    @{ path = "/api/v1/repairs?size=5"; name = "repairs" },
    @{ path = "/api/v1/search?q=Realme"; name = "search" },
    @{ path = "/api/v1/reports?range=today"; name = "reports" },
    @{ path = "/api/v1/notifications"; name = "notifications" },
    @{ path = "/api/v1/workspaces"; name = "workspaces" }
)

foreach ($check in $checks) {
    Write-Host "GET $($check.name)..."
    $null = Invoke-Json GET $check.path $null $token
}

Write-Host "All smoke checks passed against $base"
