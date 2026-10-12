# Pipecat Mobile Bot - PowerShell launcher (Windows)
# Usage: .\mobile\start_voice_ai.ps1

$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot
Set-Location ..   # project root

# 1) .env 로드
$envFile = "mobile\.env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile"
    exit 1
}
Get-Content $envFile | Where-Object {
    $_ -notmatch '^\s*#' -and $_ -match '^\s*[A-Z_][A-Z0-9_]*\s*='
} | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}

# 2) Windows 콘솔 이모지/한글 출력
$env:PYTHONIOENCODING = "utf-8"

# 3) 최신 voice-ui-kit 빌드 산출물이 있으면 그 쪽을 사용
$frontendDist = "C:\project\voice-ui-kit\small-webrtc-prebuilt\client\dist"
if (Test-Path $frontendDist) {
    $env:FRONTEND_DIST_PATH = $frontendDist
    Write-Host "✓ Frontend dist: $frontendDist" -ForegroundColor Green
} else {
    Write-Host "⚠ Frontend dist 없음 — pip 기본 prebuilt로 fallback" -ForegroundColor Yellow
}

# 4) SSL 인증서
$useHttps = (Test-Path "mobile\key.pem") -and (Test-Path "mobile\cert.pem")

Write-Host ""
Write-Host "🚀 Pipecat Mobile Bot 시작..." -ForegroundColor Cyan
Write-Host "📡 https://0.0.0.0:7860 (Ctrl+C로 종료)" -ForegroundColor Cyan
Write-Host ""

# 5) 실행
$args = @(
    "mobile\voice_ai.py",
    "--host", "0.0.0.0",
    "--port", "7860"
)
if ($useHttps) {
    $args += "--ssl-keyfile"; $args += "mobile\key.pem"
    $args += "--ssl-certfile"; $args += "mobile\cert.pem"
}

& ".venv\Scripts\python.exe" @args
