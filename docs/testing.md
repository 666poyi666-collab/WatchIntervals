# 测试与发布门禁

状态：维护中  
基线：2026-07-25

## 1. 测试原则

本项目的高风险区域不是页面能否打开，而是长时间状态、传感器切换、后台运行和跨设备同步。测试分为纯逻辑、Android 集成、模拟器界面、手表真机、手机真机和 MCP 契约六层。仓库已包含指标纯 Java 测试、MCP 契约测试和 GitHub Actions 基线；状态边界、文件中断注入和真机自动化仍需继续扩充。

当前恢复单元测试覆盖 checkpoint offset 后存在额外完整行、损坏半行以及 offset 落在行中间的情况；恢复会回退到上一个完整换行并截断未提交尾部。

## 2. 每次提交最小检查

```powershell
.\gradlew.bat test lint :app:assembleDebug :phone:assembleDebug
python -m unittest discover -s mcp\tests -v
git diff --check
```

- 修改阶段/计划：验证 JSON 新旧数据读取、空计划回退、目标边界。
- 修改训练引擎：验证开始、暂停、继续、停止、自动阶段推进、完成只保存一次。
- 修改传感器：验证 GPS、步数、心率各自单独可用和切换恢复。
- 修改 API/MCP：验证 401、错误 JSON、超时、手机不在线、手表不在线和正常路径。
- 修改 Tunnel：执行 `powershell -File mcp/tests/test_persistent_tunnel.ps1`，再执行 API-011 真机/重启验证。
- 修改 UI：至少检查 378×496 截图和点击区域。

## 3. 手表真机回归

| ID | 场景 | 操作 | 预期 |
| --- | --- | --- | --- |
| WT-001 | 首次启动权限 | 清数据后启动并逐项授权 | 权限解释和按钮可见；拒绝后可再次处理 |
| WT-002 | 默认计划 | 首次进入计划页 | 显示 1 km 跑 + 200 m 走 |
| WT-003 | 时间计划 | 创建 15 秒跑 + 15 秒休息 | 按时间自动切换并震动 |
| WT-004 | 距离计划无 GPS | 室内关闭/遮挡 GPS 后开始并行走 | 开始不阻塞，显示数据来源，实际步数增加 |
| WT-005 | 户外 GPS | 开阔处等待定位后移动至少 10 m | 轨迹连续，异常跳点不计入，距离合理 |
| WT-006 | 暂停 | 训练中暂停并移动 | 活动时间、阶段进度、距离和步数不增加 |
| WT-007 | 息屏 | 训练中息屏 3 分钟再唤醒 | 服务仍运行，时间和有效传感器数据连续 |
| WT-008 | 进程恢复 | 训练中结束 Activity/重启任务 | 恢复同一阶段、时间、距离、轨迹和状态 |
| WT-009 | 完成和历史 | 完成全部阶段 | 仅新增一条历史，统计/阶段结果完整 |
| WT-010 | 停止确认 | 中途停止 | 确认交互正确，记录保存策略一致 |
| WT-011 | 页面手势 | 首页/计划/历史及训练数据/轨迹双向滑动 | 短滑不误切，快速回滑不滞留 |
| WT-012 | 小屏边界 | 遍历全部页面和长名称计划 | 文字、按钮、底部内容不裁切或重叠 |
| WT-013 | 原生运动降级 | 在当前 OWW221 固件启动 | 能力为空时显示未开放并继续 GPS/步数 |
| WT-014 | 系统睡眠授权 | 首次启动确认“读取睡眠数据” | 系统健康权限页可见，允许后 Store API 不再返回 `Missing permissions` |
| WT-015 | 系统睡眠回读 | 请求最近 14 天 | 返回真实记录、session、stage 时间线；duration 按分钟解释，时间戳按毫秒输出 |
| WT-016 | 计划完成后自由记录 | 15 秒计划达标后继续运动 2 分钟 | 计划提示完成，但计时、距离、轨迹、心率和步数继续；手动结束只保存一次 |
| WT-017 | 长时间追加轨迹 | 注入 7200/14400 个混合来源点并中断恢复 | 无 OOM；检查点大小有界；损坏尾行可忽略；原始点和统计一致 |
| WT-018 | 来源切换 | 步数、手表 GPS、手机 GPS、系统距离依次切换 | 不重复累计；速度窗口重置；来源距离守恒 |
| WT-019 | 四页训练 UI | 378×496 遍历核心、计划、轨迹、控制页 | 文字和安全区正常；默认核心页；控制入口可达 |

### 0.18.0 OWW221 短测证据（2026-07-25）

- 从 `0.17.0`（27）使用网络 ADB `install -r` 升级到 `0.18.0`（28），首次安装时间和私有数据保留。
- 旧 schema 2 历史 3 条全部迁移为独立目录；下一次启动删除迁移备份，索引仍为 3。
- 单阶段 15 秒计划进入 `RUNNING + COMPLETED` 后继续记录 255 秒以上；暂停 5 秒活动时间不变，继续后恢复增长。
- 相同 resume/stop commandId 重试返回 duplicate，状态不反转；手动结束历史由 3 增至 4，未重复保存。
- 活动会话覆盖安装后通过首页“继续”恢复，`activeDurationMs` 从 checkpoint 继续增加，计划状态保持 COMPLETED。
- 378×496 截图检查首页和训练核心/控制/计划/轨迹四页，无文字、按钮或底部安全区裁切。
- 本次为室内未佩戴测试，无移动轨迹和心率样本；不替代 WT-005、WT-017、WT-018 和户外长测。
- GitHub Actions run `30164226710` 已通过 MCP 测试、Android JVM 测试、lint、双 APK 构建和产物上传。

### 0.21.0 手机视觉模拟器预检（2026-07-26）

- Pixel 6 / API 35（1080×2400）安装 Phone 0.21.0 debug，检查连接设置展开/收起、计划列表、计划编辑器与训练页；固定底栏未遮挡当前首屏操作，页面可继续纵向滚动。
- 计划页顶层巨型卡片已移除，计划组选中态、主/次/危险操作层级可辨；编辑器返回入口与标题不再重叠。
- 断连训练页显示“无法读取手表状态”与“打开连接设置”，不再显示可用态“开始训练”（`BUG-030`）。
- 本轮不替代小米真机字体、系统栏、真实计划长文案和已配对/实时训练数据截图；用户使用手机期间不抢占前台。

## 4. 手机真机回归

| ID | 场景 | 预期 |
| --- | --- | --- |
| PT-001 | mDNS 自动发现 | 同一 Wi-Fi 下解析到手表并填入连接信息 |
| PT-002 | 错误配对码 | 显示连接失败，不覆盖有效数据 |
| PT-003 | 计划 CRUD | 新建、命名、分组、编辑、删除、重开后均一致 |
| PT-004 | 计划同步 | 选择手机计划后手表当前计划一致 |
| PT-005 | 历史详情 | 距离、步数、心率、阶段和轨迹完整显示 |
| PT-006 | 定位中继 | 授权后前台服务运行，手表接收并过滤位置 |
| PT-007 | 重启恢复 | 手机重启后计划桥服务恢复，计划库不丢失 |
| PT-008 | 睡眠页 | 授权后显示时长、评分、血氧、深睡、REM 和阶段数量；未授权时有明确提示 |
| PT-009 | 手机服务发现 | 手机 IP 改变后 Watch MCP 通过 `_watchintervals-phone._tcp.` 找到相同 phoneDeviceId |
| PT-010 | 计划 outbox | 手表离线修改计划，恢复 LAN 后自动 ACK | 操作不丢失、不重复，pending 归零 |
| PT-011 | BLE 首次连接 | 手机授权附近设备后自动扫描、连接、发现服务、协商 MTU、订阅并认证 |
| PT-012 | BLE 计划同步 | 无 LAN 时计划 outbox 经 BLE ACK，手表 profile 回读一致 |
| PT-013 | BLE 定位中继 | 训练中每 2–5 秒发送带 sequence/TTL 的手机定位，断联不补发旧点 |
| PT-014 | BLE 控制 | start/pause/resume/stop 经 BLE；重复 commandId 返回首次结果且不反转状态 |

## 5. MCP/API 回归

| ID | 验证 |
| --- | --- |
| API-001 | 无配对码/错误配对码返回 401 |
| API-002 | `watch_status` 返回版本、会话和后台定位字段 |
| API-003 | 计划 profile 往返后名称、分组、要求、阶段不变 |
| API-004 | 创建/更新/删除/选择计划后手机与手表一致 |
| API-005 | 历史列表、详情、汇总对同一记录计算一致 |
| API-006 | start/pause/resume/stop 对目标状态幂等，修复 `BUG-002` 后启用 |
| API-007 | 超限/损坏 JSON 返回明确 4xx，不导致服务退出 |
| API-008 | 手机或手表离线时 MCP 返回可诊断错误且不改本地数据 |
| API-009 | `list_sleep_records` 保留系统来源、全部 session 和 stage 原始类型/时间线 |
| API-010 | `get_latest_sleep` 对无记录返回空；`summarize_sleep` 只对有效值求平均且单位为分钟 |
| API-011 | 长效 Tunnel 首次绑定后在线；结束 tunnel-client、重新登录和重启电脑后均自动恢复，ChatGPT 连接配置不变 |
| API-012 | 历史列表不含完整样本；详情返回预览；route/heart 游标可无重无漏读完整数据 |
| API-013 | `PoyiWatchMcp` 与 `PoyiWatchTunnel` 分别终止后自动恢复；设备离线时 MCP 仍 ready 并返回分层错误；不影响其他项目服务 |
| API-014 | 重复 operationId 返回 already_applied；旧 revision 返回 conflict；ACK 丢失重试不重复应用 |
| API-015 | 手机 API v2 相同 requestId 返回首次结果；不同正文复用 ID 和旧 revision 返回 409；在计划库提交后终止进程，重试只恢复结果而不重复修改 |
| API-016 | Watch MCP 的 24 个工具均以 `watch_` 命名；轨迹、心率和完整睡眠只通过 Resource 分页返回 |
| API-017 | `/healthz`、`/readyz`、`/metrics` 与 `/mcp` 仅监听 `127.0.0.1:8768`；手机离线只使业务调用降级 |
| API-018 | 手机 API Bearer Token 错误返回 401；mDNS 发现身份不匹配时拒绝固定新端点；日志不包含令牌、IP 或正文 |

## 5.1 BLE 集成门禁

| ID | 场景 | 验收 |
| --- | --- | --- |
| BLE-001 | 无共同 Wi-Fi、关闭无线 ADB | 60 秒内自动连接，不输入 IP，状态/计划/控制/定位可用 |
| BLE-002 | 手机与手表各息屏 5 分钟 | 连接保持或可自动恢复，无需打开开发者设置 |
| BLE-003 | 手机进程回收、手表 Activity 关闭 | 前台连接服务恢复，训练服务不受影响 |
| BLE-004 | 手机、手表、双端重启 | 无需重新输入验证码，自动恢复已配对身份 |
| BLE-005 | 双端蓝牙分别关闭再开启 | 退避后自动连接，pending 不丢失、不重复 |
| BLE-006 | 10 次连接/断开 | 不永久卡在 CONNECTING，不需清数据 |
| BLE-007 | 100 次加密 status 请求 | 全部返回相同 deviceId，无 OOM、超时或永久断联 |
| BLE-008 | 连续运行 15 分钟 | 无永久断联，记录断联与恢复次数 |
| BLE-009 | 计划、轨迹和心率分页中断续传 | cursor 续传无重复、无漏页、无 OOM |
| BLE-010 | 15 分钟功耗 | 记录真实训练＋BLE 定位中继期间的双端开始/结束电量、断联和重连次数 |

### 0.19.0 BLE 基础真机证据（2026-07-26）

- OWW221 作为 Peripheral/GATT Server 低功耗广播；Xiaomi xaga 作为 Central 扫描连接成功。
- 双端协商 MTU 517，手机依次完成 EVENTS、SYNC_RX、PAIRING、HEARTBEAT 四个 CCCD indication 订阅，并完成过渡 AUTH。
- 真机发现并修复三项 Xiaomi 栈兼容问题：认证后 GATT 操作竞态、Android 13 原子写 API、MTU 517 时属性值不得超过 512 字节。
- 手表日志确认 `POST /v1/sync/operations`、`GET /v1/plan/profile`、`POST /v1/location` 均经 GATT 返回 200；手机 UI 显示“蓝牙连接 · LAN 加速”。
- P-256 ECDH 首次公钥交换与 AES-GCM 长期密钥下发成功；覆盖安装后直接通过挑战响应恢复安全会话，未再次交换配对码。
- 仪器测试完成 10 次断开/重连和 100 次加密 status 请求；手表日志记录 10 次 `secure_session_ready`、102 次 status 200，并拒绝 1 次精确旧密文重放，后续新请求仍成功。
- Xiaomi 在连续扫描第四轮触发系统 scan throttling；改为首次扫描后缓存已验证 `BluetoothDevice` 并直接重连，完整 10 次循环通过。
- 真实训练通过 BLE 启动；同一 commandId 的 pause/resume 各重复一次均返回 duplicate 且状态不反转，手动结束后历史只新增 1 条。
- 两端关闭 Wi-Fi、无线 ADB 离线且息屏时，15 分钟训练持续完成 94 次加密请求和 4 轮重复暂停/继续；落盘活动时间 951,996 ms、暂停 8,343 ms，最终正常停止。
- BLE-001、BLE-002、BLE-006、BLE-007、BLE-008 和 PT-014 通过。手表通过 USB 取证并持续充电，电量 72%→81%，因此 BLE-010 功耗结论无效；BLE-003 至 005、BLE-009/010 继续开放。

### 0.20.0 BLE 恢复矩阵补测（2026-07-26 晚）

- BLE-005 手机半场通过：`cmd bluetooth_manager disable` 关闭小米蓝牙后手表 `dumpsys bluetooth_manager` 转 `STATE_DISCONNECTED`；重新 enable 后 12 秒内恢复 `STATE_CONNECTED`，全程无重新配对。注意该指标含系统级配对链路，应用会话证据以日志与业务请求为准。
- BLE-005 手表半场未执行：OWW221 构建不实现 `cmd bluetooth_manager`/`svc bluetooth` shell 命令，蓝牙设置页无开关（在快捷面板），为避免把日常佩戴设备置于蓝牙关闭态，留待手动测试。
- BLE-003 手表半场通过：关闭手表 Activity（回表盘）后 8765 `/v1/health` 仍返回 401 门禁存活。
- BLE-003 手机半场记录到设计内的两段式恢复：`am force-stop` 后进程死亡；shell 发送 `WATCHDOG` 广播可拉起进程，但 RCVR 态 `startForegroundService` 被系统拒绝（W 级日志自捕获，符合 PhoneBootReceiver 注释预期），服务启动推迟到 `setExactAndAllowWhileIdle` 闹钟（携临时白名单豁免）；`dumpsys alarm` 确认 u0a325 闹钟挂起。闹钟投递后的完整恢复证据不完整：21:40 投递窗口的 logcat 已被系统噪声轮转覆盖，保留的最早痕迹是 21:53 安装 0.21.0 时系统强停了一个正在运行的 `PhonePlanBridgeService`（21:28 基线时 8766 已死、该服务两次 FGS 启动均被拒），但 8766 宿主 `PhoneCompanionService` 直到重装都未再出现——闹钟可能只拉起了部分服务，也可能 ServiceRecord 是残留。实验窗口被重装破坏，结论按未证处理，需重跑：force-stop 后 15 分钟内不触碰设备、立即抓取 logcat。另注意：install -r 会随 force-stop 取消看门狗闹钟，重装后的冷状态需要一次应用启动才能重新武装整条自愈链。

### 0.19.0 小米手机 API 与独立 MCP 补测（2026-07-26）

- 小米 `xaga` 覆盖安装手机 debug APK 成功，`PhonePlanBridgeService`、`PhoneCompanionService`、`PhoneLocationRelayService` 均以前台服务运行。
- 手机 8766：无 token 为 401；已配对凭据签发独立 Bearer Token 成功；重复签发返回 duplicate；旧 revision 返回 409；过期控制命令返回 `command_expired`。
- `PoyiWatchMcp` 开发模式监听 `127.0.0.1:8768`，`/healthz`、`/readyz`、`/metrics` 通过；MCP initialize、24 个 `watch_*` 工具、4 个静态 Resource、4 个模板 Resource、`watch_get_status` 和 `watch://status` 均通过真实调用。
- 当前状态显示手机 API healthy、手表 online、训练会话 `RUNNING + COMPLETED`；BLE 当前快照曾出现 `DISCONNECTED/gatt_147` 后由 LAN 保持在线，需继续做断网/关闭无线 ADB后的 BLE-only 回归。

### 0.19.0 WinSW 服务与本机 MCP 端到端补测（2026-07-26）

- `PoyiWatchMcp` / `PoyiWatchTunnel` 已安装为自动启动服务；`PoyiWatchMcp` 以 LocalSystem 运行并通过 `127.0.0.1:8768/healthz`、`/readyz`。
- MCP 服务端口真实调用通过：`watch_get_status`、`watch_list_plans`、`watch_get_latest_sleep`、`watch://status`。
- 安全写验收通过：`watch_sync_plans` 同一 `requestId` 重放返回 duplicate；`watch_pause_workout` 同一 `commandId` 重放返回 duplicate；随后恢复训练，最终 `sessionState=RUNNING`、`planState=COMPLETED`。
- Watch 专属固定 Tunnel 与独立 Runtime Key 已 provision；Runtime Key 仅以 DPAPI LocalMachine 密文落盘。`PoyiWatchTunnel` 以 LocalSystem 自动服务运行，`127.0.0.1:8880/readyz` 为 `ready`，`doctor.ps1` 与 `verify.ps1` 通过。
- 现有“步序运动”旧私人连接没有 MCP 端点编辑入口，删除旧对象后以相同名称重新绑定 Watch Tunnel；没有同时存在第二个同名应用。连接页扫描到 24 个 `watch_*` 工具。
- ChatGPT 真实读取：`watch_get_status`、`watch_list_plans`、`watch_get_latest_sleep` 成功；`watch://status` 返回 `Unknown resource`，本地相同 Resource 仍成功，记录为 BUG-019。
- ChatGPT 真实写入：`watch_sync_plans` 首次成功，同一 `requestId` 重放返回 `duplicate=true`；pause 成功，以不同 requestId 重放同一 `commandId` 返回 `duplicate=true`。训练最终经状态回读为 `RUNNING + COMPLETED`。
- 现场修复 mDNS IPv6 缺少方括号及不可达 IPv6 阻塞 IPv4的问题；新增 2 项测试后 MCP pytest 为 12 项通过。手机前台 API 被系统结束后的首次远程调用按离线失败，重新启动应用后读取恢复；无 ADB 的开机/后台恢复仍需单独重启验收。

### 0.20.0 后台链路恢复与配速融合证据（2026-07-26）

- Android 与 MCP 自动化：`:app:testDebugUnitTest`、`:phone:testDebugUnitTest` 通过；`mcp` pytest 12 项通过；两个模块 `assembleDebug` 通过。
- `SpeedFusion` 新增 6 项纯 Java 用例：GNSS 优先、过期回退到距离窗口、精度与异常值拦截、抖动阻尼与收敛、静止判定与全源过期、配速换算与 `m'ss"` 格式化。
- 后台 LAN 发现：删除手机 `connection.xml` 并销毁 `MainActivity` 后，后台 `WatchLanLocator` 重新写回 `host` 与 `watch_device_id`；此时 MCP `watch_get_status` 为 `CONNECTED_BLE_LAN`、`lanAvailable=true`、`connection.state=healthy`。
- 控制链路：`watch_stop_workout` 首次 `accepted=true`（controlRevision 5→6）；对已停止会话重复 `expected_state=running` 返回 `STATE_MISMATCH`；相同 `requestId`/`commandId` 重放返回 `duplicateRequest=true`。
- 进程恢复：`am crash` 后进程消失、`/v1/health` 不可达；`dumpsys deviceidle tempwhitelist`（等价于精确闹钟投递时的临时白名单）后触发 `WATCHDOG` 广播，进程重建、`/v1/health` 恢复 401、看门狗重新挂起。
- 监听器硬化：绑定失败路径现有明确日志与退避重试，`logcat -s PhonePlanBridge` 可见 `API listening on 8766`。
- 未覆盖：开阔户外 GNSS 多普勒配速对比、非充电长时间功耗、MIUI 自启动关闭时的恢复行为。

## 6. 建议优先补齐的自动测试

1. `Stage`/`PlanStore` 编解码、默认计划和法特莱克识别。
2. `WorkoutRecord` schema 1/2 兼容和统计计算。
3. 距离来源切换、GPS 过滤、step counter 基线和阶段跨越算法。
4. 暂停/恢复/完成状态机和“历史只保存一次”。
5. `PlanLibraryStore`/`PhonePlanLibrary` 迁移、revision、选择与删除。
6. 手表 8765 与手机 8766 的协议契约测试。
7. 手机 `MutationGuardTest` 覆盖重复、ID 复用、旧 revision 和旧客户端兼容；API-015 的进程终止场景仍需仪器/真机测试。

## 7. 发布门禁

发布 APK 前必须全部满足：

- 两个模块从干净构建成功，无新增编译警告。
- P0/P1 开放缺陷为 0；例外必须在 Release notes 明示并由维护者接受。
- 与改动相关的真机和 API 用例通过，结果记录到 `project-log.md`。
- `versionCode`、`versionName` 与 CHANGELOG 一致。
- APK 使用预期签名；debug 包标记为 prerelease。
- 计算并记录两个 APK 的 SHA-256。
- `git status` 干净，Release 指向已推送提交。
