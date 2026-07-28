$ErrorActionPreference = 'Stop'
$services = Get-Service PoyiWatchMcp, PoyiWatchTunnel -ErrorAction SilentlyContinue |
    Select-Object Name, Status, StartType
$health = try { Invoke-RestMethod 'http://127.0.0.1:8768/healthz' -TimeoutSec 3 } catch { $null }
$ready = try { Invoke-RestMethod 'http://127.0.0.1:8768/readyz' -TimeoutSec 3 } catch { $null }
[pscustomobject]@{ Services = $services; Health = $health; Ready = $ready }
