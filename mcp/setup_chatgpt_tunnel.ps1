param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^tunnel_[A-Za-z0-9_-]+$')]
    [string]$TunnelId,

    [Parameter(Mandatory = $false)]
    [string]$RuntimeApiKey
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$client = Join-Path $root 'tunnel-client\tunnel-client.exe'
$profiles = Join-Path $root 'tunnel-profiles'
$server = Join-Path $root 'watch_intervals_mcp.py'
$python = 'C:/Progra~1/Python312/python.exe'

if (-not (Test-Path -LiteralPath $client)) { throw "缺少 tunnel-client：$client" }
if (-not (Test-Path -LiteralPath $server)) { throw "缺少 MCP 服务：$server" }
if ([string]::IsNullOrWhiteSpace($RuntimeApiKey)) {
    $secure = Read-Host '粘贴 OpenAI Tunnel Runtime API Key' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $RuntimeApiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$env:CONTROL_PLANE_API_KEY = $RuntimeApiKey
$serverForCommand = $server.Replace('\', '/')
$mcpCommand = "$python $serverForCommand"

& $client init `
    --sample sample_mcp_stdio_local `
    --profile buxu-sports `
    --profile-dir $profiles `
    --tunnel-id $TunnelId `
    --mcp-command $mcpCommand `
    --health-listen-addr '127.0.0.1:8877' `
    --force
if ($LASTEXITCODE -ne 0) { throw "Tunnel 配置生成失败：$LASTEXITCODE" }

& $client doctor --profile buxu-sports --profile-dir $profiles --explain
if ($LASTEXITCODE -ne 0) { throw "Tunnel 检查未通过：$LASTEXITCODE" }

Write-Host ''
Write-Host '检查通过。正在启动步序运动 Tunnel…' -ForegroundColor Green
& $client run --profile buxu-sports --profile-dir $profiles --open-web-ui
