$ErrorActionPreference = 'Stop'
Stop-Service PoyiWatchTunnel -Force -ErrorAction SilentlyContinue
Stop-Service PoyiWatchMcp -Force
