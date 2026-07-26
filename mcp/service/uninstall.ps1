[CmdletBinding()]
param([string]$InstallDir = "$env:ProgramFiles\Poyi\WatchMcp")
$ErrorActionPreference = 'Stop'
foreach ($name in @('PoyiWatchTunnel', 'PoyiWatchMcp')) {
    $service = Get-Service $name -ErrorAction SilentlyContinue
    if ($null -ne $service) {
        if ($service.Status -ne 'Stopped') { Stop-Service $name -Force }
        & (Join-Path $InstallDir "$name.exe") uninstall
        if ($LASTEXITCODE -ne 0) { throw "Failed to uninstall $name" }
    }
}
