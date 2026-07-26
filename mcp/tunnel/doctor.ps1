$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security
$root = Split-Path -Parent $PSScriptRoot
$dataDir = $env:WATCH_MCP_DATA_DIR
if ([string]::IsNullOrWhiteSpace($dataDir)) { $dataDir = "$env:ProgramData\Poyi\WatchMcp" }
$client = Get-ChildItem (Join-Path "$env:ProgramFiles\Poyi\WatchMcp" 'tunnel-client') `
    -Filter tunnel-client.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
if (-not $client) { throw 'Watch tunnel-client is not installed.' }
$idPath = Join-Path $dataDir 'tunnel-id'
$keyPath = Join-Path $dataDir 'runtime-key.dpapi'
if (-not (Test-Path $idPath) -or -not (Test-Path $keyPath)) {
    throw 'Watch Tunnel is not provisioned.'
}
$tunnelId = (Get-Content -Raw $idPath).Trim()
$encrypted = [Convert]::FromBase64String((Get-Content -Raw $keyPath).Trim())
$entropy = [Text.Encoding]::UTF8.GetBytes('Poyi.WatchMcp.TunnelKey.v1')
$plain = [Security.Cryptography.ProtectedData]::Unprotect(
    $encrypted, $entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
try {
    $env:CONTROL_PLANE_API_KEY = [Text.Encoding]::UTF8.GetString($plain)
    & $client doctor --control-plane.tunnel-id $tunnelId `
        --mcp.server-url 'url=http://127.0.0.1:8768/mcp,channel=main' `
        --health.listen-addr '127.0.0.1:0' --explain
    exit $LASTEXITCODE
} finally {
    $env:CONTROL_PLANE_API_KEY = $null
    [Array]::Clear($plain, 0, $plain.Length)
    [Array]::Clear($encrypted, 0, $encrypted.Length)
}
