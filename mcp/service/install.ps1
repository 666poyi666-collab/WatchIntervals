[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [Security.SecureString]$PhoneApiToken,
    [string]$PhoneDeviceId = '',
    [string]$InstallDir = "$env:ProgramFiles\Poyi\WatchMcp",
    [string]$DataDir = "$env:ProgramData\Poyi\WatchMcp",
    [string]$TunnelId = '',
    [Security.SecureString]$RuntimeApiKey
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Security

function Assert-Administrator {
    $principal = [Security.Principal.WindowsPrincipal]::new(
        [Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run install.ps1 from an elevated PowerShell session.'
    }
}
function Protect-Secret([Security.SecureString]$Secret, [string]$Path, [string]$EntropyText) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try {
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        $bytes = [Text.Encoding]::UTF8.GetBytes($plain)
        $entropy = [Text.Encoding]::UTF8.GetBytes($EntropyText)
        $encrypted = [Security.Cryptography.ProtectedData]::Protect(
            $bytes, $entropy, [Security.Cryptography.DataProtectionScope]::LocalMachine)
        [IO.File]::WriteAllText($Path, [Convert]::ToBase64String($encrypted), [Text.Encoding]::ASCII)
        [Array]::Clear($bytes, 0, $bytes.Length)
    } finally {
        if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
}
function Get-Verified([string]$Url, [string]$Sha256, [string]$Path) {
    Invoke-WebRequest -UseBasicParsing $Url -OutFile $Path
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    if ($actual -ne $Sha256.ToLowerInvariant()) { throw "SHA-256 mismatch for $Url" }
}
function Set-ServiceXml([string]$Path, [string]$ResolvedDataDir) {
    [xml]$xml = Get-Content -Raw -LiteralPath $Path
    $node = $xml.SelectSingleNode('/service/env[@name="WATCH_MCP_DATA_DIR"]')
    if ($null -eq $node) { throw "Missing WATCH_MCP_DATA_DIR in $Path" }
    $node.SetAttribute('value', $ResolvedDataDir)
    $log = $xml.SelectSingleNode('/service/logpath')
    $log.InnerText = $log.InnerText.Replace('%WATCH_MCP_DATA_DIR%', $ResolvedDataDir)
    $xml.Save($Path)
}
function Wait-Ready {
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try { $ready = Invoke-RestMethod 'http://127.0.0.1:8768/readyz' -TimeoutSec 2 }
        catch { $ready = $null }
        if ($null -ne $ready) { return }
        Start-Sleep -Milliseconds 500
    } until ((Get-Date) -ge $deadline)
    throw 'PoyiWatchMcp did not become ready.'
}

Assert-Administrator
$source = Split-Path -Parent $PSScriptRoot
if ([IO.Path]::GetFullPath($InstallDir) -notlike "$env:ProgramFiles\Poyi\WatchMcp*") {
    throw 'InstallDir must be the dedicated WatchMcp directory under Program Files.'
}
if ([IO.Path]::GetFullPath($DataDir) -notlike "$env:ProgramData\Poyi\WatchMcp*") {
    throw 'DataDir must be the dedicated WatchMcp directory under ProgramData.'
}
New-Item -ItemType Directory -Path $InstallDir, $DataDir -Force | Out-Null
Get-ChildItem -LiteralPath $source -Force | Where-Object {
    $_.Name -notin @('.venv', '.pytest_cache', '.ruff_cache', 'build', 'dist', '__pycache__')
} | Copy-Item -Destination $InstallDir -Recurse -Force

$temporary = Join-Path $env:TEMP ('watch-mcp-install-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $temporary | Out-Null
try {
    $dependencies = Get-Content -Raw (Join-Path $InstallDir 'service\dependencies.json') |
        ConvertFrom-Json
    $winsw = Join-Path $temporary 'winsw.exe'
    Get-Verified $dependencies.winsw.url $dependencies.winsw.sha256 $winsw
    Copy-Item $winsw (Join-Path $InstallDir 'PoyiWatchMcp.exe') -Force
    Copy-Item $winsw (Join-Path $InstallDir 'PoyiWatchTunnel.exe') -Force
    Copy-Item (Join-Path $InstallDir 'service\service.xml') `
        (Join-Path $InstallDir 'PoyiWatchMcp.xml') -Force
    Copy-Item (Join-Path $InstallDir 'service\tunnel-service.xml') `
        (Join-Path $InstallDir 'PoyiWatchTunnel.xml') -Force
    Set-ServiceXml (Join-Path $InstallDir 'PoyiWatchMcp.xml') ([IO.Path]::GetFullPath($DataDir))
    Set-ServiceXml (Join-Path $InstallDir 'PoyiWatchTunnel.xml') ([IO.Path]::GetFullPath($DataDir))

    $tunnelZip = Join-Path $temporary 'tunnel.zip'
    Get-Verified $dependencies.tunnelClient.url $dependencies.tunnelClient.sha256 $tunnelZip
    Expand-Archive $tunnelZip (Join-Path $InstallDir 'tunnel-client') -Force

    $previousPythonDir = $env:UV_PYTHON_INSTALL_DIR
    $env:UV_PYTHON_INSTALL_DIR = Join-Path $InstallDir 'python'
    try {
        & uv python install 3.12 --no-bin --no-registry
        if ($LASTEXITCODE -ne 0) { throw 'Private Python installation failed.' }
    } finally { $env:UV_PYTHON_INSTALL_DIR = $previousPythonDir }
    $python = Get-ChildItem (Join-Path $InstallDir 'python') -Filter python.exe -Recurse |
        Select-Object -First 1 -ExpandProperty FullName
    & uv build --project $InstallDir --wheel --out-dir $temporary
    if ($LASTEXITCODE -ne 0) { throw 'Watch MCP wheel build failed.' }
    $wheel = Get-ChildItem $temporary -Filter '*.whl' | Select-Object -First 1
    & uv pip install --python $python --break-system-packages $wheel.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Watch MCP private runtime installation failed.' }
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force -ErrorAction SilentlyContinue
}

Protect-Secret $PhoneApiToken (Join-Path $DataDir 'phone-token.dpapi') `
    'Poyi.WatchMcp.PhoneToken.v1'
if (-not [string]::IsNullOrWhiteSpace($PhoneDeviceId)) {
    Set-Content (Join-Path $DataDir 'phone-device-id') $PhoneDeviceId -Encoding UTF8 -NoNewline
}
if (-not [string]::IsNullOrWhiteSpace($TunnelId)) {
    if ($TunnelId -notmatch '^tunnel_[A-Za-z0-9_-]+$') { throw 'Invalid TunnelId.' }
    if ($null -eq $RuntimeApiKey) { throw 'RuntimeApiKey is required when TunnelId is set.' }
    Protect-Secret $RuntimeApiKey (Join-Path $DataDir 'runtime-key.dpapi') `
        'Poyi.WatchMcp.TunnelKey.v1'
    Set-Content (Join-Path $DataDir 'tunnel-id') $TunnelId -Encoding ASCII -NoNewline
}

Push-Location $InstallDir
try {
    foreach ($name in @('PoyiWatchTunnel', 'PoyiWatchMcp')) {
        $existing = Get-Service $name -ErrorAction SilentlyContinue
        if ($null -ne $existing) {
            if ($existing.Status -ne 'Stopped') { Stop-Service $name -Force }
            & (Join-Path $InstallDir "$name.exe") uninstall
        }
    }
    & (Join-Path $InstallDir 'PoyiWatchMcp.exe') install
    if ($LASTEXITCODE -ne 0) { throw 'PoyiWatchMcp service installation failed.' }
    & (Join-Path $InstallDir 'PoyiWatchTunnel.exe') install
    if ($LASTEXITCODE -ne 0) { throw 'PoyiWatchTunnel service installation failed.' }
    & sc.exe config PoyiWatchMcp obj= LocalSystem | Out-Null
    & sc.exe config PoyiWatchTunnel obj= LocalSystem | Out-Null
} finally { Pop-Location }

$mcpSid = 'NT SERVICE\PoyiWatchMcp'
$tunnelSid = 'NT SERVICE\PoyiWatchTunnel'
New-Item -ItemType Directory -Path (Join-Path $DataDir 'service-logs\mcp'), `
    (Join-Path $DataDir 'service-logs\tunnel'), (Join-Path $DataDir 'tunnel-logs') -Force | Out-Null
& icacls $InstallDir /grant:r "$mcpSid`:(OI)(CI)RX" "$tunnelSid`:(OI)(CI)RX" /T /C | Out-Null
& icacls $DataDir /inheritance:r /grant:r 'BUILTIN\Administrators:(OI)(CI)F' `
    'NT AUTHORITY\SYSTEM:(OI)(CI)F' "$mcpSid`:(OI)(CI)M" "$tunnelSid`:(OI)(CI)M" /T /C | Out-Null
foreach ($secretFile in @('phone-token.dpapi', 'phone-device-id', 'runtime-key.dpapi', 'tunnel-id')) {
    $secretPath = Join-Path $DataDir $secretFile
    if (Test-Path -LiteralPath $secretPath) {
        & icacls $secretPath /grant:r 'NT AUTHORITY\SYSTEM:R' /C | Out-Null
    }
}

Start-Service PoyiWatchMcp
Wait-Ready
if (Test-Path (Join-Path $DataDir 'runtime-key.dpapi')) { Start-Service PoyiWatchTunnel }
Write-Host 'PoyiWatchMcp installed independently.' -ForegroundColor Green
