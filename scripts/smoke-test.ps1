param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-Endpoint($Path, $ExpectedStatus = 200) {
    $uri = "$BaseUrl$Path"
    $resp = Invoke-WebRequest -Uri $uri -Method GET -SkipHttpErrorCheck
    if ($resp.StatusCode -ne $ExpectedStatus) {
        throw "Smoke test failed for $uri : expected $ExpectedStatus, got $($resp.StatusCode)"
    }
    if (-not $resp.Headers["X-Request-Id"]) {
        throw "Smoke test failed for $uri : missing X-Request-Id correlation header"
    }
    Write-Host "[PASS] GET $Path -> $($resp.StatusCode)"
}

Assert-Endpoint "/api/health/liveness" 200
Assert-Endpoint "/api/health/readiness" 200
Assert-Endpoint "/api/health" 200
Assert-Endpoint "/api/public/properties" 200
Assert-Endpoint "/api/amenities" 200
Assert-Endpoint "/api/admin/ops/status" 401

Write-Host "All Stage 8 smoke checks passed against $BaseUrl"
