$ErrorActionPreference = 'Stop'
$mcp = Invoke-RestMethod 'http://127.0.0.1:8768/readyz' -TimeoutSec 3
$tunnel = Invoke-RestMethod 'http://127.0.0.1:8880/readyz' -TimeoutSec 3
$services = Get-Service PoyiWatchMcp, PoyiWatchTunnel
if ($services.Where({$_.Status -ne 'Running'}).Count -gt 0) { throw 'Watch services are not running.' }
[pscustomobject]@{ Mcp = $mcp.state; Tunnel = $tunnel.status; Services = $services.Name }
