$ErrorActionPreference = 'Stop'
Start-Service PoyiWatchMcp
if (Test-Path "$env:ProgramData\Poyi\WatchMcp\runtime-key.dpapi") {
    Start-Service PoyiWatchTunnel
}
