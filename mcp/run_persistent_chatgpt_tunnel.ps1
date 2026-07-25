$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$client = Join-Path $root 'tunnel-client\tunnel-client.exe'
$stateDir = Join-Path $env:LOCALAPPDATA 'WatchIntervals\tunnel'
$profileDir = Join-Path $stateDir 'profiles'
$secretFile = Join-Path $stateDir 'runtime-key.dpapi'
$logFile = Join-Path $stateDir 'tunnel.log'
$pidFile = Join-Path $stateDir 'tunnel.pid'
$mutex = [Threading.Mutex]::new($false, 'Local\WatchIntervalsOpenAITunnel')

if (-not $mutex.WaitOne(0)) { exit 0 }
try {
    if (-not (Test-Path -LiteralPath $secretFile)) { throw '尚未安装长效通道凭据' }
    $entropy = [Text.Encoding]::UTF8.GetBytes('WatchIntervals.OpenAI.Tunnel.v1')
    $encrypted = [Convert]::FromBase64String([IO.File]::ReadAllText($secretFile).Trim())
    $plainBytes = [Security.Cryptography.ProtectedData]::Unprotect(
        $encrypted, $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try {
        $env:CONTROL_PLANE_API_KEY = [Text.Encoding]::UTF8.GetString($plainBytes)
        while ($true) {
            & $client run --profile buxu-sports --profile-dir $profileDir `
                --health.listen-addr '127.0.0.1:8877' --pid.file $pidFile `
                --log.file $logFile --log.level info --log.format json
            Start-Sleep -Seconds 5
        }
    } finally {
        [Array]::Clear($plainBytes, 0, $plainBytes.Length)
        $env:CONTROL_PLANE_API_KEY = $null
    }
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
