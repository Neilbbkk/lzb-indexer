# lzb-indexer Integration Test Runner
# Starts Anvil, runs the integration test, then cleans up

$ErrorActionPreference = "Stop"

$ANVIL_PORT = 8545
$PROJECT_DIR = "D:\lzkcomp\web3\lzb-indexer"

Write-Host "=== LZB Indexer Integration Test ===" -ForegroundColor Cyan

# 1. Kill any lingering processes
Write-Host "[1/4] Cleaning up previous processes..." -ForegroundColor Yellow
Get-Process -Name "anvil" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# 2. Start Anvil in background
Write-Host "[2/4] Starting Anvil on port $ANVIL_PORT..." -ForegroundColor Yellow
$anvilProc = Start-Process -FilePath "anvil" -ArgumentList "--port", $ANVIL_PORT -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\anvil-stdout.log" -RedirectStandardError "$env:TEMP\anvil-stderr.log"
Start-Sleep -Seconds 3

# Verify Anvil is running
try {
    $null = Invoke-WebRequest -Uri "http://localhost:$ANVIL_PORT" -Method POST -Body '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' -ContentType "application/json" -TimeoutSec 5
    Write-Host "  Anvil is ready" -ForegroundColor Green
} catch {
    Write-Host "  ERROR: Anvil failed to start" -ForegroundColor Red
    Get-Content "$env:TEMP\anvil-stdout.log" -Tail 20
    exit 1
}

# 3. Compile Solidity contract
Write-Host "[3/4] Compiling Solidity test contract..." -ForegroundColor Yellow
Push-Location "$PROJECT_DIR\src\test\solidity"
try {
    forge build --force 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "forge build failed"
    }
    Write-Host "  Solidity compiled" -ForegroundColor Green
} finally {
    Pop-Location
}

# 4. Run integration test
Write-Host "[4/4] Running integration test..." -ForegroundColor Yellow
Push-Location $PROJECT_DIR
try {
    mvn test -Dtest=BlockScannerIntegrationTest -DfailIfNoTests=false 2>&1
    $testExit = $LASTEXITCODE
} finally {
    Pop-Location
}

# 5. Cleanup
Write-Host "`n=== Cleaning up ===" -ForegroundColor Cyan
Stop-Process -Id $anvilProc.Id -Force -ErrorAction SilentlyContinue
Get-Process -Name "anvil" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if ($testExit -eq 0) {
    Write-Host "ALL TESTS PASSED" -ForegroundColor Green
} else {
    Write-Host "TESTS FAILED (exit code: $testExit)" -ForegroundColor Red
}
exit $testExit