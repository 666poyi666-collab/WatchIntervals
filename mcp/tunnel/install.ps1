[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [Security.SecureString]$PhoneApiToken,
    [Parameter(Mandatory = $true)] [string]$TunnelId,
    [Parameter(Mandatory = $true)] [Security.SecureString]$RuntimeApiKey,
    [string]$PhoneDeviceId = ''
)
& (Join-Path (Split-Path -Parent $PSScriptRoot) 'service\install.ps1') `
    -PhoneApiToken $PhoneApiToken -PhoneDeviceId $PhoneDeviceId `
    -TunnelId $TunnelId -RuntimeApiKey $RuntimeApiKey
