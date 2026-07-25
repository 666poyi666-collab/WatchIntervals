param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^tunnel_[A-Za-z0-9_-]+$')]
    [string]$TunnelId
)

$installer = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'install_persistent_chatgpt_tunnel.ps1'
& $installer -TunnelId $TunnelId
