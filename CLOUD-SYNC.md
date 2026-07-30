# 步序云同步（Cloud V3）

> 基线：2026-07-30。目标生产链路是 `手表 <-> 手机 <-> 云端 <-> Cloud MCP <-> ChatGPT`。电脑、本地 MCP、Tunnel 和手机 8766 不属于最终链路。

## 当前数据流

```text
手表 WorkoutService / HistoryStore / SystemSleepBridge
  <-> 安全 BLE 主链路（LAN 仅加速）
手机 Phone 0.23.0
  -> HTTPS POST /sync/v3/exchange（Device Bearer Token）
  -> WSS /sync/v3/channel（只接收 sync_needed）
  -> D1 V3 authority
  -> Cloud MCP（watch:read / watch:write / watch:control）
  -> ChatGPT
```

手机保存离线计划缓存、V3 outbox/cursor/receipt/conflict 和 Keystore 包装的 device token。云端是计划主版本；手表 `WorkoutService` 仍是活动训练状态唯一权威。V2 源码/state 暂时保留用于迁移回退，但 Phone 0.23.0 不启用、不双写，也不会在 V3 失败时自动退回。

## 云端保存范围

允许永久保存：

- 完整计划组、计划、阶段、当前选择和单调 revision；
- 训练摘要、阶段结果、公里分段、距离、配速、步数、步频、速度、爬升、平均/最低/最高心率和数据来源摘要；
- 睡眠 record、session、stage、评分、血氧、心率、呼吸及系统原始字段；
- 设备 checkpoint、同步新鲜度、实时状态、操作幂等结果、命令和审计。

始终 local-only：

- 原始轨迹数组、经纬度、坐标集合；
- 逐点心率样本；
- 配对码、设备/OAuth token、第三方凭据、私钥和诊断正文。

Phone 只从手表 `/v1/history` 读取 summary；任何 V3 请求出现 `route`、`latitude`、`longitude`、`coordinates` 或 `heartRateSamples` 都在 Phone 端拒绝，Worker 再做一次 exact-field 校验。业务正文不做应用层 E2EE，HTTPS、安全 BLE、OAuth 和 Keystore token 包装继续保留。

## Exchange 可靠性

- `POST /sync/v3/exchange` 使用 requestId/deviceId/cursor，plan/workout/sleep 各最多 25 项；重复 ID 同正文返回首次结果，不同正文复用 ID 拒绝。
- 所有进程内 exchange 串行。active request 在网络前 `commit()`，失败后原样重试；`cursor_ahead` 只按服务器 `resetCursor` 清 active request 并重建，不丢 outbox。
- plan 使用 expected revision OCC。普通 conflict 从 outbox 移入持久 conflict store，保留本地 candidate、ACK 和服务器计划库；HTTP 往返期间本地 revision/fingerprint 变化时，旧响应不得覆盖新编辑。
- workout 是 create-once fact；同 ID 同内容幂等，不同内容冲突。训练删除只在手表 command ACK 后由云端写独立 tombstone，后续上传不能复活。
- 睡眠首次回填最近 31 天，此后增量更新；暂时读不到不推断删除。
- `watch_cloud_v3.xml` 被 Auto Backup 和 device transfer 排除。

## 命令通道

- `/sync/v3/channel` 只发送 exact `{type:"sync_needed"}`；业务正文仍由 exchange 拉取。
- WebSocket 消息直接触发轻量 command exchange，不先扫描训练历史或 31 天睡眠；WorkManager 只做后台/重启补偿。
- Phone 运行中补配置凭据后会自动重连；同一实例最多保留一个 reconnect timer。
- 成功执行命令后，同一次 `sync()` 立即做第二次 exchange 回传结果。Cloud MCP 最多等待 10 秒，超时返回可查询的 pending。
- 手表离线时 Phone 不提前写失败 ACK；命令保持 pending/delivered，30 秒后由云端过期。Phone 每次执行前检查 expiresAt，恢复连接后绝不执行旧命令。
- 删除训练走 `/v1/control/delete_workout`，复用手表持久 command cache；相同 ID 返回首次结果，不同正文复用 ID 返回 409。

## OAuth 和 MCP

- `watch:read`：状态、同步新鲜度、计划、训练、统计和睡眠。
- `watch:write`：计划组、计划、选择计划和删除训练。
- `watch:control`：开始、暂停、继续、停止和命令状态。
- `offline_access` 只作为连接协议 scope，不授予 Watch 数据权限。
- device token 不能调用 MCP，OAuth token 不能调用 exchange。
- authority observation 只读取 V3 checkpoint/device/cursor；真实验收前 `supportsPcOff=false`。

## 尚未完成的验收

本地自动化和 APK 构建不能替代以下门禁：

1. 用户手动重新授权真实 ChatGPT connector 新 scope；代理不得代点 consent。
2. 完成一条真实超过 1 公里的训练，使云端出现非空公里分段；现有真实训练已回读摘要和平均心率，但没有 splits。
3. D1/MCP/运行日志最终扫描确认无坐标、轨迹数组、逐点心率、凭据和 token。
4. 实际关闭电脑与全部 Windows 服务，完成手机前台、后台 Doze、手机重启三轮 PC-off。

已完成的 staging 真机证据：真实 Phone receipt、5 个计划、3 条训练、24 条睡眠；最新睡眠包含 session/stage；start/pause/resume/stop 均在 10 秒内 ACK；离线 start 命令 30 秒过期且恢复后未 delivered、未执行；Cloud MCP 临时计划经 Phone 和安全 BLE outbox 到达 Watch，随后云端、Phone、Watch 三处精确回滚。

V3 staging 已完成基础远端合同，但生产发布、ChatGPT OAuth consent、Windows 服务卸载和本地 MCP 删除仍需在对应门禁后由用户确认执行。生产非空 MCP 回读、三轮 PC-off 和服务卸载全部通过前，不关闭 `BUG-041`，不设置 `supportsPcOff=true`。
