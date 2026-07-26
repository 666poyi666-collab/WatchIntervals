$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dataDir = "$env:ProgramData\Poyi\WatchMcp"
$client = Get-ChildItem (Join-Path "$env:ProgramFiles\Poyi\WatchMcp" 'tunnel-client') `
    -Filter tunnel-client.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
if (-not $client) { throw 'Watch tunnel-client is not installed.' }
$tunnelId = (Get-Content -Raw (Join-Path $dataDir 'tunnel-id')).Trim()
& $client doctor --control-plane.tunnel-id $tunnelId `
    --mcp.server-url 'url=http://127.0.0.1:8768/mcp,channel=main' --explain
exit $LASTEXITCODE
