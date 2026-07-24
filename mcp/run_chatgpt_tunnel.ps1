param(
    [Parameter(Mandatory = $false)]
    [string]$RuntimeApiKey
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$client = Join-Path $root 'tunnel-client\tunnel-client.exe'
$profiles = Join-Path $root 'tunnel-profiles'
if ([string]::IsNullOrWhiteSpace($RuntimeApiKey)) {
    $secure = Read-Host '粘贴 OpenAI Tunnel Runtime API Key' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $RuntimeApiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$env:CONTROL_PLANE_API_KEY = $RuntimeApiKey
& $client run --profile buxu-sports --profile-dir $profiles --open-web-ui

