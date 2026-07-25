$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$client = Join-Path $root 'tunnel-client\tunnel-client.exe'
$scripts = @(
    'install_persistent_chatgpt_tunnel.ps1',
    'run_persistent_chatgpt_tunnel.ps1',
    'check_persistent_chatgpt_tunnel.ps1'
)

foreach ($name in $scripts) {
    $tokens = $null
    $errors = $null
    [Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $root $name), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -ne 0) { throw "$name has PowerShell parse errors" }
}

$sample = [Text.Encoding]::UTF8.GetBytes('test_runtime_key_not_real')
$entropy = [Text.Encoding]::UTF8.GetBytes('WatchIntervals.OpenAI.Tunnel.v1')
$encrypted = [Security.Cryptography.ProtectedData]::Protect(
    $sample, $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser)
$roundTrip = [Security.Cryptography.ProtectedData]::Unprotect(
    $encrypted, $entropy, [Security.Cryptography.DataProtectionScope]::CurrentUser)
if ([Text.Encoding]::UTF8.GetString($roundTrip) -ne 'test_runtime_key_not_real') {
    throw 'DPAPI round-trip failed'
}

$profileDir = Join-Path (Split-Path -Parent $root) 'build\tunnel-profile-test'
New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
$env:CONTROL_PLANE_API_KEY = 'test_runtime_key_not_real'
try {
    & $client init --sample sample_mcp_stdio_local --profile buxu-sports `
        --profile-dir $profileDir --tunnel-id tunnel_00000000000000000000000000000000 `
        --mcp-command 'python mcp/watch_intervals_mcp.py' `
        --health-listen-addr '127.0.0.1:8877' --force | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Profile generation failed' }
} finally {
    $env:CONTROL_PLANE_API_KEY = $null
}
$profile = Get-Content -Raw (Join-Path $profileDir 'buxu-sports.yaml')
if ($profile -notmatch 'env:CONTROL_PLANE_API_KEY') { throw 'Profile does not use an environment reference' }
if ($profile -match 'test_runtime_key_not_real') { throw 'Profile contains the plaintext test key' }

[Array]::Clear($sample, 0, $sample.Length)
[Array]::Clear($roundTrip, 0, $roundTrip.Length)
Write-Host 'Persistent tunnel tests: PASS'
