# 云同步（Cloudflare）· 步序 · 间歇跑

> 2026-07-26 建立。目标：电脑关机后，ChatGPT/Claude 仍能读到训练/睡眠/计划数据。

## 现状架构

```text
手表/手机（权威数据）
  ←局域网→ 本机 Watch MCP (127.0.0.1:8768，服务 PoyiWatchMcp)
      →出站→ 云端镜像 watch-mcp.focuslink-poyi-6465e9.workers.dev（Cloudflare Workers + D1）
```

- **同步代理**：`C:\Program Files\Poyi\FleetWatchdog\cloud_sync.py`（源码在
  PersonalMcpGateway 仓库 `fleet/cloud_sync.py`），由看护服务 PoyiFleetWatchdog
  每 ~5 分钟驱动一轮：调用本机 Watch MCP 的只读工具，把结果推到云端 `/sync/push`。
- **诚实语义**：手机不在线时本机工具返回 `PHONE_OFFLINE`，同步代理**跳过**该轮
  （云端保留上一次成功的快照）；云端工具永远返回 `state: synced/stale/never_synced`
  + `syncedAt`，绝不假装设备在线。
- 云端工具集：watch_get_status / watch_summarize_workouts / watch_list_workouts /
  watch_get_latest_sleep / watch_summarize_sleep / watch_list_plans / watch_get_sync_overview。
- Worker 源码：`C:\开发\mcp开发\watch-cloud-mcp`（D1 表 snapshots，访问密钥在
  Worker Secret，连接 URL 见该目录 `.dev.vars`，不入 git）。

## 下一步（需要动手机 App 时再做）

真正的"只要有网、任何一台设备都能同步"终态：手机 App 直接向云端 `/sync/push`
出站推送（无需电脑开机）。设备上行使用独立 `SYNC_KEY` Bearer，不得把 ChatGPT/Claude
连接器所用的 `ACCESS_KEY` 嵌入 APK。
`{"source":"phone","snapshots":{"watch_summarize_workouts": <json>, ...}}`。
届时电脑侧代理自动退化为冗余通道，无需改云端。
