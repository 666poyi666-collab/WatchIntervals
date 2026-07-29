# 架构与开发规范

状态：维护中  
基线：2026-07-29

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
            -> history_changed -> EncryptedWatchSyncWorker
       -> PhoneLocationRelayService
       -> PhonePlanBridgeService :8766
       -> EncryptedWatchSyncWorker -> HTTPS /sync/v2/exchange
                    |
                    v
       Watch Cloud MCP（密文 authority + 最小只读 projection）
                    ^
                    | OAuth watch:read
                    |
                 ChatGPT
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
| 手表 UI | `MainActivity`、`PlanActivity`、`WarmupActivity`、`TrainingActivity`、`HistoryActivity`、`Ui`、`WatchPagerLayout`、`WorkoutRouteView` | 导航、权限、计划选择和状态展示；`Ui` 统一视觉令牌与交互反馈，`WatchPagerLayout` 持有跟手分页和固定页码，`WorkoutRouteView` 持有地图惰性生命周期与增量轨迹绘制；计划编辑收敛在手机端与 MCP |
| 训练引擎 | `WorkoutService` | 状态机、计时、阶段推进、传感器融合、检查点、通知、震动、历史落盘 |
| 训练模型 | `Stage`、`WorkoutRecord` | 阶段和历史 JSON schema |
| 本地存储 | `PlanStore`、`PlanLibraryStore`、`HistoryStore` | 当前计划、多计划库、最多 200 条历史 |
| 传感器桥 | `SystemExerciseBridge`、`SystemSleepBridge`、`SystemGpsBridge` | 厂商 HealthKit 动态能力、系统睡眠只读转换与系统 GPS 控制 |
| 手表连接 | `WatchCommandRouter`、`WatchLinkService`、`WatchCloudBridgeEvent`、`WatchBridgeService` | BLE/LAN 共享业务路由、GATT Peripheral、LAN 加速与 mDNS；训练历史成功落盘后发送无业务数据的安全同步提示 |
| 手机伴侣 | `WatchConnectionManager`、`WatchCloudBridgeEvent`、`BleGattTransport`、`LanHttpTransport`、`phone/*` | 连接状态、传输选择、计划库、同步、历史详情、定位中继；严格解析手表同步提示并交给唯一后台任务 |
| 手机云同步 | `EncryptedWatchSync`、`EncryptedWatchSyncWorker`、`CloudSyncCredentials`、`WatchSyncKeyPackages` | `SyncEnvelopeV1` 加密双向 exchange、持久 outbox/cursor/conflict、Keystore 凭据、恢复包与后台恢复；训练完成事件、BLE/LAN 重连和 15 分钟周期共同触发同一唯一任务；`CloudSnapshotSync` 仅保留兼容入口 |
| MCP | `mcp/src/watch_mcp` | 独立 Watch MCP；只通过手机 8766 业务门面访问本项目 |

### 2.1 手表视觉层

手表 UI 继续使用 Java 动态 View，不引入第二套页面框架。`Ui` 是唯一公共视觉层：基于 378×496 画布缩放，提供 AMOLED 纯黑背景、运动语义色、表格数字、圆角面板、状态胶囊、运动员图标、指标网格、五区心率条、阶段环、页码与心率趋势。训练页的主要内容区使用剩余高度权重伸展到固定页码上方，不再用大块空 View 补齐画布。实时趋势只接收 `WorkoutService.Snapshot` 中实际有效的心率值；无样本时只显示文字空状态，禁止生成装饰性假曲线。主页保持三屏 pager，训练保持控制/综合仪表/训练数据/阶段/轨迹五屏，Activity 仍只消费服务快照并发送控制动作。

轨迹地图改用与 OWW221 系统运动同代的 Baidu Map SDK 7.5.9 原生矢量底图和项目既有本地暗色样式，不使用卫星影像或高德道路瓦片。`WorkoutRouteView` 把服务提供的原始 WGS84 点通过 SDK `CoordinateConverter(GPS)` 转为 BD-09LL，只转换观察层，落盘坐标不变。历史地图高度固定为系统资源值 164dp；相机取景使用横向 15dp、纵向 25dp 内容框，最大 zoom 19、单点 zoom 18，路线为 3dp 圆帽细线。

定位继续沿用经过既有真机行为验证的容错边界：首次位置可接受到 200m，连续跟踪点可接受到 150m，并同时受时间间隔、速度和跳点过滤约束。`legacy` 历史里的 accuracy 可能来自旧 schema 迁移默认值，不得在没有原始采集证据时把它解释为每个 GNSS fix 的实测水平；历史详情必须保留并绘制原始合法坐标。界面可以展示数据来源与不确定性，但禁止用底图吸附、手工闭环或无依据删点改变历史几何。

系统运动应用的旧路线不在 HealthKit `ExerciseSessionRecord` 中，而存于健康服务的 `sport_gps` 表，并经 `ISportAidlInterface2.queryGpsByte(sportId)` 返回压缩 protobuf。虽然 BinderProvider 外层 permission 标记为 normal，provider 内部仍校验调用包签名，第三方签名会得到 `signature not match`，因此本应用不能把该私有库当作稳定导入 API。系统运动和本应用的轨迹观察层现均使用 Baidu Map SDK 7.5.9 暗色矢量地图，但本应用只绘制自身保存的合法原始点，不读取或伪造系统私有路线。

跑者图形由 `Ui.WorkoutGlyph` 以本地几何绘制：粗实心躯干、圆帽加权四肢和前倾重心保证 34–36 dp 下仍可辨识。按压由 `Ui.pressable()` 统一提供 `1 → 0.94 → 1` 的 RenderThread 缩放、轻微透明度与触觉反馈；准备倒计时复用 `Ui.popIn()`，每一拍单独触觉提示。实现只参考 OWW221 系统运动应用的视觉和交互原则，不复制厂商 path、图片、字体或其他专有资产。

`WatchPagerLayout` 是主页和训练的唯一横向分页容器。它使用系统 paging touch slop、quintic ease-out 和按剩余距离计算的约 210–267 ms 吸附；固定页码由容器根据连续 `scrollX` 绘制，拖动时主动点同步位移并拉伸，不再由每页维护一组静态圆点。吸附过程中再次按下会接管仍有明显余量的运动，接近终点则先完成吸附，任何路径都必须回到整页。主页三张低频静态页可在空闲阶段预热当前页及相邻页的硬件层；训练五页包含每秒数据，禁止整页缓存，防止纹理持续失效反而增加合成成本。

训练刷新以分页运动为背压边界：拖动和吸附期间不取新快照、不写 TextView，`OnPageSettledListener` 停稳后补一帧；平时只更新当前可见页。`WorkoutService.snapshot(false)` 在轨迹页隐藏时不构造两组坐标数组，服务仍是所有训练状态和原始轨迹的唯一所有者。`WorkoutRouteView` 只有在轨迹页停稳且可见时才按需创建/恢复 Baidu `MapView`，离页立即暂停；同一 `Polyline`、起终点 `Marker` 和位图跨刷新复用，前缀不变时只转换新增坐标，镜头最多每 5 秒直接重算一次且不播放动画。历史详情先渲染指标，延迟 500 ms 激活地图，避免首屏与地图初始化争用主线程。

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
9. 云同步首次启动或根密钥变化后必须先完成 pull bootstrap，再允许本地 mutation push；不得以默认计划覆盖既有远端 revision。
10. 本地列表暂时缺项不代表删除；只有与计划库同次提交的显式 tombstone 可以生成 delete mutation。
11. 远端 change 解密/materialize、ACK 删除 outbox、冲突留存和 cursor 推进必须在同一份持久状态提交成功后才生效。
12. 设备 token、OAuth token 与根同步密钥三者用途隔离；云端、MCP、日志和迁移代码均不得获得未包装根密钥。
13. 手表的 `history_changed` 仅是已认证 BLE 上的版本化提示，不携带训练、位置、健康、设备身份或凭据；真实数据必须由手机重新读取 authenticated Watch API 后进入 canonical sync。
14. 云端可读 projection 只允许计划名与训练类型/起止/时长/距离/步数；Worker 在写入前精确校验字段，在每次 MCP 读取前再次校验 D1 行。

## 4. 数据和存储

| 数据 | 位置 | 当前 schema/上限 | 说明 |
| --- | --- | --- | --- |
| 当前计划 | SharedPreferences `plans` | 阶段 JSON 数组 | 含名称、分组、要求 |
| 多计划库 | SharedPreferences `plan_library_v2` | schema 3 | 手机为主库；新增显式 `deletedPlanIds` tombstone，缺项不再推断删除 |
| 活动会话 | `files/active_workouts/<id>/` | checkpoint v1 + NDJSON | 标量检查点原子替换；轨迹/心率追加写入；恢复时按已确认 offset 截断尾部 |
| 训练历史 | `files/workouts/<id>/` + `workout_index.json` | `WorkoutRecord` schema 3，200 条 | 摘要索引与每条记录样本文件分离；旧单文件自动迁移 |
| BLE 身份 | SharedPreferences `watch_identity` / `bridge` | 稳定设备 ID + 过渡六位码 | 当前仅为 debug 认证；正式密钥与挑战响应关联 `BUG-015` |
| 手机敏感凭据 | SharedPreferences 密文 + Android Keystore `poyi.watchintervals.phone.secrets.v1` | BLE pairing secret、LAN credential、短期 pairing code、Gateway API token | 旧 plaintext 字段首次读取时原子迁移；解密/迁移失败时不签发替代 token、不删除旧值 |
| Watch MCP 数据 | `%ProgramData%/Poyi/WatchMcp` | 已验证手机身份、DPAPI 密文、独立服务日志 | 不保存固定 IP、明文令牌或其他项目数据 |
| Watch Tunnel 凭据 | `%ProgramData%/Poyi/WatchMcp` | DPAPI LocalMachine + Watch 专属 Tunnel ID | Runtime Key 不写入仓库、命令行和日志 |
| 手机加密同步 | SharedPreferences `encrypted_watch_sync_v1` + Android Keystore | `/sync/v2/exchange`、`SyncEnvelopeV1`、严格 `c<base36>` cursor | endpoint/deviceId 为非密字段；device token、根密钥由不可导出 Keystore key 包装；state 持久保存 entity/outbox/conflict/flight/cursor |

任何 schema 变更都要：提升 schema 版本、保留向后读取、增加迁移/损坏数据测试、更新本表与 CHANGELOG。

Watch 与 Phone manifest 均设置 `allowBackup=false`，避免训练/轨迹、计划和凭据进入系统云备份；Phone 仍以 Auto Backup / device-transfer exclusion 逐项排除同步 state、旧云配置、BLE/LAN/Gateway 凭据、LAN outbox、运行时连接状态和幂等缓存，作为 OEM 行为差异下的防御纵深。Keystore 私钥本身不可导出；跨设备同步 root 只能走恢复包或设备批准包。两端 boot receiver 对外只接收系统保护的 `BOOT_COMPLETED`，自定义 watchdog 使用显式 PendingIntent 指向各自 `exported=false` receiver，第三方应用不能用公开广播反复拉起服务。

## 5. 距离与传感器策略

当前优先级为：厂商原生距离 -> 手表 GPS/手机中继坐标 -> 步数估距。

- 原生距离超过 10 秒没有新样本后降级。
- GPS 获取允许精度上限 200 m，跟踪允许 150 m；合理速度范围为 0.15 至 15 m/s。
- 连续定位间隔超过 60 秒时重建轨迹基线；GPS 75 秒无更新视为过期。
- 步长默认 0.72 m；单次 step delta 超过 50 不累计。
- 心率有效范围 25 至 240 bpm，15 秒无新样本视为过期。

这些阈值改变属于产品行为变化，必须关联需求/缺陷编号并进行户外真机对比。

### 5.1 速度与配速融合

距离来源决定「跑了多远」，`SpeedFusion` 决定「现在多快」，两者互相独立：

- 主源为 GNSS 多普勒速度 `Location.getSpeed()`。样本需在 3 秒内、`getSpeedAccuracyMetersPerSecond()` 优于 1.6 m/s（平台未提供精度时不做该项拦截），且不超过 12.5 m/s。
- 备源为 `WorkoutMetricsAccumulator` 的 10 秒距离窗口速度，沿用既有 `STALE_MILLIS` 过期规则。
- 输出按 4 秒时间常数做基于时间的指数平滑，因此不规则的定位频率不会改变平滑强度；低于 0.5 m/s 显示为静止而不是抖动的小数。
- 暂停、恢复和阶段切换调用 `resetWindow()` 时同步 `SpeedFusion.reset()`，避免休息后仍显示休息前的速度。

`SpeedFusion` 不引用任何 Android 类型，规则由 `app/src/test` 的纯 Java 测试覆盖。

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

### 手机端到端加密云同步

Phone 0.22.0 将 `CloudSnapshotSync` 收口为兼容调用入口，实际数据面由 `EncryptedWatchSync` 负责。唯一 canonical 路由为 HTTPS `POST /sync/v2/exchange`；请求包含 protocol 2 / envelope 1、产品 `watch`、设备身份、严格 base36 cursor 和最多 25 条加密 mutation。计划、保留分组与选择状态的 `sync:library` 元数据实体、以及 `/v1/history` 返回的训练摘要在设备上用 AES-256-GCM 加密；AAD 固定绑定产品、实体类型/ID、目标 revision、操作和 key version。原始轨迹、逐点心率、睡眠、第三方凭据和诊断正文不进入该数据面。

本地 `state` 在一次 SharedPreferences `commit()` 中保存实体、加密 outbox、flight lease、冲突副本、projection 待办和 cursor。网络前先提交 outbox；服务端 ACK 与 change 均验证 schema、UUID、revision、AAD hash、nonce 和 page 上限。change 成功解密后，materialize、ACK 移除、projection 待办和 cursor 推进一次提交；随后把计划 projection 幂等写入 schema 3 计划库，成功后才清除待办。崩溃或 projection 失败会在下一次同步开始前重放，不能静默吞掉。revision conflict 将本地加密 candidate 留在 outbox 的 `conflict` 状态，并保存远端候选，禁止自动覆盖。训练实体为 immutable；计划删除只读取计划库 schema 3 的显式 tombstone，绝不扫描“缺少的本地计划”来推断远端删除。

根同步密钥不会由网络或 token 派生。设备 token 和根密钥分别由 Android Keystore AES key 包装；GCM encryption 必须由启用 `randomizedEncryptionRequired` 的 Keystore provider 生成 IV，再持久化 `Cipher.getIV()`，禁止调用方预生成 IV。更换同一 deviceId 的旋转 token 保留根密钥，更换 deviceId 或确认 root fingerprint 变化时先把旧 state 隔离备份，再清空 active state。缺少根密钥时同步保持未就绪，不会静默生成新密钥。首台空白空间必须由用户显式初始化；已有空间通过 PBKDF2-HMAC-SHA256（310,000 次）恢复包，或由已授权设备针对目标 3072-bit Android Keystore RSA 公钥生成 10 分钟一次性混合加密批准包。批准包绑定 target deviceId、一次性 nonce、公钥 fingerprint 和有效期；相同 root fingerprint 的恢复保留现有 state，不同 root 才重新执行 pull-first bootstrap。

`EncryptedWatchSyncWorker` 使用网络约束、指数退避的一次性恢复任务和 15 分钟唯一周期任务，覆盖断网、Doze、进程回收与重启。训练成功落盘时，`WatchLinkService` 向当前已认证且订阅 indication 的手机发送严格两字段 `history_changed` 提示；提示本身不含业务数据。`WatchConnectionManager` 严格解析后只 enqueue `ExistingWorkPolicy.KEEP` 的同一个任务，重复提示不会形成并行上传；BLE/LAN 每次成功重连也 enqueue 一次，补偿断联期间漏掉的提示。未配置 token/根密钥时任务成功退出且不会形成无限重试。旧 `/sync/push` 和 plaintext snapshot 只保留历史记录；旧 `SYNC_KEY` 会被删除且不迁移为 V2 设备 token，旧路由的目标行为为 410。

Worker 的 encrypted entity/change 表仍是 canonical authority。为了让 OAuth MCP 在没有根密钥的前提下读取用户明确允许的数据，设备可在同一次 authenticated `/sync/v2/exchange` 附带最小 `readProjection`：计划仅有哈希键和名称；训练仅有哈希键、计划/自由类型、起止、活动时长、距离和步数。projection 按 deviceId 整体替换，上传前严格 exact-key/范围/重复键校验，读取时再次逐行验证并隐藏键。`watch_get_status`/`watch_get_sync_overview` 返回真实 encrypted sync 与 projection 新鲜度，`watch_list_plans`/`watch_list_workouts` 返回实际 projection，`watch_summarize_workouts` 是允许的粗粒度活动健康汇总。睡眠、路线、坐标、逐点心率、凭据和未知字段保持 local-only。

Watch Worker 另提供仅命名 service binding 可达的 authority observation entrypoint。请求必须精确使用 vendor `Accept`、`Authorization: Capability <产品独立 secret>` 和完整 HTTPS `/authority/watch` audience；公网同路径固定拒绝。D1 trigger 只在真实设备、同步 change/state/operation/audit、projection 或撤销状态变化时推进 authority checkpoint；每个 revision 的 exact-field observation 首次生成后持久化，后续读取保持 truth、`observedAt`、`expiresAt` 完全一致，中央签名 authority 因而得到稳定 observationHash。过期、损坏、额外字段、依赖或 revision 不可用时返回非 200，Watch Worker 不生成签名。

当前证据覆盖本地 JVM/Android/Worker 合同、Worker staging migration/deployment/current build attestation，以及真实 Phone 上 provider-generated IV、device token/root Keystore 包装。2026-07-30 只读审计确认 staging 服务依赖全绿，但真实 read projection 与 receipt 均为 0；恢复/批准、真实 Phone 上行、非空 MCP 回读、中央两跳和三轮 PC-off 尚未完成，因此本节不得被解释为生产可用或 `supportsPcOff=true`。

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

默认值 `TOKEN` 只允许编译不展示地图的开发构建；运行时会明确显示“地图授权待配置”，不得静默退回另一家底图。Android AK 必须绑定包名 `com.poyi.watchintervals` 和实际签名 SHA-1。

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

## 10. 独立 Watch MCP、云端 MCP 与 ChatGPT 通道

- 本机控制链路为 `ChatGPT -> Watch 专属 Tunnel -> PoyiWatchMcp -> 手机 8766 -> BLE/LAN -> 手表`；云端只读链路为 `手机 -> Watch Cloud MCP -> ChatGPT`，电脑不在数据路径上。
- `PoyiWatchMcp` 只监听 `127.0.0.1:8768`，同端口提供 `/mcp`、`/healthz`、`/readyz`、`/metrics` 及 OAuth Protected Resource 元数据；避开 PersonalMcpGateway 的 8760/8761，且不加载其模块。
- MCP 只发现手机 `_watchintervals-phone._tcp.local.`，以 Bearer Token 认证并固定首次验证的 `phoneDeviceId`。它不直接连接手表、不读取 Android 数据库、不使用 ADB 或固定 IP。
- `PoyiWatchTunnel` 使用独立 Tunnel ID、独立 Runtime Key 和 `127.0.0.1:8880` 健康端口，仅连接 Watch MCP。两个 WinSW 服务均自动启动和失败重启。
- 手机 API Token 与 Tunnel Runtime Key 分别用 DPAPI LocalMachine 和不同 entropy 保存，日志过滤令牌、精确位置和正文。Windows 服务按默认 LocalSystem 运行，安装脚本显式授予 `SYSTEM` 数据目录权限；升级旧虚拟服务账户安装时会强制切回 LocalSystem，避免 DPAPI 文件和日志目录 ACL 不一致。
- 手机 mDNS 地址进入候选列表前必须规范化：IPv6 authority 使用方括号、IPv4 优先尝试、坏缓存跳过；只有 `/v1/status` 成功且稳定 `phoneDeviceId` 匹配后才更新运行时端点。
- 本机 `watch_*` 工具只返回摘要；轨迹、心率和完整睡眠使用 `watch://` Resource 分页读取。云端 MCP 只提供 OAuth `watch:read` 的同步状态、实际计划/粗粒度训练与活动健康汇总；睡眠工具明确返回 `local_only`。
- 旧 `personal_gateway.py`、Quick Tunnel、固定 IP/六位码配置和直接连接手表脚本已删除；有效工具、Schema、错误映射和发现逻辑已迁入独立包。

## 11. BLE 连接架构与边界

- 角色固定为手机 Central/GATT Client、OWW221 Peripheral/GATT Server；旧 `BleProbeService` 已删除，正式服务均 `exported=false`。
- GATT 服务包含设备信息、配对、控制、事件、同步收发、定位、LAN endpoint 和心跳特征；手机顺序订阅 indication 后认证。
- 消息使用 16 字节帧头，兼容默认 MTU 23；单帧同时受 `MTU-3` 与 512 字节属性值上限约束，消息上限 256 KB，不完整帧 30 秒清理。
- `WatchConnectionManager` 负责 BLE 优先、LAN 加速、状态快照和退避。控制、计划、同步与定位优先 BLE；历史/睡眠等批量读取优先已验证 LAN，失败可回退 BLE。
- 训练成功写入 `HistoryStore` 后只发送安全 `history_changed` indication；手机收到后通过 WorkManager 读取完整 authenticated `/v1/history`，事件通道本身绝不承载训练摘要。事件重复由唯一工作去重，断联漏事件由成功重连与周期任务补偿。
- 2026-07-26 真机已验证 OWW221 广播、Xiaomi 连接、MTU 517、四个 CCCD、AUTH、计划 outbox、计划回读和定位请求。
- 安全版使用 P-256 ECDH、一次性验证码 HMAC 确认、长期配对密钥、challenge-response、AES-GCM、随机 sequence 和持久认证 challenge 防重放；真机门禁通过前仍关联 `BUG-015`。无 Wi-Fi、后台、重启、5 分钟息屏、10 次重连、100 次请求和 15 分钟功耗关联 `BUG-016`。
