# 缺陷与技术债台账

状态：维护中  
基线：2026-07-26

严重度：P0 数据损坏/训练核心不可用；P1 核心行为错误或高风险；P2 有降级路径；P3 体验或维护问题。状态使用 `Open`、`In Progress`、`Fixed`、`Verified`、`Won't Fix`。

## 1. 开放项

### BUG-001：关键路径自动化测试仍不完整

- 状态：Verified
- 严重度：P1
- 影响：所有当前版本
- 现象：已有指标纯 Java 测试、MCP 契约测试和 CI，但训练状态边界、文件中断恢复、schema 迁移和 UI 仍主要依赖人工回归。
- 风险：传感器切换、暂停、恢复和历史 schema 修改容易产生回归。
- 处理：继续按 `testing.md` 第 6 节补齐纯 Java、Robolectric/仪器和 API 契约测试。
- 关闭条件：核心状态机、编解码和协议在 CI 中自动执行。

### BUG-002：pause/resume API 实际采用 toggle，调用不幂等

- 状态：Fixed，待真机验证
- 严重度：P1
- 影响：手表 0.16.0
- 复现：连续调用两次 `/v1/control/pause`；第二次会继续训练。对已暂停训练调用 `resume` 之外的重复请求也可能反转状态。
- 根因：`WatchBridgeService.control()` 将 `pause`、`resume`、`toggle` 全部映射到 `ACTION_TOGGLE`。
- 处理：增加显式 `ACTION_PAUSE` / `ACTION_RESUME`，服务按当前状态幂等处理；错误 action 返回 422。
- 验证：启用 API-006，并覆盖重复、乱序和网络重试。

### BUG-003：局域网 API 使用明文 HTTP 和长期六位配对码

- 状态：Open
- 严重度：P2
- 影响：手表 0.16.0、手机 0.9.0
- 现象：服务监听 8765/8766，使用持久六位码和明文 HTTP；同网观察者可能重放请求。
- 当前约束：只在受信局域网使用，不暴露端口到公网，不在日志/截图中记录配对码。
- 处理候选：会话 token、码轮换、请求 nonce/HMAC、仅绑定合适接口、速率限制。
- 关闭条件：完成威胁模型和协议升级，并保留旧客户端迁移说明。

### BUG-004：仓库缺少 Gradle Wrapper

- 状态：Fixed
- 严重度：P2
- 影响：新开发环境、CI
- 现象：README 使用 `gradle`，但仓库没有 `gradlew` 和 `gradle/wrapper`；依赖本机安装或缓存的 8.14.3。
- 处理：使用 8.14.3 生成 wrapper，提交 wrapper 配置和校验后的脚本。
- 验证：已生成 8.14.3 Wrapper；本地通过 `gradlew.bat` 执行测试与双模块编译，CI 使用相同入口。

### BUG-005：关键路径存在大量吞异常，诊断证据不足

- 状态：Open
- 严重度：P2
- 影响：连接、存储、传感器和厂商桥接
- 现象：多处 `catch (Exception ignored)`；失败时界面可能只表现为无数据。
- 处理：定义统一日志 tag/event，记录操作和错误类型；配对码、API Key、经纬度不得入日志。
- 关闭条件：关键 API、历史写入、mDNS、传感器注册和会话恢复都有可定位日志。

### BUG-006：当前仅发布 debug APK

- 状态：Open
- 严重度：P2
- 影响：`v0.16.0`
- 现象：Release 附件使用 debug 签名，不具备稳定正式升级链。
- 处理：定义 keystore 保管、release 构建、签名校验和回滚流程。
- 关闭条件：发布可升级的 release APK，并记录证书指纹和离线备份位置（不提交密钥）。

### BUG-007：厂商 HealthKit 在当前固件返回空运动能力

- 状态：Open（外部依赖）
- 严重度：P2
- 影响：OWW221 固件 `4.1.3_a09f60c_260616`
- 现象：服务和 API 可连接，但 `OUTDOOR_RUN` 能力映射为空。
- 当前行为：显示“系统 未开放”，降级到 GPS/步数，不阻塞训练。
- 复查条件：固件升级或厂商服务版本变化后重新执行三段能力检测。
- 参考：`system-exercise-implementation.md`。

### BUG-008：测试截图和 UI XML 证据散落在仓库根目录外的本地工作区

- 状态：Open
- 严重度：P3
- 影响：审计和长期回归
- 现象：存在大量按版本命名的截图/XML，但已由 `.gitignore` 排除，没有用例、设备和结果元数据。
- 处理：只保留关键基线截图到受控 `docs/test-evidence/<version>/`，附 manifest；临时抓取继续忽略。
- 关闭条件：每次发布至少有关键页面和真机训练证据索引。

### BUG-009：ChatGPT Quick Tunnel 地址在重启后变化

- 状态：In Progress
- 严重度：P1
- 影响：MCP 0.5.1 及此前通过 `trycloudflare.com` 连接的 ChatGPT 插件。
- 现象：Cloudflare Quick Tunnel 每次启动生成不同 URL，旧插件连接随进程或电脑重启失效。
- 根因：Quick Tunnel 只适合临时调试，不提供稳定连接标识。
- 修复：改用 OpenAI Secure MCP Tunnel 固定 Tunnel ID；Runtime Key 使用 Windows DPAPI CurrentUser 加密，计划任务在登录后启动守护脚本，客户端退出后 5 秒重连。
- 关闭条件：完成一次 Tunnel 绑定，重启电脑后 `check_persistent_chatgpt_tunnel.ps1` 显示 `Online=True`，ChatGPT 无需修改连接即可调用 `watch_status` 和 `summarize_sleep`。

### BUG-010：最后阶段达标后训练提前终止

- 状态：Fixed，待 OWW221 户外验证
- 严重度：P1
- 影响：手表 0.17.0 及以前
- 现象：最后阶段达标后立即停止 GPS、传感器和前台服务，用户继续运动的数据不再记录。
- 修复：分离 SessionState 与 PlanState；计划完成后进入自由记录，只有手动结束才保存并停止。
- 验证：新增短时间计划回归；仍需完成 30–60 分钟户外、暂停和进程恢复测试。

### BUG-011：超过 600 个轨迹点后早期路线持续丢失

- 状态：Fixed，待压力与户外验证
- 严重度：P1
- 影响：手表 0.17.0 及以前
- 根因：内存数组达到 600 后持续删除第二个点，检查点和历史又整段重写该数组。
- 修复：原始轨迹/心率改为每训练独立 NDJSON 追加文件；检查点仅保存标量和文件偏移；地图使用最多 600 点简化预览。
- 验证：新增存储结构和单元测试基线；仍需注入 7200/14400 点并执行真机长时压力测试。

### BUG-012：检查点 offset 后的追加样本未重放到统计

- 状态：Fixed，待进程中断真机验证
- 严重度：P1
- 影响：手表 0.18.0-debug 候选
- 现象：检查点保存 route/heart offset，但恢复只读取预览轨迹和检查点累计值，没有重放确认 offset 之后的完整 NDJSON 行。
- 风险：进程在两次检查点之间终止时，轨迹文件可能含有额外完整点，但总距离、来源统计或心率汇总停留在旧检查点，产生事实不一致。
- 处理：采用统一提交边界语义。checkpoint 中的 route/heart offset 是权威边界；服务恢复任何累计值前，先将样本文件截断至 offset，并在 offset 非法或落入半行时回退到上一个完整换行。offset 后尚未进入 checkpoint 统计的完整行和损坏尾行一并丢弃，避免无法从路线行重建的系统运动/步数距离被错误重放。
- 验证：新增 `WorkoutFileStoreTest` 两项 JVM 测试，覆盖额外完整行、损坏半行和行中 offset；仍需用普通进程 kill/crash 验证真实文件和统计一致。
- 关闭条件：真机进程中断后，原始样本、总距离、来源汇总和恢复前已确认状态一致。

### BUG-013：历史样本分页仍整文件解析

- 状态：Open
- 严重度：P2
- 影响：手表 0.18.0-debug 候选
- 现象：route/heart 接口使用整数样本 cursor，但每次请求先把完整 NDJSON 读入内存，再截取当前页。
- 风险：长训练的响应体虽然分页，服务端内存和解析耗时仍随完整文件增长。
- 处理：按文件 byte offset 或持久样本索引流式跳转；历史记录归档后不可变，cursor 需绑定 recordId 和稳定 offset。
- 关闭条件：请求后续页时内存与耗时只和页大小近似相关，分页无重复、无遗漏。

### BUG-014：计划 outbox 尚未形成完整可靠同步协议

- 状态：Open
- 严重度：P2
- 影响：手机 0.11.0-debug 候选
- 已实现：持久 operationId、完整库 revision、ACK 清理、手表最近 500 个 operationId 去重、旧 revision 冲突、重启后保留 pending、MCP 手表回读验证。
- 未实现：per-plan 操作与 revision、真实 tombstone、后台退避调度、死信状态、乱序操作合并规则，以及普通手机 UI 同步后的手表内容回读。
- 当前约束：完整库快照入队时压缩旧快照；离线时只能报告 pending，不得描述为可靠同步已经完成。
- 关闭条件：API-014 和重启/离线/ACK 丢失/乱序矩阵通过，手机与手表最终状态经回读一致。

### BUG-015：BLE 认证尚未达到正式安全配对要求

- 状态：In Progress
- 严重度：P1
- 影响：手表/手机 0.19.0-debug 候选
- 处理：首次配对使用 P-256 ECDH、公钥与随机数交换、六位码派生确认和 AES-GCM 下发长期密钥；重连使用双向 HMAC 挑战，业务消息使用会话密钥、严格序号、时间窗和 AES-GCM。
- 证据：OWW221/Xiaomi 首次配对和持久密钥重连成功；10 次重连全部建立安全会话；精确重放旧密文被拒绝 1 次，之后新请求继续成功。
- 遗留：解除配对 UX 和 CompanionDeviceManager 关联作为后续增强，不影响当前应用层认证与防重放结论。

### BUG-016：BLE 后台与长时间门禁未完成

- 状态：Open
- 严重度：P1
- 影响：手表/手机 0.19.0-debug 候选
- 现象：OWW221 Peripheral 与 Xiaomi Central 已完成无共同 Wi-Fi、息屏 5 分钟、10 次重连、100 次请求和连续 15 分钟测试；双端重启、蓝牙开关恢复、分页续传及非充电功耗仍未完成。
- 关闭条件：`testing.md` 的 BLE-001 至 BLE-010 全部有真机证据，且关闭无线 ADB 后核心功能仍可用。

### BUG-017：Watch 业务曾注册到统一 PersonalMcpGateway

- 状态：Fixed，待 Windows 重启恢复验证
- 严重度：P1
- 影响：独立部署、项目故障隔离和工具命名空间。
- 现象：旧架构由统一 Gateway 同时直接连接手机和手表，并与其他项目共享 MCP/Tunnel 生命周期。
- 处理：在本仓库新增独立 `PoyiWatchMcp` 与 `PoyiWatchTunnel`；只连接手机业务门面，使用 `watch_*` 工具和 `watch://` Resource；统一 Gateway 仓库不再承载新增 Watch Adapter。
- 验证：独立 MCP Python 12 项测试、Ruff、Pyright、PowerShell 语法和真实本地 streamable HTTP 调用通过；`PoyiWatchMcp`、`PoyiWatchTunnel` 均以 LocalSystem 自动服务运行，Tunnel doctor/ready 通过。现有“步序运动”因不提供端点编辑入口，删除旧对象后以相同名称建立私人开发连接，ChatGPT 已扫描 24 个 `watch_*` 工具并成功读取状态、计划和睡眠；统一 Gateway/Tunnel 未被复用。

### BUG-018：手机 mDNS IPv6 地址生成无效 URL

- 状态：Fixed，待跨网络真机验证
- 严重度：P1
- 影响：独立 Watch MCP 手机发现和训练控制。
- 现象：手机 mDNS 返回 IPv6 时曾生成 `http://IPv6:port`，HTTP 客户端将末段误判为非法端口，远程工具表现为 `INVALID_ARGUMENT`；不可达 IPv6 还会阻塞后续 IPv4 候选。
- 处理：IPv6 authority 强制方括号、IPv4 候选优先、坏 endpoint 缓存跳过，并将 `InvalidURL` 映射为可重试的协议错误。
- 验证：新增 IPv4/IPv6 URL、地址排序和旧坏缓存测试；MCP pytest 12 项通过。
- 关闭条件：手机 IP 变化和 IPv4/IPv6 双栈各完成一次无 ADB 自动重发现。

### BUG-019：ChatGPT 私人 MCP 未读取 `watch://` Resource

- 状态：Open
- 严重度：P2
- 影响：ChatGPT 中的大量/长内容访问。
- 现象：本地 MCP `resources/list`、`resources/read watch://status` 均通过，ChatGPT 私人连接也扫描出 24 个工具，但真实请求 `watch://status` 返回 `Unknown resource`，Tunnel 日志未出现对应 Resource 转发。
- 当前判断：连接创建页只展示工具，当前 ChatGPT 插件执行面未把该 URI 路由到 MCP `resources/read`。
- 关闭条件：平台端可见并读取静态/模板 Resource，或在不增加第 25 个业务工具的前提下提供等价 Resource 入口。

### BUG-020：手机 API 监听绑定失败被静默吞掉

- 状态：Fixed，已真机验证
- 严重度：P1
- 影响：手机 0.19.0 及更早
- 现象：`PhonePlanBridgeService.serve()` 只 `new ServerSocket(PORT)` 一次；绑定抛异常时 `server` 仍为 `null`，日志分支被跳过，进程存活但 8766 不服务，MCP 全链路返回 `watch_offline` 且无任何诊断。
- 初步根因：catch 分支以 `server != null` 为前提；无重试、无 `SO_REUSEADDR`、无失败日志。
- 处理：改为显式 `bind` + `setReuseAddress(true)`，失败按 1s→30s 退避重试并记录端口与异常；`stopping` 标志区分正常停止与需重试的失败。
- 验证：`adb` 强制杀进程后重启，`logcat -s PhonePlanBridge` 出现 `API listening on 8766`，`/v1/health` 恢复 401。

### BUG-021：手机进程被回收后台服务不再恢复

- 状态：Fixed，已真机验证（依赖 MIUI 自启动授权）
- 严重度：P1
- 影响：手机 0.19.0 及更早
- 现象：进程被系统或异常终止后不再自行拉起，8766 长期不可达；用户在户外打开 ChatGPT 时 MCP 无法连接，必须手动打开手机 App 才恢复。
- 初步根因：仅依赖 `START_STICKY` 默认值；MIUI 抑制异常终止后的重启。补加广播看门狗后，Android 15 在 `uidState: RCVR` 下拒绝后台启动前台服务，日志为 `ForegroundServiceStartNotAllowedException: mAllowStartForeground false`。
- 处理：`onStartCommand` 显式返回 `START_STICKY`；`PhoneBootReceiver` 增加 `WATCHDOG` 动作与 15 分钟看门狗，并改用 `setExactAndAllowWhileIdle` 取得投递时的临时白名单，该白名单是平台文档中允许后台启动前台服务的豁免路径；无精确闹钟权限时退回 `setInexactRepeating`。
- 验证：`am crash` 后进程消失、8766 不可达；`dumpsys deviceidle tempwhitelist`（模拟精确闹钟投递时的临时白名单）后触发 `WATCHDOG` 广播，进程重建、`/v1/health` 恢复 401、看门狗重新挂起。
- 残留风险：MIUI「自启动」为系统级开关，代码无法覆盖；关闭时任何拉起路径都会失败，需用户在系统设置中授权。

### BUG-022：轨迹底图暗色滤镜把空白瓦片变成灰蓝色块

- 状态：Fixed，已真机验证
- 严重度：P2
- 影响：手表 0.19.0 及更早
- 现象：训练轨迹页整块呈 `RGB(58,71,80)` 灰蓝，看起来像未完成的占位图而不是暗色地图。
- 初步根因：滤镜矩阵把各通道乘以约 0.1 后再加常数偏移，近白色瓦片（栅格底图的大部分区域）被压到中等亮度而非接近黑色。
- 处理：改为反转亮度并保持近灰输出；白纸变近黑、深色道路与注记变亮，同时避免朴素颜色反转造成的色相翻转。
- 验证：真机截图底图为近黑，轨迹折线与起点/当前点标记对比正常。

### BUG-023：手机同步把 BLE 连接当硬前置，LAN 可用也报失败

- 状态：Fixed，已真机验证
- 严重度：P1
- 影响：手机 0.20.0 及更早
- 现象：`syncAll` 先 `connect().get(25s)` 等 BLE；BLE 失败（扫描限流/不在附近）直接抛出，错误经 `getMessage()` 显示为「连接失败：null」，历史与当前计划永远停在「连接后读取」——而同一时刻 MCP 经 LAN 全链路正常。
- 处理：BLE 连接失败时若 `lanAvailable` 则继续（请求层传输选择器自行走 LAN）；两者皆不可达才报错。错误文案改为向下钻取 cause，不再显示 null。
- 验证：真机 BLE 扫描超时后同步继续走 LAN，状态显示「蓝牙连接 · LAN 加速」，当前安排与 10 条历史读回。

### BUG-024：已配对后 mDNS 发现仍按 6 位码校验长期凭据

- 状态：Fixed，已真机验证
- 严重度：P2
- 影响：手机 0.19.0 至 0.20.0
- 现象：发现手表后取 `lanCredential()`（长期凭据，长度远大于 6）做 `length()!=6` 校验，已配对用户被误提示「已发现手表，请输入配对码」，与输入框「已完成安全配对」自相矛盾。
- 处理：6 位校验仅在未配对且使用输入框配对码时生效；已配对直接用长期凭据验证设备身份。
- 验证：真机重启发现流程后不再出现该提示。

### BUG-025：手表服务被系统回收后 8765/BLE 不自愈

- 状态：Fixed，已真机验证
- 严重度：P1
- 影响：手表 0.20.0 及更早
- 现象：OWW221 空闲期回收前台服务后无人拉起 `WatchBridgeService`，8765、mDNS 广播与 BLE 外设同时消失，手机/MCP 链路报 watch_offline，需手动打开手表 App 恢复（与手机侧 BUG-021 同构）。
- 处理：`BootReceiver` 增加 `WATCHDOG` 动作；`setExactAndAllowWhileIdle` 一次性精确闹钟每次投递自续 15 分钟链。实测本机 ColorOS 会静默丢弃第三方 `setInexactRepeating`（uid 不入 alarm 表），精确闹钟可注册。
- 验证：`am force-stop` 后 8765 不可达；触发 `WATCHDOG` 广播进程重建、`/v1/health` 恢复 401；`dumpsys alarm` 可见下一发闹钟挂起。

### BUG-026：手表时长超过 1 小时不进位，配速记法三处不一致

- 状态：Fixed
- 严重度：P2
- 影响：手表 0.20.0 及更早
- 现象：手表端 `TrainingActivity`/`HistoryActivity`/`MainActivity` 各自持有 `mm:ss` 封顶的时长格式化，75 分钟长跑主计时显示 `75:32`，而手机端同一场训练显示 `1:15:32`；历史详情配速为 `05:32/km`、训练页为 `5'32"`、手机为 `5:32 /公里`，同一产品三种记法；历史详情累计爬升直接拼接 `optDouble` 原始小数。
- 处理：新增纯 Java `Format.duration/distance`（超过 1 小时进位 `h:mm:ss`，与手机端一致），三个 Activity 删除本地副本；手表历史配速统一改用 `SpeedFusion.formatPace` 的 `5'32"` 专业记法（1 公里分段的 `/km` 后缀冗余，删除）；爬升四舍五入到整米。
- 验证：新增 `FormatTest` 覆盖进位、边界与钳制；`:app:testDebugUnitTest`、`assembleDebug` 通过。

### BUG-027：手机历史详情数据行用 38 个空格排版，配速记法同屏不一致

- 状态：Fixed，待真机截图核对
- 严重度：P2
- 影响：手机 0.20.0 及更早
- 现象：`HistoryDetailActivity.dataLine` 用硬编码 38 个空格分隔标签和值，标签长度或字号一变就错位，值不右对齐；同屏「运动概览」卡配速为 `5:32 /公里` 而「运动表现」「公里分段」卡用 formatDuration 拼出 `05:32 /公里`；累计爬升拼接原始 double；睡眠列表把整晚时长显示为秒表记法 `7:12:00`。
- 处理：`dataLine` 改为真两列（标签弹性宽度、值加粗右对齐）；新增纯 Java `PhoneFormat`（duration/distance/pace/paceSeconds/minutesHuman），两个 Activity 的私有格式化副本删除；配速统一 `5:32 /公里` 记法；爬升取整米；睡眠总长/深睡/REM 改为「7小时12分」人读格式。
- 验证：新增 `PhoneFormatTest` 5 组用例；`:phone:assembleDebug`、`:phone:testDebugUnitTest` 通过；真机渲染核对待设备恢复连接。

### BUG-028：手机同步回调迟到时启动定位前台服务导致进程崩溃

- 状态：Fixed，小米真机验证
- 严重度：P1
- 影响：手机 0.20.0 及更早，Android 14+
- 环境：Redmi 22041216C（xaga）、Android targetSDK 35、应用 0.20.0-debug
- 现象：`ensureLocationRelay()` 由异步同步成功回调触发，只校验了定位权限；当回调在 Activity 退到后台后到达（本例：启动后被其他应用抢占前台），`startForegroundService` 照常执行，`PhoneLocationRelayService.onCreate` 的 `startForeground` 因 location 类型 FGS 不允许后台启动抛 `SecurityException`，整个进程 FATAL 崩溃。
- 处理：MainActivity 增加 `foreground` 生命周期标记，`ensureLocationRelay` 非前台直接跳过并对 `startForegroundService` 兜底捕获；服务侧 `startForeground` 包 try/catch，不合规时 `stopSelf()` 静默退场，下次前台同步自动重试。
- 验证：修复前小米启动即崩（logcat FATAL 栈）；修复后启动稳定驻留前台，`logcat AndroidRuntime:E` 清零，蓝牙+LAN 链路、历史 13 条与睡眠 8 条读回正常。

## 2. 已修复/历史项

以下记录依据源码注释、README 和本地回归文件名重建；精确修复提交在首个 Git 提交之前不存在，因此证据等级低于后续规范化记录。

| 编号 | 历史问题 | 修复结果 | 状态/证据 |
| --- | --- | --- | --- |
| BUG-H001 | GPS 搜星阻塞训练开始 | 允许立即开始，弱信号时走步数估距 | Verified；README、距离回归截图 |
| BUG-H002 | OWW221 `Step_detector` 可能返回累计值 | 优先 `TYPE_STEP_COUNTER` 差分，detector 仅兜底 | Verified；README、`WorkoutService` |
| BUG-H003 | 原生距离停止更新后持续占用数据源 | 10 秒过期后退回 GPS/步数，恢复先建基线 | Verified；README、`WorkoutService` |
| BUG-H004 | 短距离反向滑动会吸回轨迹页 | 拦截首个 MOVE 时保留完整位移 | Fixed；`WatchPagerLayout` 源码注释，需自动化手势测试 |
| BUG-H005 | 378×496 页面底部内容和告警挤占操作 | 基准缩放、安全留白、仅异常显示告警 | Verified；多轮 `ui-*`/`watch-*` 回归截图 |
| BUG-H006 | 训练任务/进程重建后状态丢失 | 检查点保存并恢复计划、轨迹、心率和阶段结果 | Fixed；`WorkoutService`，需压力回归 |
| BUG-H007 | 完成态和历史可能重复/残留 | 使用 `historySaved`、训练 ID 去重和完成清理 | Fixed；`WorkoutService`、`HistoryStore` |
| BUG-H008 | 手机计划编辑后重开/同步不稳定 | 引入 schema 2 多计划库、revision 和选择同步 | Verified；`phone-flow-*`、`PhonePlanLibrary` |
| BUG-H009 | MCP `set_training_plan_profile` 只写手表当前 profile，手机计划库无记录且后续同步会覆盖 | 改为手机库幂等写入、选择、同步并回读两端校验；失败不再报告成功 | Fixed；MCP 0.4.1、`mcp/tests/test_watch_intervals_mcp.py` |
| BUG-H010 | 厂商睡眠 duration 初版按秒命名，真机 352 实际表示 352 分钟 | API、手机和 MCP 统一改为 `*Minutes`，真机以 session 起止时间交叉验证 | Fixed；WT-015、睡眠汇总单元测试 |
| BUG-H011 | 手机睡眠页只展示首个 session，且把缺失评分/血氧显示为 0 | 时长使用 record 总时长，深睡/REM/阶段聚合全部 session；缺失指标显示 `--`，MCP 汇总返回 `null` 及样本数 | Fixed；PT-008、API-010 |
| BUG-H012 | pause/resume API 采用 toggle，重复调用会反转状态 | 增加显式 action、commandId、expectedState、expiresAt 和有限结果缓存 | Fixed；API-006，待真机重试验证 |
| BUG-H013 | 仓库缺少 Gradle Wrapper | 加入并锁定 Gradle 8.14.3 Wrapper，CI 与本地统一入口 | Fixed；CI/本地构建验证 |
| BUG-H014 | schema 2 缺少 schema 3 数值时迁移得到 NaN，整批历史迁移失败 | 旧字段使用有限默认值，输出边界再次归一化；新增缺字段和非有限值测试 | Verified；OWW221 旧版 3 条历史迁移后索引仍为 3 |
| BUG-H015 | 活动进程重建后首页“继续”仍进入准备页，绑定服务后计时显示 00:00 | 恢复入口先显式启动服务读取 checkpoint，再打开现有 TrainingActivity | Verified；覆盖安装恢复后计时从 checkpoint 继续增长 |
| BUG-H016 | 首页长训练要求挤压首屏，配对码和计划入口被底部裁切 | 首页移除重复要求正文并压缩固定尺寸，完整要求保留在计划页 | Verified；OWW221 378×496 截图和 UI bounds |
| BUG-H017 | Gateway 写计划在响应丢失或进程终止后可能重复执行，且旧 revision 未拒绝 | 手机 API v2 持久记录 requestId/请求哈希/首次结果，执行前写 in_progress，并用单调 revision 恢复提交后的中断 | Fixed；`MutationGuardTest`、双模块构建，待 API-015 真机故障注入 |
| BUG-H018 | Xiaomi 短时间连续 BLE 扫描触发系统限流，第四轮重连超时 | 首次发现后缓存已验证设备并直接 GATT 重连，仅首次或直连不可用时扫描 | Verified；10 次真机断开/重连通过 |

## 3. 新缺陷模板

```markdown
### BUG-NNN：标题
- 状态：Open
- 严重度：P0/P1/P2/P3
- 发现版本：
- 环境：设备、系统、应用版本
- 前置条件：
- 复现步骤：
- 实际结果：
- 预期结果：
- 日志/截图：不得含敏感数据
- 初步根因：
- 修复提交：
- 验证用例：
```
