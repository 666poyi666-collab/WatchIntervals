$ErrorActionPreference = 'Stop'
$taskName = 'WatchIntervals ChatGPT Tunnel'
$gatewayTaskName = 'WatchIntervals Personal MCP Gateway'
$stateDir = Join-Path $env:LOCALAPPDATA 'WatchIntervals\tunnel'
$profile = Join-Path $stateDir 'profiles\buxu-sports.yaml'
$secret = Join-Path $stateDir 'runtime-key.dpapi'
$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
$gatewayTask = Get-ScheduledTask -TaskName $gatewayTaskName -ErrorAction SilentlyContinue
try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8877/healthz' -TimeoutSec 3
    $ready = Invoke-RestMethod -Uri 'http://127.0.0.1:8877/readyz' -TimeoutSec 3
    $online = $true
} catch {
    $health = $null
    $ready = $null
    $online = $false
}
try { $gateway = Invoke-RestMethod -Uri 'http://127.0.0.1:8767/readyz' -TimeoutSec 3; $gatewayOnline = $true }
catch { $gateway = $null; $gatewayOnline = $false }

[pscustomobject]@{
    Installed = ($null -ne $task -and $null -ne $gatewayTask -and (Test-Path -LiteralPath $profile) -and (Test-Path -LiteralPath $secret))
    TaskState = if ($task) { $task.State } else { 'Missing' }
    GatewayTaskState = if ($gatewayTask) { $gatewayTask.State } else { 'Missing' }
    GatewayOnline = $gatewayOnline
    Online = $online
    Health = if ($health) { 'ok' } else { 'unavailable' }
    Ready = if ($ready) { 'ready' } else { 'unavailable' }
    SecretStorage = if (Test-Path -LiteralPath $secret) { 'Windows DPAPI CurrentUser' } else { 'missing' }
} | Format-List

if (-not $online -or -not $gatewayOnline) { exit 1 }
