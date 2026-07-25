@echo off
start "" "https://platform.openai.com/settings/organization/tunnels"
start "" "https://platform.openai.com/settings/organization/api-keys"
start "" "https://chatgpt.com/#settings/Connectors"
echo Create one Tunnel and one Tunnel Runtime API Key in the opened pages.
echo The Runtime API Key will be requested in a hidden PowerShell prompt and is not saved as plaintext.
set /p TUNNEL_ID=Tunnel ID:
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install_persistent_chatgpt_tunnel.ps1" -TunnelId "%TUNNEL_ID%"
pause
