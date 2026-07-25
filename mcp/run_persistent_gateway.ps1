$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$gateway = Join-Path $root 'personal_gateway.py'
$mutex = [Threading.Mutex]::new($false, 'Local\WatchIntervalsPersonalGateway')
if (-not $mutex.WaitOne(0)) { exit 0 }
try {
    while ($true) {
        & python $gateway
        Start-Sleep -Seconds 5
    }
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
