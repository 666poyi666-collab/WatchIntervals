# 步序云同步（加密 V2）

> 基线：2026-07-29。目标是电脑关机后，由手表、手机和 Watch Cloud MCP 完成只读数据链；旧 `/sync/push`/电脑同步代理已经退役。

## 当前数据流

```text
手表训练成功落盘
  -> 已认证 AES-GCM BLE history_changed（只含事件名和版本）
  -> 手机读取 authenticated /v1/history
  -> WorkManager（网络约束、唯一任务、指数退避、15 分钟周期）
  -> HTTPS /sync/v2/exchange（device token）
       -> canonical encrypted_sync_*：AES-256-GCM 密文、revision、ACK、cursor、conflict
       -> watch_read_projection：严格允许字段的最小可读 projection
  -> OAuth watch:read
  -> Watch Cloud MCP
  -> ChatGPT
```

电脑、Windows Watch MCP、Tunnel、ADB 和同一局域网都不在这条云端读取路径上。手机可以使用蜂窝或 Wi-Fi；手表只需通过 BLE 把变化提示给手机。提示丢失时，BLE/LAN 成功重连和 15 分钟周期任务会再次执行 catch-up。

## 权限和数据边界

- `/sync/v2/exchange` 只接受专用 device token；OAuth token 不能调用同步路由。
- `/mcp` 只接受签名、issuer/audience/resource/scope/introspection 均通过的 OAuth `watch:read` token；device token 不能调用 MCP。
- authority observation 不是公网或 Gateway 数据面：中央签名 authority 只经命名 service binding，以 vendor `Accept`、独立 `Capability` 和完整 HTTPS `/authority/watch` audience 读取。Worker 返回 exact-field、checkpoint 派生且同 revision 不可变的 observation，本身不签名。
- canonical 计划和训练摘要只以 AES-256-GCM 密文存储；根密钥不离开已授权设备。
- 为满足用户允许的云端实际读取，手机在同一次设备认证 exchange 中附带严格最小 projection：
  - 计划：哈希实体键、名称；
  - 训练：哈希实体键、计划/自由类型、开始/结束、活动时长、距离、步数。
- MCP 隐藏实体键，只返回计划名、粗粒度训练、encrypted sync 状态/新鲜度，以及次数、时长、距离、步数活动健康汇总。
- 原始轨迹、坐标、逐点心率、睡眠、凭据、根密钥、设备私钥和诊断正文不上传到 projection；未知或多余字段在写入前拒绝，D1 行在读取前再次验证。

## 可靠性边界

- 手机本地 `state` 同时持久化 entity、outbox、flight lease、conflict、projection pending 和 cursor。
- ACK 移除、远端 materialize、冲突留存和 cursor 推进必须同一次 `commit()` 成功。
- 首次/换 root 必须先 pull bootstrap；计划删除只来自 schema 3 显式 tombstone；训练是 create-once immutable fact。
- WorkManager 使用 `ExistingWorkPolicy.KEEP` 去重一次性工作；网络恢复、Doze、进程回收和开机由持久任务/receiver 恢复。
- 未配置或 Keystore 解密失败时 fail closed，不生成替代 token/root，也不无限重试。

## 尚未完成的验收

当前本地实现和自动化不能代替真实设备结论。必须继续完成：

1. OWW221 + 手机真实 `history_changed` indication 与重复事件/断联重连测试（`WT-025`、`BLE-011`）。
2. 手机 Keystore 恢复包/设备批准真机负测（`PT-016`、`PT-017`）。
3. 蜂窝、Wi-Fi、后台 Doze、重启三轮 PC-off catch-up（`PT-018`、`PT-020`）。
4. staging Worker migration、build attestation、OAuth metadata/readiness 已通过；仍需真实 Phone 产生非空 read projection，并用短期 `watch:read` token 执行 `watch-cloud-mcp` 的 `npm run test:staging:mcp`，完成实际计划/训练/状态/活动汇总回读。

在上述证据齐全前，项目 manifest 必须保持 `supportsPcOff=false`，不得把本地绿色门禁描述为生产可用。
