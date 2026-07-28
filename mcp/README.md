# WatchIntervals 独立 MCP

本目录提供步序（WatchIntervals）自己的 MCP Server。最终运行链路是：

```text
ChatGPT -> Watch 专属 Secure MCP Tunnel -> PoyiWatchMcp -> 手机 8766 API -> BLE/LAN -> 手表
```

它不加载 `PersonalMcpGateway`，不注册其他项目工具，不读取其他项目数据库，也不直接连接手表。手机是唯一业务门面，手机内部的 `WatchConnectionManager` 优先使用安全 BLE；ADB 只允许用于开发期安装和取证，不属于运行链路。

## 本地开发

```powershell
cd mcp
uv sync --extra dev
$env:WATCH_MCP_PHONE_TOKEN = '<独立 API 令牌>'
uv run poyi-watch-mcp serve
```

监听地址固定为 `127.0.0.1:8768`，避免与现有 PersonalMcpGateway 的 8760/8761 冲突：

- MCP：`/mcp`
- 存活：`/healthz`
- 就绪：`/readyz`
- 指标：`/metrics`

服务使用 `_watchintervals-phone._tcp.local.` 发现手机，以首次验证的 `phoneDeviceId` 固定身份；`phone-endpoint.json` 只缓存运行时地址，不把 IP 当身份。认证使用手机生成的 256 位 Bearer Token，Windows 端仅以 LocalMachine DPAPI 密文保存。

## 工具

- `watch_get_status`
- `watch_get_capabilities`
- `watch_get_current_plan`
- `watch_list_plans`
- `watch_get_plan`
- `watch_set_plan`
- `watch_delete_plan`
- `watch_select_plan`
- `watch_list_plan_groups`
- `watch_create_plan_group`
- `watch_rename_plan_group`
- `watch_delete_plan_group`
- `watch_list_workouts`
- `watch_get_workout`
- `watch_summarize_workouts`
- `watch_delete_workout`
- `watch_get_latest_sleep`
- `watch_summarize_sleep`
- `watch_start_workout`
- `watch_pause_workout`
- `watch_resume_workout`
- `watch_stop_workout`
- `watch_get_sync_status`
- `watch_sync_plans`

所有写工具要求 `request_id` 和 `expected_revision`；控制工具还要求 `command_id`、`expected_state` 和 `expires_at`。手机业务 API 持久保存幂等结果，所以 MCP 服务重启或网络重试不会重复执行。

## Resources

- `watch://status`
- `watch://capabilities`
- `watch://plans`
- `watch://workouts/recent`
- `watch://workouts/{workout_id}`
- `watch://workouts/{workout_id}/route/{cursor}`
- `watch://workouts/{workout_id}/heart/{cursor}`
- `watch://sleep/{days}`

轨迹、心率和完整睡眠明细不通过普通工具整批返回。

## Windows 服务与 Tunnel

管理员 PowerShell 中运行 `service/install.ps1` 安装 `PoyiWatchMcp`。提供独立 Tunnel ID 和 Runtime Key 时同时安装 `PoyiWatchTunnel`。两项服务均自动启动、失败重启，数据写入 `%ProgramData%\Poyi\WatchMcp`，程序写入 `%ProgramFiles%\Poyi\WatchMcp`。

该安装过程不会停止、替换或修改 `PoyiPersonalMcpGateway`、`OpenAISecureMcpTunnel` 或其他项目服务。Tunnel 只把独立 Watch MCP 的 `127.0.0.1:8768/mcp` 连接到 Watch 专属 tunnel ID。

旧的统一 `personal_gateway.py`、Quick Tunnel、固定 IP/六位码配置和直接连接手表入口已在迁移完成后删除，避免误启动第二套架构。
