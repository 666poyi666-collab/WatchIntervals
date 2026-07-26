# 架构与开发规范

状态：维护中  
基线：2026-07-26

## 1. 总体架构

```text
手表 app (Android API 30)
  UI Activities
      -> WorkoutService（训练状态唯一运行时来源）
      -> PlanStore / PlanLibraryStore / HistoryStore
      -> SystemExerciseBridge / SystemSleepBridge / SystemGpsBridge / Android Sensors
      -> WatchCommandRouter
           -> WatchLinkService（BLE Peripheral/GATT Server）
           -> WatchBridgeService :8765 + mDNS（LAN 加速）
                    ^
                    | BLE 主链路 / LAN 加速
                    v
手机 phone (Android API 29+)
  MainActivity / HistoryDetailActivity
      -> PhonePlanLibrary（多计划主库）
      -> WatchConnectionManager
           -> BleGattTransport（Central/GATT Client）
           -> LanHttpTransport
      -> PhoneLocationRelayService
      -> PhonePlanBridgeService :8766
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
| 手表连接 | `WatchCommandRouter`、`WatchLinkService`、`WatchBridgeService` | BLE/LAN 共享业务路由、GATT Peripheral、LAN 加速与 mDNS |
| 手机伴侣 | `WatchConnectionManager`、`BleGattTransport`、`LanHttpTransport`、`phone/*` | 连接状态、传输选择、计划库、同步、历史详情、定位中继 |
| MCP | `mcp/src/watch_mcp` | 独立 Watch MCP；只通过手机 8766 业务门面访问本项目 |

## 3. 核心状态和不变量

训练状态拆为两个维度：会话 `PREPARING -> RUNNING <-> PAUSED -> STOPPED`，计划 `ACTIVE -> COMPLETED`。计划完成后会话继续处于 RUNNING/PAUSED 并进入自由记录。

必须保持以下不变量：

1. `WorkoutService` 是活动训练状态的唯一所有者；Activity 只订阅快照和发送 action。
2. 暂停期间不增加活动时间、阶段时间、距离和步数。
3. 每个阶段只记录一次完成结果；最后阶段只保存一次历史。
4. 距离来源切换时必须重新建立累计基线，不可重复累计旧样本。
5. 轨迹点只在通过精度、速度、时间间隔过滤后入库。
6. 恢复会话必须兼容旧检查点；损坏检查点应清除或跳过坏字段，不阻塞新训练。
7. 缺失/过期的心率显示为未知，不能沿用无限期旧值。
8. checkpoint 是轨迹和心率文件的提交边界；恢复累计值前必须截断 offset 后的完整或损坏尾行，不能保留未被统计确认的样本。

## 4. 数据和存储

| 数据 | 位置 | 当前 schema/上限 | 说明 |
| --- | --- | --- | --- |
| 当前计划 | SharedPreferences `plans` | 阶段 JSON 数组 | 含名称、分组、要求 |
| 多计划库 | SharedPreferences `plan_library_v2` | schema 2 | 手机为主库，手表保留同步副本 |
| 活动会话 | `files/active_workouts/<id>/` | checkpoint v1 + NDJSON | 标量检查点原子替换；轨迹/心率追加写入；恢复时按已确认 offset 截断尾部 |
| 训练历史 | `files/workouts/<id>/` + `workout_index.json` | `WorkoutRecord` schema 3，200 条 | 摘要索引与每条记录样本文件分离；旧单文件自动迁移 |
| BLE 身份 | SharedPreferences `watch_identity` / `bridge` | 稳定设备 ID + 过渡六位码 | 当前仅为 debug 认证；正式密钥与挑战响应关联 `BUG-015` |
| Watch MCP 数据 | `%ProgramData%/Poyi/WatchMcp` | 已验证手机身份、DPAPI 密文、独立服务日志 | 不保存固定 IP、明文令牌或其他项目数据 |
| Watch Tunnel 凭据 | `%ProgramData%/Poyi/WatchMcp` | DPAPI LocalMachine + Watch 专属 Tunnel ID | Runtime Key 不写入仓库、命令行和日志 |

任何 schema 变更都要：提升 schema 版本、保留向后读取、增加迁移/损坏数据测试、更新本表与 CHANGELOG。

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
| `GET /v1/history` | 历史摘要列表，不含完整样本 |
| `GET/DELETE /v1/history/{id}` | 详情或删除 |
| `GET /v1/history/{id}/route?cursor=&limit=` | 分页读取原始 WGS-84 轨迹 |
| `GET /v1/history/{id}/heart?cursor=&limit=` | 分页读取心率样本 |
| `GET /v1/sleep?days=1..31` | 系统睡眠记录、session 和原始阶段时间线；默认 7 天 |
| `POST /v1/location` | 手机定位中继 |
| `POST /v1/control/{start|pause|resume|toggle|stop}` | 训练控制 |
| `POST /v1/sync/operations` | 计划 outbox 操作去重与 ACK |

### 手机 `:8766`

手机计划库为计划分组和多计划的主数据源，独立 Watch MCP 通过该端口读写后同步至手表。协议细节以 `PhonePlanBridgeService` 和 `mcp/src/watch_mcp` 为准；新增端点时必须补充独立契约测试。

手机同时广播 `_watchintervals-phone._tcp.`。`/v1/status` 返回稳定 `phoneDeviceId` 与 `protocolVersion`；Windows Watch MCP 只把 IP 当运行时端点，旧地址失败后通过 mDNS 发现并校验身份。

`POST /v1/auth/token` 用于一次性签发独立 Watch MCP Bearer Token。未迁移设备可使用当前 6 位配对码 bootstrap；完成安全 BLE 配对且旧码已清除的设备，使用已配对长期 LAN 凭据 bootstrap。签发请求仍要求 UUID `requestId` 与 `expectedRevision`，重复请求返回首次 token，旧 revision 或已有 token 的新请求返回 409。token 不写入日志、仓库或命令行。

Watch MCP 使用手机 API v1 写入契约：`POST/PUT /v1/plans[/id]` 的正文为
`{requestId, expectedRevision, plan}`，`PUT /v1/plan-selection` 为
`{requestId, expectedRevision, planId}`。手机持久保存请求哈希、状态和首次结果；相同请求重放
首次结果，ID 复用或 revision 冲突返回 409。执行前同步提交 `in_progress`，若进程在计划库提交后、
结果缓存提交前终止，重试通过单调 library revision 恢复结果，不再次执行写入。旧的直接计划正文继续
旧客户端仅保留迁移参考，正式 Watch MCP 不使用旧格式。

### 协议规范

- 请求/响应使用 UTF-8 JSON；请求体当前限制 256,000 字节。
- 睡眠响应的 `state` 为 `ready`、`permission_required` 或 `error`，`source=system_healthkit`。duration 字段单位为分钟，时间戳单位为毫秒；stage 同时保留厂商 `type` 和不推断语义的 `system_N` 标签。
- 2xx 表示处理成功；4xx 返回稳定错误码。控制接口使用 `commandId`、`expectedState` 与 `expiresAt`；重复命令返回缓存结果，过期命令不执行。
- 手机计划写接口使用 UUID `requestId` 与 `expectedRevision`；409 响应区分 revision conflict 和 request ID reuse。幂等缓存最多保留 500 个最近请求。
- 局域网 API 使用明文 HTTP，仅用于受信网络；安全改进见 `BUG-003`。

## 7. 开发环境和构建

前置条件：JDK 17、Android SDK 35。仓库 Wrapper 锁定 Gradle 8.14.3。

```powershell
.\gradlew.bat :app:assembleDebug :phone:assembleDebug
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

## 9. Git 和发布规范

- 分支：`main` 始终保持可构建；功能分支建议 `feat/REQ-*`，修复分支建议 `fix/BUG-*`。
- 提交：`type(scope): summary`，正文写需求/缺陷编号和验证命令。
- 版本：手表和手机独立维护 `versionName/versionCode`；每次分发必须递增对应版本。
- APK：不提交 Git，上传 GitHub Release；文件名含模块、版本和构建类型。
- Release 记录：提交 SHA、构建命令、测试结果、APK SHA-256、已知问题。
- 正式发布应使用受控 release keystore；当前 debug 预发布不得描述为正式生产包。

## 10. 独立 Watch MCP 与 ChatGPT 通道

- 正式链路固定为 `ChatGPT -> Watch 专属 Tunnel -> PoyiWatchMcp -> 手机 8766 -> BLE/LAN -> 手表`。
- `PoyiWatchMcp` 只监听 `127.0.0.1:8768`，同端口提供 `/mcp`、`/healthz`、`/readyz`、`/metrics`；避开 PersonalMcpGateway 的 8760/8761，且不加载其模块。
- MCP 只发现手机 `_watchintervals-phone._tcp.local.`，以 Bearer Token 认证并固定首次验证的 `phoneDeviceId`。它不直接连接手表、不读取 Android 数据库、不使用 ADB 或固定 IP。
- `PoyiWatchTunnel` 使用独立 Tunnel ID、独立 Runtime Key 和 `127.0.0.1:8880` 健康端口，仅连接 Watch MCP。两个 WinSW 服务均自动启动和失败重启。
- 手机 API Token 与 Tunnel Runtime Key 分别用 DPAPI LocalMachine 和不同 entropy 保存，日志过滤令牌、精确位置和正文。
- `watch_*` 工具只返回摘要；轨迹、心率和完整睡眠使用 `watch://` Resource 分页读取。
- 旧 `personal_gateway.py`、Quick Tunnel、固定 IP/六位码配置和直接连接手表脚本已删除；有效工具、Schema、错误映射和发现逻辑已迁入独立包。

## 11. BLE 连接架构与边界

- 角色固定为手机 Central/GATT Client、OWW221 Peripheral/GATT Server；旧 `BleProbeService` 已删除，正式服务均 `exported=false`。
- GATT 服务包含设备信息、配对、控制、事件、同步收发、定位、LAN endpoint 和心跳特征；手机顺序订阅 indication 后认证。
- 消息使用 16 字节帧头，兼容默认 MTU 23；单帧同时受 `MTU-3` 与 512 字节属性值上限约束，消息上限 256 KB，不完整帧 30 秒清理。
- `WatchConnectionManager` 负责 BLE 优先、LAN 加速、状态快照和退避。控制、计划、同步与定位优先 BLE；历史/睡眠等批量读取优先已验证 LAN，失败可回退 BLE。
- 2026-07-26 真机已验证 OWW221 广播、Xiaomi 连接、MTU 517、四个 CCCD、AUTH、计划 outbox、计划回读和定位请求。
- 安全版使用 P-256 ECDH、一次性验证码 HMAC 确认、长期配对密钥、challenge-response、AES-GCM、随机 sequence 和持久认证 challenge 防重放；真机门禁通过前仍关联 `BUG-015`。无 Wi-Fi、后台、重启、5 分钟息屏、10 次重连、100 次请求和 15 分钟功耗关联 `BUG-016`。
