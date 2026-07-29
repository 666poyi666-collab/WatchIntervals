# 缺陷与技术债台账

状态：维护中  
基线：2026-07-29

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
- 2026-07-29 进展：Watch 0.21.0 全界面重构已在 OWW221 生成主页三屏、准备、训练五屏/确认、计划和历史截图并完成目视回归；截图仍仅位于临时目录，未形成脱敏 evidence manifest，因此本项保持 Open。
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

- 状态：Open（2026-07-26 晚补测缩小范围）
- 严重度：P1
- 影响：手表/手机 0.19.0-debug 候选
- 现象：OWW221 Peripheral 与 Xiaomi Central 已完成无共同 Wi-Fi、息屏 5 分钟、10 次重连、100 次请求和连续 15 分钟测试；双端重启、蓝牙开关恢复、分页续传及非充电功耗仍未完成。
- 2026-07-26 晚补测（见 testing.md「0.20.0 BLE 恢复矩阵补测」）：BLE-005 手机半场通过（关/开蓝牙 12 秒内自动重连）；BLE-003 手表半场通过（Activity 关闭 8765 门禁存活）、手机半场确认两段式看门狗设计行为；闹钟投递后完整恢复证据不完整（21:40 窗口日志被轮转覆盖、实验被 0.21.0 重装破坏，21:53 仅见 PlanBridgeService 在运行而 8766 宿主未恢复），按未证处理待重跑。
- 仍开放：BLE-004（双端重启，会切断无线 ADB 取证通道，需人在场）、BLE-005 手表半场（构建无 shell 蓝牙开关，需手动）、BLE-009（分页续传）、BLE-010（非充电真实功耗）。
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

### BUG-029：手表历史详情从列表进入缺失全部派生卡片，数据行标签被截断

- 状态：Fixed，OWW221 真机验证
- 严重度：P2
- 影响：手表 0.20.0 及更早
- 现象：详情页的分段/最佳配速/心率范围/累计爬升卡与轨迹图都由轨迹、心率样本文件现算，但从完整历史列表点击进入时 `showDetail` 直接使用索引里的摘要对象（样本为空），上述内容全部缺失；同一条记录从首页速览进入（`record_id` intent → `HistoryStore.find` 全量加载）则完整——双路径行为割裂。另外 `detailLine` 值列固定 180dp，在 378px 画布上把标签列压到约 40dp，两位数分段显示为「10 公…」、「实测范围」显示为「实测…」。
- 处理：`showDetail` 先经 `HistoryStore.find` 全量加载（找不到时回退摘要对象）；值列改 wrap_content、标签列弹性占满。
- 验证：注入合成长跑记录（10.2 km / 75:32 / 工程化分段，见项目日志）后真机对比：修复前列表路径详情只有摘要卡+空轨迹图；修复后两条路径一致，分段 11 行、最快段高亮、心率 128–171、爬升 36 m、标签完整无截断。合成记录验证后已删除。

### BUG-030：手机无法读取手表状态时仍显示高亮“开始训练”

- 状态：Fixed，模拟器验证
- 严重度：P2
- 影响：手机 0.21.0 首个视觉候选
- 现象：训练页状态轮询失败后，环心明确显示“无法读取手表状态”，下方却仍显示可点击的亮绿色“开始训练”；操作必然失败，错误态与操作层互相矛盾。
- 处理：不可达状态单独渲染低强调“打开连接设置”，不再暴露训练控制；空闲且状态读取成功时才显示“开始训练”。
- 验证：Pixel 6 / API 35 模拟器断连场景截图；`:phone:testDebugUnitTest` 与双模块构建通过。真实 BLE/LAN 错误态待手机真机截图确认。

### BUG-031：无轨迹训练详情仍占用首屏展示空白地图

- 状态：Fixed，模拟器验证
- 严重度：P2
- 影响：手机 0.20.0 至 0.21.0 首个视觉候选
- 现象：室内或无定位训练仍创建 360dp 地图并定位到默认城市，首屏大半区域只有空白瓦片网格和“没有有效定位轨迹”浮层；既挤走核心指标，也容易被理解为地图加载失败。
- 处理：无有效轨迹时隐藏地图视图，以 136dp 深色空状态明确说明“本次训练未记录定位轨迹”，同时不显示地图署名；真实轨迹仍保留 340dp 地图、缩放与起终点。
- 验证：Pixel 6 / API 35 注入不落盘的 10.24 km 合成详情，首屏同时可见空状态、运动概览与详细数据；测试结束后未向应用历史写入记录。

### BUG-032：电脑关机后 ChatGPT 无法读取步序数据

- 状态：Verified
- 严重度：P1
- 影响：Phone 0.21.0 及以前的本机 Tunnel 架构
- 现象：旧“步序运动”连接必须经过 Windows Watch MCP、Tunnel 和手机 8766；电脑关机后连接器整体不可用，即使手机和手表仍在线也无法读取最近训练、睡眠或计划。
- 处理：Phone 0.21.1 增加独立 `SYNC_KEY` 的 HTTPS 快照上行，把六个只读数据面直接同步到 Watch Cloud MCP；ChatGPT 改接云端 MCP，云端只提供快照读取与同步概览，不冒充本机训练控制。
- 验证：停止全部本机 MCP/Tunnel/watchdog 服务后云端工具仍返回手机来源快照；ChatGPT 新连接扫描到 7 个云端工具且无 4 个训练控制工具。互联网、Cloudflare 或手机上行不可用时返回最后快照及 stale 元数据。
- 后续：该证据只证明 0.21.1 旧快照链路；因明文快照不满足端到端加密和双向 catch-up，Phone 0.22.0 已在本地替换为 REQ-SYNC-012 至 014。旧证据不能用于把 V2 标记为完成。

### BUG-033：旧云快照与隐式根密钥会泄露明文或造成密文空间分叉

- 状态：Fixed，待 staging/真机验证
- 严重度：P1
- 影响：Phone 0.21.1 快照实现及 Phone 0.22.0 首个加密同步草案
- 现象：`/sync/push` 上传可由云端直接读取的状态、计划、训练和睡眠摘要；首个 V2 草案在根密钥缺失时自动生成随机 key，重装或第二设备会得到另一把 key，随后无法解密既有 change，重新保存 token 还会无条件清除旧 root。本地计划列表暂时缺项也会被推断为远端删除。
- 根因：快照模型没有端到端密钥生命周期、双向 outbox/cursor 和显式 tombstone；初版迁移把“有 token”误当成“已授权获得同一根密钥”。
- 处理：移除 `CloudSnapshotPayload`，`CloudSnapshotSync` 只转入 `/sync/v2/exchange`；token/root 用 Android Keystore 包装，根密钥只允许显式初始化、离线恢复或当前一次性设备批准；换 root 清空 state 后 pull-first；计划库升为 schema 3 显式 tombstone；conflict 保留双方；生产默认拒绝 `/sync/push` 和 plaintext V1 数据路由。
- 验证：`EncryptedWatchSyncTest`、`WatchSyncKeyPackagesTest`、`PhonePlanLibrarySyncFormatTest` 与 Phone debug 编译通过；Worker 新增旧 V1 默认 410 负测。Android Keystore 真机、staging revision、三轮 PC-off 与 crash/Doze 故障注入尚未完成，因此不标记 Verified。

### BUG-034：两端敏感数据可被 Auto Backup，公开 watchdog 可被第三方触发

- 状态：Fixed，待真机验证
- 严重度：P1
- 影响：Watch/Phone 0.21.x 及以前
- 现象：BLE pairing secret、LAN credential 与 Gateway API token 以 plaintext SharedPreferences 保存；两模块允许默认 Auto Backup，手机计划和手表训练/轨迹也可能进入系统备份。同一个 `exported=true` receiver 同时接收系统开机和自定义 watchdog action，其他应用可发送后者反复拉起前台服务。
- 处理：新增通用 Android Keystore AES-GCM envelope，首次读取原子迁移 pairing/LAN/Gateway token；配对码不再写入 `connection.xml`；两模块 `allowBackup=false`，Phone 另保留细粒度 exclusion 作为防御纵深；两端开机 receiver 只接收受保护 `BOOT_COMPLETED`，app watchdog 均拆到 `exported=false` receiver。
- 验证：Phone JVM/assemble 门禁通过；仍需覆盖安装迁移、错误 Keystore、Auto Backup/设备迁移清单、外部广播拒绝和 reboot/watchdog 真机回归。

### BUG-035：跑者图标造型生硬，实时刷新与轨迹地图拖慢翻页

- 状态：Verified
- 严重度：P2
- 影响：Watch 0.21.0 首个全界面视觉候选
- 环境：OWW221、Android 11、378×496、60 Hz
- 现象：首版代码自绘跑者使用等粗关节和零散速度线，小尺寸下像折断的火柴人；主页/训练横滑虽能换页，但页码不跟手、释放吸附缺少系统运动应用的节奏，训练实时刷新和轨迹地图更新时还能观察到掉帧。
- 初步根因：视觉图形没有形成连贯重心；pager 使用通用触摸阈值和页内独立圆点；训练每秒同时改写隐藏页面并复制整组轨迹坐标，地图重复提交整条折线、标记和镜头动画，历史详情首屏也过早初始化地图。这些工作会与拖动/吸附帧竞争主线程。
- 处理：跑者改为粗实心、前倾重心的独立几何剪影；`WatchPagerLayout` 使用 paging touch slop、quintic ease-out、约 210–267 ms 吸附和固定跟手页码，并处理吸附中再次触摸，避免停在半页；主页仅在空闲预热相邻静态页，训练不缓存整页。训练在拖动/吸附期间延期刷新，停稳后只更新当前页；隐藏轨迹页的 snapshot 不复制坐标数组。`WorkoutRouteView` 按需创建并离页暂停，复用折线、起终点和位图，只追加新增点，镜头最多每 5 秒无动画重算一次；历史详情延迟初始化地图。
- 验证：关联 `WT-021`。OWW221 固定脚本暖态主页 `0↔1` 为 592 帧/119 jank（20.10%）/P50 10 ms/P90 22 ms，三屏往返为 619 帧/88 jank（14.22%）/P50 10 ms/P90 18 ms，训练五屏为 619 帧/193 jank（31.18%）/P50 12 ms/P90 23 ms；吸附中点按回到完整页，退后台倒计时可恢复，真实历史轨迹与空状态均显示。户外连续 GNSS、佩戴心率和长时间功耗不在本项关闭范围，继续由 WT-005、WT-018、BLE-010 覆盖。

### BUG-036：训练页固定高度未吃满 496px，底部形成大块黑下巴

- 状态：Verified
- 严重度：P2
- 影响：Watch 0.21.0 第二轮视觉候选
- 环境：OWW221、Android 11、378×496
- 现象：综合仪表的心率趋势固定为 30dp，后面再用 weighted 空 View 撑开；实际内容约在 y=379 结束，页码在 y=483，中间留下约 90–100px 无信息黑区。训练数据与阶段页也存在同类固定内容高度加空撑杆。
- 处理：综合仪表将剩余高度交给真实心率趋势面板，无样本显示明确空状态而不绘制假曲线；训练数据三行按剩余高度等分；阶段环容器按剩余高度伸展。固定页码和底部安全区保持不变。
- 验证：关联 `WT-022`。覆盖安装后在 378×496 真机逐屏截图，综合仪表有效内容延伸至 y=469、页码位于 y=483；训练数据和阶段页均无固定底部空撑杆，文字、圆环、页码无裁切。测试生成的 0m 记录已删除，历史恢复原有 2 条。

### BUG-037：道路底图与灰度滤镜隐藏河道/跑道，只剩国道高速

- 状态：In Progress
- 严重度：P2
- 影响：Watch 0.21.0 视觉候选
- 环境：OWW221、Android 11、378×496
- 现象：用户实际沿河绕圈，但 `style=7` 道路栅格经过反色灰度滤镜后河道与堤岸细节消失，画面只剩粗大的主干道/高速，看起来像跑错位置。首次把问题误判为路线放大过度并继续缩远，反而让道路层级更粗。
- 处理：卫星候选已彻底撤销，高德/osmdroid 降级也已从手表模块删除。观察层改为 Baidu Map SDK 7.5.9 原生矢量底图、本地暗色样式和 SDK GPS→BD-09LL 转换；地图保持 164dp，取景横向 15dp/纵向 25dp，轨迹 3dp。真实坐标和几何不变。
- 验证：关联 `WT-023`。编译和单元测试已通过；百度 Android AK 尚需为当前包名/签名完成控制台登记，因此新底图还未在 OWW221 联网实测，本项保持 In Progress。历史文件仍为原有数据，不得用地图授权状态隐藏或改写。

### BUG-038：误把 legacy 迁移精度当逐点实测值，并错误隐藏历史轨迹

- 状态：In Progress
- 严重度：P1
- 影响：旧版轨迹过滤及 Watch 0.21.0 视觉候选
- 环境：OWW221、现有 2.43km 历史记录
- 现象：为解释“路线不像沿河跑道”，曾把 280 个 `legacy` 点共同携带的 125m 值当成每个原始 fix 的可靠实测精度，随后新增 35/50m 门禁并把整条历史路线隐藏。这直接破坏了用户查看既有轨迹的能力。
- 根因：旧 schema 的迁移默认值与原始定位 accuracy 没有可追溯区分，不能仅凭 `legacy + 125m` 推断真实采集质量；此前系统 Baidu 与应用 AMap 的底图差异也被错误归因于坐标精度。系统旧轨迹实际保存在健康服务 `sport_gps` 表，但 BinderProvider 在 normal permission 之外还执行包签名校验，普通第三方签名不能直接读取。
- 处理：删除未经验证的 35/50m 新门禁，恢复既有 200m 获取/150m 连续跟踪边界；历史详情重新把所有原始合法点传给 `WorkoutRouteView`，不删除、不吸附、不伪造闭环。后续把“采集实测精度”和“迁移估计值”分字段处理，不能再让元数据不确定性抹掉路线。
- 验证：关联 `WT-024`。OWW221 已重新显示原有 2.43km、280 点路线，历史仍为 2 条。室内准备页 20 秒只观察到 24 个卫星候选，GPS provider 的 last location 仍为 null、accuracy report 为 0，因此当前没有证据宣称可定位到 35m 以下；需在开阔户外取得真实 fix 后继续验证。本项保持 In Progress。

### BUG-039：训练成功落盘后手机不会立即触发 V2 云同步

- 状态：Fixed，待手机/手表真机与 PC-off 验证
- 严重度：P1
- 影响：Watch 0.21.0 / Phone 0.22.0 首个加密 V2 候选
- 现象：手机虽有 boot、network、Doze 和 15 分钟周期 WorkManager，但手表训练完成后没有业务入口主动 enqueue；用户可能在周期任务前从云端 MCP 读不到刚完成的训练。
- 根因：`BleGattTransport.subscribe()` 已实现未匹配安全消息分发，但 `WatchConnectionManager` 没有注册 listener，`HistoryStore`/`WatchLinkService` 也没有发出历史变化提示。
- 处理：训练成功落盘或真实删除后，手表向已认证且订阅 indication 的手机发送严格两字段、无业务数据的 `history_changed` 安全事件；手机 exact-key/version 校验后 enqueue 网络约束唯一任务。BLE/LAN 成功重连也 enqueue，15 分钟周期继续兜底。
- 自动化：双端纯 Java 合同测试覆盖事件最小字段、敏感字段不存在、版本/状态/replyTo/多余字段拒绝；完整 Gradle 门禁见 `project-log.md`。
- 剩余：需按 `WT-025`、`PT-020`、`BLE-011` 在真实 OWW221/手机上验证 indication、后台蜂窝/Wi-Fi、重复事件、断联重连和 Doze。

### BUG-040：真实 Android Keystore 拒绝调用方提供的 GCM IV，所有凭据包装失败

- 状态：Verified
- 严重度：P0
- 影响：Watch 0.21.0 / Phone 0.22.0 加密候选
- 环境：真实 Phone、Android 15；JVM 单测无法提供 `AndroidKeyStore`
- 现象：V2 debug provisioning 不生成 `encrypted_watch_sync_v1.xml`，device token/root 无法保存；同一模式也影响 Phone pairing/LAN/Gateway secret 和 Watch pairing secret。
- 根因：Keystore AES key 设置了 `randomizedEncryptionRequired=true`，但 encryption 又调用 `Cipher.init(ENCRYPT_MODE, key, GCMParameterSpec)` 注入自生成 IV；真实 Keystore2 以 `InvalidAlgorithmParameterException: Caller-provided IV not permitted` fail closed。
- 处理：三个 Keystore wrapper 统一改为 `Cipher.init(ENCRYPT_MODE, key)`，从 provider 读取并校验 12-byte `Cipher.getIV()` 后与 ciphertext 一起持久化；decrypt 格式保持兼容。
- 验证：关联 `PT-021`。真实 Phone androidTest 验证 nonce 不重复、正确 AAD 回解、错误 AAD 拒绝，以及 staging device token/root 均以 ciphertext/nonce 保存、旧 plaintext v1 配置清除；force-stop 后仍可回解，并持久化网络约束的一次性/周期 WorkManager。真实 Watch 覆盖安装后通过 provider-generated nonce、正确/错误 AAD 回解，并在进程停止后确认自定义 action 与显式伪造 `BOOT_COMPLETED` 均不能拉起进程。未执行设备重启，不把本项证据扩张为 PC-off 完成。

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
