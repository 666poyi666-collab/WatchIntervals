param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^tunnel_[A-Za-z0-9_-]+$')]
    [string]$TunnelId,

    [Parameter(Mandatory = $false)]
    [Security.SecureString]$RuntimeApiKey
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$client = Join-Path $root 'tunnel-client\tunnel-client.exe'
$server = Join-Path $root 'watch_intervals_mcp.py'
$runner = Join-Path $root 'run_persistent_chatgpt_tunnel.ps1'
$stateDir = Join-Path $env:LOCALAPPDATA 'WatchIntervals\tunnel'
$profileDir = Join-Path $stateDir 'profiles'
$secretFile = Join-Path $stateDir 'runtime-key.dpapi'
$taskName = 'WatchIntervals ChatGPT Tunnel'
$python = 'C:/Progra~1/Python312/python.exe'

foreach ($path in @($client, $server, $runner)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "缺少长效通道组件：$path" }
}
New-Item -ItemType Directory -Path $profileDir -Force | Out-Null

if ($null -eq $RuntimeApiKey) {
    $RuntimeApiKey = Read-Host '粘贴 Tunnel Runtime API Key（输入不会回显）' -AsSecureString
}

$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($RuntimeApiKey)
try {
    $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainKey)) { throw 'Runtime API Key 为空' }
    $entropy = [Text.Encoding]::UTF8.GetBytes('WatchIntervals.OpenAI.Tunnel.v1')
    $encrypted = [Security.Cryptography.ProtectedData]::Protect(
        [Text.Encoding]::UTF8.GetBytes($plainKey), $entropy,
        [Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllText($secretFile, [Convert]::ToBase64String($encrypted), [Text.Encoding]::ASCII)

    $env:CONTROL_PLANE_API_KEY = $plainKey
    $serverForCommand = $server.Replace('\', '/')
    $mcpCommand = "$python $serverForCommand"
    & $client init --sample sample_mcp_stdio_local --profile buxu-sports `
        --profile-dir $profileDir --tunnel-id $TunnelId --mcp-command $mcpCommand `
        --health-listen-addr '127.0.0.1:8877' --force
    if ($LASTEXITCODE -ne 0) { throw "Tunnel 配置生成失败：$LASTEXITCODE" }

    & $client doctor --profile buxu-sports --profile-dir $profileDir --explain
    if ($LASTEXITCODE -ne 0) { throw "Tunnel 检查失败：$LASTEXITCODE" }
} finally {
    $env:CONTROL_PLANE_API_KEY = $null
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $plainKey = $null
}

$arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$runner`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings `
    -Principal $principal -Description '步序 MCP 固定 ChatGPT 通道；登录后启动并自动重连。' -Force | Out-Null
Start-ScheduledTask -TaskName $taskName

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    try { $ready = Invoke-RestMethod -Uri 'http://127.0.0.1:8877/readyz' -TimeoutSec 2 } catch { $ready = $null }
} until ($null -ne $ready -or (Get-Date) -ge $deadline)
if ($null -eq $ready) { throw '长效通道已安装，但 30 秒内未通过就绪检查；请运行检查脚本查看状态。' }

Write-Host '长效 ChatGPT 通道已安装并在线。' -ForegroundColor Green
Write-Host 'ChatGPT 插件中选择“通道”，绑定刚创建的 Tunnel；以后无需填写服务器 URL。'
