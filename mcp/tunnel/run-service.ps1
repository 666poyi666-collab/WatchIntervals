$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security
$root = Split-Path -Parent $PSScriptRoot
$dataDir = $env:WATCH_MCP_DATA_DIR
if ([string]::IsNullOrWhiteSpace($dataDir)) { $dataDir = "$env:ProgramData\Poyi\WatchMcp" }
$keyPath = Join-Path $dataDir 'runtime-key.dpapi'
$idPath = Join-Path $dataDir 'tunnel-id'
if (-not (Test-Path $keyPath) -or -not (Test-Path $idPath)) { throw 'Watch Tunnel is not provisioned.' }
$deadline = (Get-Date).AddMinutes(2)
do {
    try { $ready = Invoke-RestMethod 'http://127.0.0.1:8768/readyz' -TimeoutSec 2 }
    catch { $ready = $null }
    if ($null -eq $ready) { Start-Sleep -Seconds 1 }
} until ($null -ne $ready -or (Get-Date) -ge $deadline)
if ($null -eq $ready) { throw 'Watch MCP is not ready.' }

$encrypted = [Convert]::FromBase64String((Get-Content -Raw $keyPath).Trim())
$entropy = [Text.Encoding]::UTF8.GetBytes('Poyi.WatchMcp.TunnelKey.v1')
$plain = [Security.Cryptography.ProtectedData]::Unprotect(
    $encrypted, $entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
try {
    $env:CONTROL_PLANE_API_KEY = [Text.Encoding]::UTF8.GetString($plain)
    $tunnelId = (Get-Content -Raw $idPath).Trim()
    $client = Get-ChildItem (Join-Path $root 'tunnel-client') -Filter tunnel-client.exe -Recurse |
        Select-Object -First 1 -ExpandProperty FullName
    & $client run --control-plane.tunnel-id $tunnelId `
        --mcp.server-url 'url=http://127.0.0.1:8768/mcp,channel=main' `
        --health.listen-addr '127.0.0.1:8880' --log.format json
    exit $LASTEXITCODE
} finally {
    $env:CONTROL_PLANE_API_KEY = $null
    [Array]::Clear($plain, 0, $plain.Length)
    [Array]::Clear($encrypted, 0, $encrypted.Length)
}
