# 架构与开发规范

状态：维护中  
基线：2026-07-25

## 1. 总体架构

```text
手表 app (Android API 30)
  UI Activities
      -> WorkoutService（训练状态唯一运行时来源）
      -> PlanStore / PlanLibraryStore / HistoryStore
      -> SystemExerciseBridge / SystemSleepBridge / SystemGpsBridge / Android Sensors
      -> WatchBridgeService :8765 + mDNS
                    ^
                    | 局域网 HTTP + X-Pairing-Code
                    v
手机 phone (Android API 29+)
  MainActivity / HistoryDetailActivity
      -> PhonePlanLibrary schema 3（多计划主库 + 显式删除 tombstone）
      -> WatchClient
      -> PhoneLocationRelayService
      -> PhonePlanBridgeService :8766
      -> EncryptedWatchSync / WorkManager
                    |
                    | HTTPS + SyncEnvelopeV1 密文
                    v
              /sync/v2/exchange
                    ^
                    | 本地 HTTP
                    v
电脑 mcp (Python)
  watch_intervals_mcp.py / buxu_remote_mcp.py
      -> OpenAI Secure MCP Tunnel（固定 Tunnel ID、出站长轮询）
```

## 2. 模块职责

| 模块 | 关键类/文件 | 职责 |
| --- | --- | --- |
| 手表 UI | `MainActivity`、`PlanActivity`、`StageEditorActivity`、`WarmupActivity`、`TrainingActivity`、`HistoryActivity` | 导航、权限、计划编辑和状态展示 |
| 训练引擎 | `WorkoutService` | 状态机、计时、阶段推进、传感器融合、检查点、通知、震动、历史落盘 |
| 训练模型 | `Stage`、`WorkoutRecord` | 阶段和历史 JSON schema |
| 本地存储 | `PlanStore`、`PlanLibraryStore`、`HistoryStore` | 当前计划、多计划库、最多 200 条历史 |
| 传感器桥 | `SystemExerciseBridge`、`SystemSleepBridge`、`SystemGpsBridge` | 厂商 HealthKit 动态能力、系统睡眠只读转换与系统 GPS 控制 |
| 手表 API | `WatchBridgeService` | 配对、mDNS、计划/历史/睡眠/控制/手机定位中继 |
| 手机伴侣 | `phone/*` | 计划库、发现配对、同步、历史详情、定位中继 |
| 手机加密同步 | `EncryptedWatchSync`、`EncryptedWatchSyncWorker`、`CloudSyncCredentials`、`WatchSyncKeyPackages` | V2 密文交换、持久 outbox/cursor/conflict/projection、Keystore 包装、恢复包和后台 catch-up |
| MCP | `mcp/*.py` | 将手表和手机 HTTP API 暴露为本地工具 |

## 3. 核心状态和不变量

训练状态按 `idle -> preparing -> running <-> paused -> completed/stopped` 演进。

必须保持以下不变量：

1. `WorkoutService` 是活动训练状态的唯一所有者；Activity 只订阅快照和发送 action。
2. 暂停期间不增加活动时间、阶段时间、距离和步数。
3. 每个阶段只记录一次完成结果；最后阶段只保存一次历史。
4. 距离来源切换时必须重新建立累计基线，不可重复累计旧样本。
5. 轨迹点只在通过精度、速度、时间间隔过滤后入库。
6. 恢复会话必须兼容旧检查点；损坏检查点应清除或跳过坏字段，不阻塞新训练。
7. 缺失/过期的心率显示为未知，不能沿用无限期旧值。
8. 本地列表暂时缺项不代表删除；只有与 schema 3 计划库同次提交的显式 tombstone 能生成远端 delete。
9. ACK、change materialize、projection 待办与 cursor 推进必须在同一份持久同步状态提交；projection 可幂等重放。
10. 所有前台与 WorkManager 同步入口共享凭据生命周期锁，禁止两个 `Store` 实例并发覆盖 outbox/cursor；`syncAsync` 在调用线程只置 dirty，避免与计划库锁形成反序死锁，并在运行中变更后自动再收一轮。

## 4. 数据和存储

| 数据 | 位置 | 当前 schema/上限 | 说明 |
| --- | --- | --- | --- |
| 当前计划 | SharedPreferences `plans` | 阶段 JSON 数组 | 含名称、分组、要求 |
| 多计划库 | SharedPreferences `plan_library_v2` | schema 3 | 手机为主库；`deletedPlanIds` 保存显式 tombstone，schema 2 向后读取 |
| 活动会话 | SharedPreferences `active_session` | 兼容 schema 2 前检查点 | 用于进程/任务恢复 |
| 训练历史 | `files/workout_history.json` | `WorkoutRecord` schema 2，200 条 | 临时文件写入后替换 |
| 配对码 | SharedPreferences `bridge` | 六位十进制字符串 | 当前持久保存，不自动轮换 |
| MCP 配置 | `%USERPROFILE%/.watchintervals.json` | host/port/phoneHost/phonePort/pairingCode | 禁止提交真实配置 |
| Tunnel 凭据 | `%LOCALAPPDATA%/WatchIntervals/tunnel` | DPAPI CurrentUser + 本地 profile | Runtime Key 不写入仓库、命令行和日志 |
| 手机加密同步 | SharedPreferences `encrypted_watch_sync_v1` + Android Keystore | protocol 2 / envelope 1 | endpoint/deviceId 为非密字段；device token、root、state/outbox/cursor/conflict/projection 持久化，备份禁用 |

任何 schema 变更都要：提升 schema 版本、保留向后读取、增加迁移/损坏数据测试、更新本表与 CHANGELOG。

Phone manifest 设置 `allowBackup=false`，并在 Auto Backup/device-transfer 规则中显式排除 `encrypted_watch_sync_v1.xml` 与仍含局域网配对信息的 `connection.xml`。Keystore 包装 key 不可导出；同步 root 跨设备只能走恢复包或已授权设备批准包。

## 5. 距离与传感器策略

当前优先级为：厂商原生距离 -> 手表 GPS/手机中继坐标 -> 步数估距。

- 原生距离超过 10 秒没有新样本后降级。
- GPS 获取允许精度上限 200 m，跟踪允许 150 m；合理速度范围为 0.15 至 15 m/s。
- 连续定位间隔超过 60 秒时重建轨迹基线；GPS 75 秒无更新视为过期。
- 步长默认 0.72 m；单次 step delta 超过 50 不累计。
- 心率有效范围 25 至 240 bpm，15 秒无新样本视为过期。

这些阈值改变属于产品行为变化，必须关联需求/缺陷编号并进行户外真机对比。

## 6. 本地 API 合约

### 手表 `:8765`

所有请求要求 `X-Pairing-Code`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /v1/status` | 设备、版本、活动会话、后台定位、传输状态 |
| `GET/PUT /v1/plan` | 当前阶段列表 |
| `GET/PUT /v1/plan/profile` | 当前计划名称、分组、要求和阶段 |
| `GET/PUT /v1/plan-library` | 完整计划库 |
| `PUT /v1/plan-selection` | 选择计划 |
| `GET /v1/history` | 全部历史 |
| `GET/DELETE /v1/history/{id}` | 详情或删除 |
| `GET /v1/sleep?days=1..31` | 系统睡眠记录、session 和原始阶段时间线；默认 7 天 |
| `POST /v1/location` | 手机定位中继 |
| `POST /v1/control/{start|pause|resume|toggle|stop}` | 训练控制 |

### 手机 `:8766`

手机计划库为计划分组和多计划的主数据源，MCP 通过该端口读写后同步至手表。协议细节以 `PhonePlanBridgeService` 和 `mcp/watch_intervals_mcp.py` 为准；新增端点时必须补充独立契约测试。

### 协议规范

- 请求/响应使用 UTF-8 JSON；请求体当前限制 256,000 字节。
- 睡眠响应的 `state` 为 `ready`、`permission_required` 或 `error`，`source=system_healthkit`。duration 字段单位为分钟，时间戳单位为毫秒；stage 同时保留厂商 `type` 和不推断语义的 `system_N` 标签。
- 2xx 表示处理成功；4xx 返回稳定错误码；控制接口要保持幂等语义，当前 `pause/resume` 的 toggle 实现见 `BUG-002`。
- 局域网 API 使用明文 HTTP，仅用于受信网络；安全改进见 `BUG-003`。

### 加密 V2 数据面

手机使用 HTTPS `POST /sync/v2/exchange`，请求固定为 protocol 2、envelope 1、product `watch`。计划、保留分组/当前选择及删除 ledger 的 `sync:library` 元数据实体，以及训练摘要在设备端使用 AES-256-GCM 加密；AAD 绑定产品、实体类型、实体 ID、目标 revision、操作和 key version。原始轨迹、逐点心率、睡眠、第三方凭据和未知字段通过 allowlist 排除，workout envelope ID 使用本地 ID 的 SHA-256，不暴露时间型记录 ID。

本地 `state` 一次提交保存 entity、outbox、flight lease、conflict、Phone/Watch projection 待办和严格 base36 cursor。首次建立 root 后必须 pull-first；ACK/change 验证成功后，outbox 删除、cursor 推进和 projection 入队原子提交，再幂等写入 schema 3 计划库并通过现有 `WatchClient` 回写手表。revision conflict 保留本地候选与解密后的远端候选，不自动覆盖手机计划库。训练摘要为 immutable 云实体；训练 pull 保留在加密同步 state 中，本阶段不新增历史导入或 UI。

协议 delete mutation 按云合同不携带 ciphertext/nonce，因此客户端不能只信公开 `aadHash`。每个 plan delete 还必须命中 root 加密的 `sync:library.deletedPlanIds` ledger；缺少这份第二证据时 projection 持久挂起，绝不删除本地计划。已 ACK tombstone 继续留在加密 ledger 中但不再进入待发送 delete 集合，计划重新创建时才移除。当前威胁模型仍信任云端的 change ordering/availability；抗云端 rollback 不在本阶段声明范围。

device token 与 32-byte root 分别由 Android Keystore AES key 包装。首台设备必须显式初始化 root；已有空间通过 PBKDF2-HMAC-SHA256（310,000 次）恢复包，或绑定目标 deviceId、一次性 nonce、公钥 fingerprint 和 10 分钟有效期的 3072-bit RSA 批准包导入。凭据/root 变更和所有 sync 入口共用生命周期锁；HTTP 401 使用本次请求 token compare-and-clear，只有清除持久成功才取消后台任务。400/403/409/413/415 等永久 4xx 记录可操作状态并停止 WorkManager retry。当前收敛阶段没有配置 UI；已配置安装可由服务启动与开机接收器恢复调度。

## 7. 开发环境和构建

前置条件：JDK 17、Android SDK 35、Gradle 8.14.3。Phone 加密同步使用 WorkManager 2.10.1。当前仓库未包含 Gradle Wrapper（`BUG-004`）。

```powershell
gradle :app:assembleDebug :phone:assembleDebug
adb -s WATCH_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE_SERIAL install -r phone/build/outputs/apk/debug/phone-debug.apk
```

百度地图 Key 通过 Gradle 属性传入，禁止硬编码或提交真实值：

```properties
BAIDU_MAP_AK=YOUR_LOCAL_KEY
```

默认值 `TOKEN` 只允许用于不依赖百度地图能力的开发构建。

## 8. 编码规范

1. Java 17；包名保持 `com.poyi.watchintervals` / `.phone`。
2. 新状态必须由训练服务产生不可变快照，避免 Activity 保存第二份运行状态。
3. 所有持久数据写入要考虑崩溃、旧 schema、空值和损坏内容。
4. 禁止新增空 `catch`；可恢复异常至少写 tag、操作、错误类型，且不得含配对码或轨迹。
5. 网络动作必须设置连接/读取超时，校验状态码和 JSON 字段。
6. 378×496 手表界面固定执行文字溢出、底部安全区、横纵手势冲突检查。
7. 修复缺陷时先在 `bugs.md` 建号，再补测试或可复现验证步骤。
8. 厂商健康数据通过公开 Store Binder 和运行时匹配的 protobuf 类读取；禁止提交厂商 APK、反编译产物、权限记录或真实健康数据。
9. 加密同步协议字段、AAD、cursor、ACK 与 revision 必须按合同验证；不可把网络返回直接投影到计划库。

## 9. Git 和发布规范

- 分支：`main` 始终保持可构建；功能分支建议 `feat/REQ-*`，修复分支建议 `fix/BUG-*`。
- 提交：`type(scope): summary`，正文写需求/缺陷编号和验证命令。
- 版本：手表和手机独立维护 `versionName/versionCode`；每次分发必须递增对应版本。
- APK：不提交 Git，上传 GitHub Release；文件名含模块、版本和构建类型。
- Release 记录：提交 SHA、构建命令、测试结果、APK SHA-256、已知问题。
- 正式发布应使用受控 release keystore；当前 debug 预发布不得描述为正式生产包。

## 10. ChatGPT 长效通道

- ChatGPT 插件绑定 OpenAI Tunnel ID，不再依赖会变化的 Quick Tunnel URL。
- `install_persistent_chatgpt_tunnel.ps1` 只在首次安装时读取 Runtime Key，并用 Windows DPAPI CurrentUser 加密保存。
- `run_persistent_chatgpt_tunnel.ps1` 由用户登录计划任务启动，使用互斥锁避免重复实例，tunnel-client 退出后等待 5 秒重连。
- 本地健康端点仅监听环回地址；`check_persistent_chatgpt_tunnel.ps1` 检查任务、凭据、`healthz` 和 `readyz`。
- 删除或轮换 Runtime Key 后必须重新执行安装脚本；仓库和发布包不得包含 profile、密钥、Tunnel ID 或运行日志。
