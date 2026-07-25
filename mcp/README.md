# 步序运动 MCP

本地 MCP 通过手表配对 API 查询完整训练历史、轨迹、实际步数、心率、训练计划，并控制开始、暂停、继续和结束。

已提供状态、命名计划/分组/要求读写、阶段读写、训练聚合统计、历史列表、单条完整轨迹、系统睡眠列表/最近记录/汇总，以及开始、暂停、继续、结束、删除。`watch_status.backgroundLocation` 应为 `true`，以保证从手机或 MCP 后台启动后仍能连续定位。

睡眠工具直接读取手表系统 HealthKit，首次使用需在手表打开步序并确认“读取睡眠数据”。duration 单位为分钟，时间戳为 Unix 毫秒；阶段保留厂商原始 `type`，`system_N` 标签不额外推断医学语义。

`set_training_plan_profile` 会以名称和分组生成稳定计划 ID，依次写入手机主计划库、选择该计划、同步到手表，并回读手机计划库、手表计划库和手表当前 profile。只有三处数据和阶段全部一致时才返回 `verified: true`；手机离线、手表同步 pending 或回读不一致都会返回 MCP 错误。

同步逻辑单元测试：

```powershell
python -m unittest discover -s mcp\tests -v
```

配置文件默认为 `%USERPROFILE%/.watchintervals.json`：

```json
{"host":"192.168.1.44","port":8765,"phoneHost":"192.168.1.84","phonePort":8766,"pairingCode":"手表首页显示的六位码"}
```

Codex/ChatGPT MCP 启动命令：

```text
python C:\开发\手表开发\mcp\watch_intervals_mcp.py
```

可直接复制 `chatgpt-mcp-config.json` 中的 `buxu-sports` 配置到支持本地 stdio MCP 的 ChatGPT/Codex 客户端。Windows 也可以直接运行 `start_buxu_mcp.cmd`。

## ChatGPT 网页远程连接

ChatGPT 网页使用 OpenAI Secure MCP Tunnel 连接本机服务。项目已包含官方
`tunnel-client` Windows x64 客户端和配置脚本：

1. 运行 `打开ChatGPT远程连接设置.cmd`。
2. 在 Platform 创建 Tunnel，复制 `tunnel_id`，再创建 Tunnel Runtime API Key。
3. 执行：

```powershell
powershell -ExecutionPolicy Bypass -File C:\开发\手表开发\mcp\setup_chatgpt_tunnel.ps1 -TunnelId tunnel_xxx
```

脚本会安全提示输入 Runtime API Key，完成 MCP 启动、Tunnel 检查和连接。
以后只需运行 `run_chatgpt_tunnel.ps1`。ChatGPT 中创建开发者模式 App 时，
Connection 选择 **Tunnel**，再选择同一个 `tunnel_id`。

所有数据默认停留在手表、手机和本机；MCP 只响应已配对的本地请求。

计划与分组以手机计划库为准。MCP 可创建、重命名、删除分组，增删改选计划，并通过 `sync_plan_library` 立即把完整计划库推送到手表；训练状态与历史仍直接读取手表。
