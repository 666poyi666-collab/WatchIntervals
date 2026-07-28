$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security

$root = Split-Path -Parent $PSScriptRoot
$dataDir = $env:WATCH_MCP_DATA_DIR
if ([string]::IsNullOrWhiteSpace($dataDir)) { $dataDir = "$env:ProgramData\Poyi\WatchMcp" }
$secretPath = Join-Path $dataDir 'phone-token.dpapi'
if (-not (Test-Path -LiteralPath $secretPath)) { throw 'Phone API token is not installed.' }
$python = Get-ChildItem -LiteralPath (Join-Path $root 'python') -Filter python.exe -Recurse |
    Select-Object -First 1 -ExpandProperty FullName
if ([string]::IsNullOrWhiteSpace($python)) { throw 'Private Python runtime is missing.' }

$encrypted = [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $secretPath).Trim())
$entropy = [Text.Encoding]::UTF8.GetBytes('Poyi.WatchMcp.PhoneToken.v1')
$plain = [Security.Cryptography.ProtectedData]::Unprotect(
    $encrypted, $entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
try {
    $env:WATCH_MCP_PHONE_TOKEN = [Text.Encoding]::UTF8.GetString($plain)
    & $python -s -m watch_mcp.main serve
    exit $LASTEXITCODE
} finally {
    $env:WATCH_MCP_PHONE_TOKEN = $null
    [Array]::Clear($plain, 0, $plain.Length)
    [Array]::Clear($encrypted, 0, $encrypted.Length)
}
