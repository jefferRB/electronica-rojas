<#
.SYNOPSIS
    Starts the Electronica Rojas backend against the local Docker Compose PostgreSQL.

.DESCRIPTION
    Docker Compose reads .env for the container, but a Java process started from
    PowerShell or VS Code does not see those values (ARCH-DEV-003). This script reads
    the repository .env, derives DB_URL, DB_USERNAME and DB_PASSWORD (plus the optional
    BOOTSTRAP_ADMIN_* values), and sets them
    only for this PowerShell process and its child (Maven/Java). Nothing is printed
    or persisted, and the variables disappear when the script ends.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -Maven "-B package"

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -EnableDebugger

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1 -DemoSeed
#>
[CmdletBinding()]
param(
    # Maven arguments, space separated. Defaults to running the application.
    # -B (batch mode): without it Maven expects an interactive console and fails with
    # NoSuchElementException when started without stdin (VS Code tasks, background jobs).
    [string] $Maven = '-B spring-boot:run',

    # Opens a JDWP debug port on 127.0.0.1:5005 for "Attach to backend" in VS Code.
    [switch] $EnableDebugger,

    # DEV-ONLY: loads the fictitious portfolio scenario once at startup (APP_DEMO_SEED=true for this
    # process only). Local database and development inbox only; see docs/runbook.md.
    [switch] $DemoSeed
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot '.env'
$backendDir = Join-Path $repoRoot 'backend'

if (-not (Test-Path $envFile)) {
    throw "Missing $envFile. Create it with: Copy-Item .env.example .env (then set a password)."
}

$settings = @{}
foreach ($line in Get-Content $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $separator = $trimmed.IndexOf('=')
    if ($separator -lt 1) { continue }
    $key = $trimmed.Substring(0, $separator).Trim()
    $value = $trimmed.Substring($separator + 1).Trim().Trim('"').Trim("'")
    $settings[$key] = $value
}

foreach ($required in 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD') {
    if (-not $settings[$required]) { throw "$required is not set in .env" }
}

$port = if ($settings['POSTGRES_HOST_PORT']) { $settings['POSTGRES_HOST_PORT'] } else { '5433' }

$env:DB_URL = "jdbc:postgresql://127.0.0.1:$port/$($settings['POSTGRES_DB'])"
$env:DB_USERNAME = $settings['POSTGRES_USER']
$env:DB_PASSWORD = $settings['POSTGRES_PASSWORD']

# Optional first administrator (only used when both values are present).
foreach ($name in 'BOOTSTRAP_ADMIN_EMAIL', 'BOOTSTRAP_ADMIN_PASSWORD', 'BOOTSTRAP_ADMIN_NAME') {
    if ($settings[$name]) { Set-Item -Path "Env:$name" -Value $settings[$name] }
}

# Optional notification settings. Without them mail stays in the development inbox;
# SMTP is used only with MAIL_MODE=smtp and a server configured in .env (never in Git).
foreach ($name in 'MAIL_MODE', 'MAIL_FROM', 'PUBLIC_BASE_URL', 'NOTIFICATIONS_WORKER_ENABLED',
        'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD') {
    if ($settings[$name]) { Set-Item -Path "Env:$name" -Value $settings[$name] }
}

if ($DemoSeed) {
    $env:APP_DEMO_SEED = 'true'
    Write-Host 'Demo data: APP_DEMO_SEED=true for this run'
}

Write-Host "Backend -> $env:DB_URL as $env:DB_USERNAME (password hidden)"

Push-Location $backendDir
try {
    $mavenArgs = @($Maven -split '\s+' | Where-Object { $_ })
    if ($EnableDebugger) {
        $mavenArgs += '-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005'
    }
    & .\mvnw.cmd @mavenArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
