#!/usr/bin/env python3
"""Start 步序 HTTP MCP plus a Cloudflare Quick Tunnel and print one URL."""
import os, re, secrets, signal, subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PYTHON = ROOT / ".venv" / "Scripts" / "python.exe"
CLOUDFLARED = Path(r"C:\Program Files (x86)\cloudflared\cloudflared.exe")
URL_FILE = ROOT / "ChatGPT服务器URL.txt"
LOG_FILE = ROOT / "remote-mcp.log"

token = secrets.token_urlsafe(24)
env = os.environ.copy()
env["BUXU_MCP_PATH"] = token
env["BUXU_MCP_PORT"] = "8878"

server_log = open(LOG_FILE, "w", encoding="utf-8")
server = subprocess.Popen([str(PYTHON), str(ROOT / "buxu_remote_mcp.py")], env=env, stdout=server_log, stderr=subprocess.STDOUT)
time.sleep(1.5)
if server.poll() is not None:
    raise SystemExit(f"HTTP MCP 启动失败，请查看 {LOG_FILE}")

tunnel = subprocess.Popen(
    [str(CLOUDFLARED), "tunnel", "--url", "http://127.0.0.1:8878", "--no-autoupdate"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
)

public = None
deadline = time.time() + 30
while time.time() < deadline:
    line = tunnel.stdout.readline()
    if not line and tunnel.poll() is not None: break
    match = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", line or "")
    if match:
        public = match.group(0)
        break
if not public:
    server.terminate()
    raise SystemExit("公网地址生成失败，请检查网络。")

url = f"{public}/{token}/mcp"
URL_FILE.write_text(url, encoding="utf-8")
try:
    subprocess.run(["clip"], input=url, text=True, check=False)
except Exception:
    pass

print("\n步序运动 MCP 已启动")
print("服务器 URL（已复制到剪贴板）：")
print(url)
print("\nChatGPT 中选择：服务器 URL → 身份验证“无” → 勾选风险确认 → 创建")
print("保持此窗口运行。按 Ctrl+C 停止。")

try:
    while server.poll() is None and tunnel.poll() is None:
        time.sleep(1)
except KeyboardInterrupt:
    pass
finally:
    for process in (tunnel, server):
        if process.poll() is None:
            process.terminate()
    server_log.close()

