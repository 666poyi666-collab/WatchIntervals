# 项目开发与决策日志

本日志保存 Vibe Coding 过程中已经沉淀为产品/工程事实的内容。日期来自文件时间、版本化截图和当前 Git/Release 记录；首个 Git 提交之前的多轮改动没有逐次提交，因此以下按主题重建，不把截图版本号等同于正式发布版本。

## 2026-07-23：手表独立训练主流程

- 建立 Android 手表应用和 378×496 竖屏布局。
- 完成首页、计划、阶段编辑、准备、训练、暂停/继续、停止确认和完成流程。
- 阶段模型确定为 `RUN/WALK/REST` + `DISTANCE/TIME`。
- 建立 1 km 跑 + 200 m 快走默认计划，并迭代 15 秒时间阶段用于快速验证。
- 开始使用前台服务维持训练计时，加入阶段达标震动和自动推进。
- 多轮截图显示重点修正：小屏底部裁切、计时显示、计划编辑器滚动、暂停面板和完成态。

## 2026-07-23：传感器和真实数据

- 接入 GPS、GNSS 卫星状态、步数与公开心率传感器。
- 明确“不给出伪造健康数据”：无样本时显示读取状态或佩戴提示。
- 距离阶段从“等待 GPS 才能开始”调整为“立即开始 + 数据源降级”。
- 针对 OWW221 的非标准 step detector 行为，选择 step counter 累计差作为主步数来源。
- 加入异常 GPS 点过滤、移动速度约束、步数估距和来源标记。

## 2026-07-24：厂商系统运动能力调查

- 对当前实机 HealthKit/运动服务进行静态和 Binder 验证。
- 形成 `system-exercise-implementation.md`，确认接口主体使用 protobuf `ProtoParcelable`。
- 架构决策：动态加载与固件匹配的厂商客户端，执行 Provider/API version/capabilities 三段检测。
- 当前固件能力映射为空，决定保留桥接并自动降级，不因厂商接口不可用阻塞训练。
- 增加原生距离 10 秒 stale 策略，避免陈旧累计值长期压制 GPS/步数。

## 2026-07-24：轨迹、恢复与页面手势

- 训练数据作为第一页，实时轨迹固定为右侧页面，支持双向跟手返回。
- 修复短反向拖动吸回轨迹页的问题，保留首次拦截 MOVE 的完整位移。
- 轨迹和传感器数据纳入活动会话检查点；恢复时兼容早期格式并跳过损坏点。
- 历史升级为 schema 2，记录完整轨迹、实际步数、心率样本、阶段结果和统计。
- 历史容量确定为 200 条，并采用临时文件写入后替换。

## 2026-07-24：手机伴侣、局域网和 MCP

- 新增 `phone` 应用，完成 mDNS 自动发现、六位码配对和手表 8765 API。
- 手机建立 schema 2 多计划库，支持计划命名、分组、要求、编辑、选择和同步。
- 新增手机定位中继和历史详情/轨迹查看。
- 新增手表/手机开机前台桥服务，MCP 可查询状态、计划、统计、历史和完整轨迹，并控制训练。
- 决策：连续轨迹同步优先使用同一局域网；BLE 暂列后续候选。

## 2026-07-24：仓库和首个 APK 发布

- 初始化 Git 仓库，加入忽略规则，排除分析 APK、构建目录、虚拟环境、日志、截图和本机配置。
- 首个提交：`b68f189 Initial release of WatchIntervals`。
- 创建私有 GitHub 仓库 `666poyi666-collab/WatchIntervals`，默认分支 `main`。
- 使用 Gradle 8.14.3 验证 `:app:assembleDebug :phone:assembleDebug`，结果成功。
- 创建 `v0.16.0` prerelease：手表 `0.16.0`（26），手机 `0.9.0`（9）。
- APK SHA-256：
  - watch：`5625CAE4A7095B6613073F5EF1AFF29728C1AEFE3C27C7F9FADB0CAF78ABBAFB`
  - phone：`C3A2E2BE72EB638206627EB3F7AE9E6C6B785B492CC715BE6B8EFF24F770766F`

## 2026-07-25：建立长期文档基线

- 审计现有 README、源码、系统运动说明、Git/Release 和本地测试留痕。
- 建立需求、架构开发、测试、缺陷、项目日志、CHANGELOG 六类文档并增加统一索引。
- 首次显式登记八项开放问题，其中自动测试和控制 API 幂等性为最高优先级。
- 确立规则：后续功能/修复必须同时更新编号化需求、测试证据、Bug 台账和版本日志。

## 2026-07-25：修正 MCP 计划同步成功判定

- 根据导出的 ChatGPT 对话复核“基线快走计划已写入并同步”的声明。
- 实时读取确认手机与手表计划库内容一致，但均不存在对话声称写入的计划。
- 根因是 `set_training_plan_profile` 直接写手表当前 `PlanStore`，绕过了手机主计划库；任意后续计划库同步都可能覆盖该临时 profile。
- MCP 0.4.1 改为：稳定 ID 写入手机计划库、选择计划、同步手表、回读手机列表/手表计划库/手表 profile，全部一致才返回成功。
- 新增 4 个 Python 单元测试，覆盖成功、同步 pending、回读不一致和重试幂等 ID。

## 2026-07-25：接入系统级详细睡眠

- 关联 `REQ-DATA-008`、`REQ-DATA-009`、`BUG-H010`。
- 新增只读 `SystemSleepBridge`，通过系统 HealthKit Store API 查询 `SleepSessionRecord`，不访问或复制厂商私有数据库。
- 首次打开手表应用使用系统健康权限页请求“读取睡眠数据”；API 返回数据来源及 `ready`、`permission_required`、`error` 状态。
- `/v1/sleep?days=N` 向手机和 MCP 提供评分、血氧、OSA 原值、心率/呼吸基准与范围、多个 session 和完整 stage 时间线。
- OWW221 Android 11 真机经 USB 验证：14 天请求返回 8 条系统记录，存在多 session 和完整 stage；以起止时间确认厂商 duration 单位为分钟。
- MCP 0.5.0 增加 `get_latest_sleep`、`list_sleep_records`、`summarize_sleep`，单元测试由 4 项增至 7 项。
- 发布构建：手表 `0.17.0`（27）、手机 `0.10.0`（10），均为 debug prerelease。
- 睡眠精度复核发现系统记录存在指标缺失，且手机页只显示首个 session；手机 `0.10.1` 改为聚合全部 session，MCP `0.5.1` 用 `null` 和样本计数表达缺失值。
- 新增长效 ChatGPT Tunnel 安装、守护和检查脚本：固定 Tunnel ID，Runtime Key 经 DPAPI 加密，登录后自动启动并在退出后重连；关联 `REQ-SYNC-004`、`BUG-009`。
- APK SHA-256：watch `3FC388C682E0AFD393AD4CD916C9152B3B8E8C3992447840AC636D2E4D0F70DA`；phone `A44B5212E9F847C1B29013A2AD60B01C4C2954C7A027EE125DD94F393D7907D7`。

## 2026-07-25：户外可靠性、协议 v2 与工程基线

- 关联 `REQ-WORKOUT-002`、`REQ-WORKOUT-007`、`REQ-DATA-010` 至 `REQ-DATA-012`、`REQ-SYNC-004` 至 `REQ-SYNC-006`，以及对应 BUG 台账。
- 提交 Gradle 8.14.3 Wrapper 和 GitHub Actions；CI 统一执行 Python/Java 测试、Android lint、两端构建、差异检查及 debug 产物打包。
- 将计划完成与会话结束分离：最后阶段达标后进入自由记录，训练服务继续持有状态和传感器，只有手动结束才归档。
- 活动轨迹和心率改为 NDJSON 追加文件，检查点保持有界并原子替换；历史改为每记录独立目录和最多 200 条的摘要索引。
- 增加 10 秒平滑速度、来源距离/切换证据、计划内与自由记录统计，以及历史摘要/详情/游标分页协议。
- 手机增加计划 outbox 和 operationId/revision/ACK 基础协议；控制命令分离 pause/resume 并加入状态前置条件和过期时间。
- Windows MCP 拆为长期 Gateway 与 Tunnel 两层，手机/手表通过 mDNS 和稳定设备 ID 恢复运行时地址；远程可调用错误不再包含无法自证的 `TUNNEL_OFFLINE`。
- BLE 只实现 debug ping/pong POC，不接入同步引擎；真实户外、后台 BLE、Windows Tunnel 端到端和功耗门禁均保留为待验证项。
- 本轮本地证据：两端 Java 编译、手表单元测试、10 项 MCP 测试和 `git diff --check` 通过；完整 lint/APK 构建与哈希记录见本次交付结果。

## 2026-07-25：0.18.0 OWW221 升级与短计划验收

- 通过 USB 将 OWW221 切换为网络 ADB；确认旧版为 `0.17.0`（27），备份旧 APK 和应用私有数据后使用 `install -r` 升级。
- 首次启动发现 schema 2 缺少新数值字段时 Android JSON 返回 NaN，修复有限值兼容并新增 2 项迁移测试；旧历史 3 条完整迁移。
- 15 秒计划完成后保持 RUNNING 并自由记录 255 秒以上；暂停不累计、继续恢复、重复 resume/stop 幂等，手动结束只新增一条历史。
- 利用覆盖安装终止活动进程，发现首页恢复入口只绑定空服务；修复为先启动服务恢复 checkpoint，再打开训练页，恢复后活动时间继续增长。
- 378×496 截图发现首页底部配对码和计划入口裁切；移除首页重复要求正文并压缩固定尺寸，计划要求继续在计划页完整展示。
- 真机遍历训练核心、控制、自由记录计划和轨迹四页，未发现裁切或重叠；室内未佩戴测试没有轨迹/心率，不作为户外数据验收。
- 首次推送稳定化分支触发 GitHub Actions；Linux runner 暴露 `gradlew` 缺少可执行位并以 exit 126 失败，修正 Git mode 为 100755 后重跑。

## 2026-07-25：活动样本检查点一致性

- 关联 `REQ-DATA-010`、`BUG-012`。
- 将 checkpoint 中的 route/heart offset 定义为样本提交边界；服务恢复统计前先截断 offset 后的完整或损坏尾行，并在非法半行 offset 时回退到上一完整行。
- 采用截断而非路线重放，因为路线文件无法无歧义还原系统运动距离、步数估距及阶段边界；错误重放会比丢弃未提交尾部产生更严重的重复累计。
- 有效样本计数同步改为只统计可解析 JSON 行。
- `WorkoutFileStoreTest` 新增 2 项，`:app:testDebugUnitTest` 通过；GitHub Actions run `30164226710` 亦已完整通过此前冻结提交的测试、lint、双端构建和产物上传。
- 完整执行 `gradlew.bat test lint :app:assembleDebug :phone:assembleDebug` 成功；手表 APK SHA-256 为 `8DA31F39A1172DB4C43CADE5ED2187C0BE49B7BD4487644ACF6882CD75CE4F44`，通过 USB 覆盖安装后网络 ADB 仍保持在线。

## 2026-07-25：Personal MCP Gateway 手机写入契约

- 关联 `REQ-SYNC-007`、`BUG-H017`、`API-015`。
- 手机 8766 的计划新增/更新和选择接口接受 `requestId`、`expectedRevision` 封装，同时保留旧直接正文读取兼容。
- 同一 requestId/正文返回首次结果，不同正文复用 ID 或旧 revision 返回 409；缓存使用同步持久提交。
- 为消除计划库已提交但结果缓存未提交的重复执行窗口，执行前记录 `in_progress` 和初始 revision，恢复时用单调 library revision 判断已提交并重建结果。
- 本地执行 `gradlew.bat :phone:testDebugUnitTest :app:assembleDebug :phone:assembleDebug` 成功；进程终止故障注入保留为真机门禁。

## 2026-07-26：0.19.0 手机—手表 BLE 主链路

- 关联 `REQ-SYNC-008` 至 `REQ-SYNC-010`、`BUG-015`、`BUG-016`。
- 先在 OWW221/Xiaomi 上确认手机 Central、手表 Peripheral 角色可用，再删除两端 exported debug POC，建立正式 GATT 服务与连接管理器。
- 协议使用 16 字节帧头，默认 MTU 23 可传输；真机 MTU 517 测试后补充 512 字节属性值上限及 Android 13 原子写 API。
- 手表新增共享 `WatchCommandRouter`；手机计划 outbox、定位中继和正常业务通过 `WatchConnectionManager` 选择 BLE/LAN，不再直接依赖固定 IP。
- 真机日志确认广播、连接、MTU、四项 CCCD、过渡 AUTH、`/v1/sync/operations`、`/v1/plan/profile` 和 `/v1/location` 成功；手机 UI 已显示 BLE + LAN 加速状态。
- 无活动训练时点击手机“暂停”收到手表 `409 state_mismatch`，验证控制请求和 expectedState 前置条件且未修改训练数据。
- 两端升级为 0.19.0 debug 候选并覆盖安装。测试仍使用网络 ADB；安全配对、真实训练控制/重复 commandId、无 Wi-Fi、后台、重启、长时及功耗门禁保持开放。

## 2026-07-26：0.19.0 安全 BLE 与缩短门禁验收

- 关联 `REQ-SYNC-010`、`BUG-015`、`BUG-016`、`PT-014`、`BLE-001/002/006/007/008/010`。
- 首次配对改为 P-256 ECDH、公钥与随机数交换、六位码派生确认及 AES-GCM 下发长期密钥；重连使用双向 HMAC 挑战，业务消息使用会话密钥、严格序号、时间窗和 AES-GCM。
- OWW221/Xiaomi 首次交换及覆盖安装后的持久密钥重连成功；10 次重连、102 次加密 status 200 和 1 次精确旧密文重放拒绝通过，拒绝后新请求仍成功。
- 真机发现 Xiaomi 对短时间连续 BLE 扫描触发 scan throttling；手机首次发现后缓存已验证 `BluetoothDevice` 并直接 GATT 重连，避免把设备重连错误实现为反复扫描。
- BLE 真实会话控制完成 start、重复 pause、重复 resume 和 stop；重复 commandId 返回 duplicate，状态不反转，历史只新增 1 条。
- 两端关闭 Wi-Fi、无线 ADB 离线并息屏，继续运行约 15 分钟；完成 94 次加密请求和 4 轮重复暂停/继续，训练落盘活动时间 951,996 ms、暂停 8,343 ms，最终正常停止。
- 手表在 USB 取证期间持续充电，电量从 72% 升至 81%；该数据只证明测试期间供电状态，不作为 BLE-010 功耗结果。非充电双端电量测试、双端重启、蓝牙开关恢复和分页续传继续开放。
- 最终执行 MCP 10 项测试、两端 JVM 测试、lint、debug APK 构建、手机 androidTest APK 构建和 `git diff --check`，均通过；Temurin 21 C2 在一次 R8 优化中崩溃后，以 `-XX:TieredStopAtLevel=1` 重跑通过。
- debug 产物：`dist/0.19.0-debug/`；使用既有 debug 证书保持升级签名连续性，watch SHA-256 `2C6FD6FEAA58BB7F30A89A15D28742EF1895DDF542123E17701BB1AC86152943`，phone SHA-256 `A737FD6CE0213FAEC0130BED75E1A9E4EC1245B8F598C694C5511AD2174D7E6C`。两端 `install -r` 成功，配对数据保留并自动恢复安全 BLE 会话。

## 2026-07-26：Watch MCP 从统一 Gateway 收回为独立服务

- 关联 `REQ-SYNC-003` 至 `REQ-SYNC-007`、`BUG-017`、`API-016` 至 `API-018`。
- 保留手机 8766 的版本化业务 API，并补充独立随机 Bearer Token、健康/能力端点、严格 UUID/revision/命令过期校验和持久结果重放。
- 在 `mcp/src/watch_mcp` 建立可打 Wheel 的独立服务；桌面只通过 mDNS 和稳定 `phoneDeviceId` 访问手机，由手机内部安全 BLE/LAN 连接手表，不再直连手表或使用固定 IP/ADB。
- 工具统一为 24 个 `watch_*`；轨迹、心率和完整睡眠明细使用 8 类 `watch://` Resource。新增独立 WinSW `PoyiWatchMcp`、`PoyiWatchTunnel`、DPAPI 数据目录和安装/诊断脚本。
- 自动验证：Ruff 通过、Pyright 0 错误、独立 MCP pytest 9 项通过且覆盖率 83.28%、`pip-audit` 无已知漏洞、PowerShell 全脚本解析通过；Android `test lint :app:assembleDebug :phone:assembleDebug` 通过。
- 当前本地候选产物：watch APK SHA-256 `2C6FD6FEAA58BB7F30A89A15D28742EF1895DDF542123E17701BB1AC86152943`，phone APK SHA-256 `3A8C12D61BC9A5744B79B862618033F075CB8A61717912E9CF023FAF95DF63B4`，MCP Wheel SHA-256 `A56E6FB0B6726268EC439A187B687EA1D0BF86878FA9643B112C064C61668464`。
- OWW221 已通过有线 ADB 覆盖安装 0.19.0 (29) 并保留数据。小米手机当时不在线，无法推送新版手机 APK、签发 Token 或执行真实 BLE/API；浏览器未登录 ChatGPT，无法创建独立 Tunnel/应用，这两项不得写成已验收。

## 2026-07-26：小米手机补齐安装、手机 API 与独立 MCP 实测

- 关联 `REQ-SYNC-003` 至 `REQ-SYNC-010`、`BUG-016`、`BUG-017`、`API-013`、`API-015` 至 `API-018`。
- 小米 `xaga` 手机上线后，仅向该设备覆盖安装 `phone-debug.apk`，授予定位、附近设备和通知权限，启动后确认 `PhonePlanBridgeService`、`PhoneCompanionService` 和定位中继前台服务运行。
- 发现安全 BLE 配对完成后手机会清除旧 6 位码，导致 `/v1/auth/token` 仍只接受旧码而无法签发独立 Watch MCP token；新增 `BootstrapCredentialValidator`，token bootstrap 同时接受未迁移旧 6 位码和已配对长期 LAN 凭据，空值或错误值仍拒绝。
- 发现独立 MCP 在 stateless HTTP 下每个请求 lifespan 结束会关闭全局 `PhoneApiClient`，后续真实工具调用报 client closed；`PhoneApiClient` 现在每次请求前检查并重建已关闭的 `httpx.AsyncClient`。
- 手机 8766 实测：未带 token 返回 401；用已配对凭据签发 256-bit Bearer Token 成功；相同签发请求返回 duplicate；不同 requestId 携带旧 revision 返回 409；过期控制命令 `/v1/control/pause` 返回 `409 {"error":"command_expired"}` 且不转发。
- 独立 Watch MCP dev 服务使用真实手机 token 与稳定 `phoneDeviceId` 启动在 `127.0.0.1:8768`；`/healthz` 返回 `alive`，`/readyz` 返回 `ready`，`/metrics` 返回 `watch_mcp_ready 1`。
- MCP 协议实测：initialize 成功，24 个工具全部为 `watch_*`；静态 Resource 4 个、模板 Resource 4 个；真实 `watch_get_status` 调用成功，`watch://status` Resource 与工具读取同一手机状态。手机 API healthy，手表在线，当前训练为 `RUNNING + COMPLETED`。
- 当前候选产物：watch APK SHA-256 `2C6FD6FEAA58BB7F30A89A15D28742EF1895DDF542123E17701BB1AC86152943`，phone APK SHA-256 `F1FD58C2F5A641E476B805B3EE5B0D3920D4B806973CD61864562A343F172461`，MCP Wheel SHA-256 `766FD22E3E1D9A1396A529F79B8B783A2BFED56790ADEA2F5307E93F98F3EC27`。
- 当前 shell 非管理员，WinSW `PoyiWatchMcp`/`PoyiWatchTunnel` 服务安装、Windows 重启恢复和独立 ChatGPT Tunnel 绑定需在管理员 PowerShell 与已登录 ChatGPT 环境继续执行；本轮未把这些写成完成。
- 验证命令：`gradlew.bat :phone:testDebugUnitTest`、`gradlew.bat test lint :app:assembleDebug :phone:assembleDebug`、`uv run pyright`、`python -m pytest -q`、`ruff check src tests`、`git diff --check` 均通过。

## 2026-07-26：PoyiWatchMcp WinSW 服务安装与本机 MCP 读写验收

- 关联 `REQ-SYNC-003`、`REQ-SYNC-004`、`BUG-017`、`API-013`、`API-016` 至 `API-018`。
- 管理员安装脚本已安装 `PoyiWatchMcp` 与 `PoyiWatchTunnel`；统一 `PoyiPersonalMcpGateway` / `OpenAISecureMcpTunnel` 保持运行且未被复用。
- 现场发现 WinSW 旧安装保留 `NT SERVICE\PoyiWatchMcp` 服务账户，切换服务 XML 后 Windows 服务配置仍未更新；安装脚本新增升级兼容：安装后强制 `sc.exe config ... obj= LocalSystem`，并给 `SYSTEM` 授予数据目录与 token/tunnel 单文件读取权限。
- `PoyiWatchMcp` 已以 LocalSystem 运行，`GET /healthz` 为 `alive`，`GET /readyz` 为 `ready`。LocalSystem 下 mDNS 未首次发现小米手机，已写入经 `phoneDeviceId` 校验的运行时 endpoint 缓存；后续仍按身份校验，不把 IP 当设备身份。
- 通过 `127.0.0.1:8768/mcp` 完成本机 MCP 协议验收：`watch_get_status`、`watch_list_plans`、`watch_get_latest_sleep`、`watch://status` 均成功；手机 API healthy，手表 online，训练最终保持 `RUNNING + COMPLETED`。
- 写入与幂等验收：`watch_sync_plans` 使用同一 `requestId` 重放返回 duplicate；`watch_pause_workout` 首次执行成功，同一 `commandId` 第二次返回 duplicate；随后 `watch_resume_workout` 恢复训练，最终状态 `RUNNING`。
- `PoyiWatchTunnel` 服务已安装但未 provision。当前本机未发现 Watch 专属 `tunnel-id`、`runtime-key.dpapi`、tunnel-client profile、admin profile 实密钥或 `OPENAI_ADMIN_KEY`/`CONTROL_PLANE_API_KEY` 环境变量；ChatGPT 现有“步序运动”连接的固定 Tunnel 绑定仍需在连接设置里取得/更新 Watch 专属 Tunnel ID 与 Runtime Key 后继续，未创建第二个 ChatGPT 应用。

## 决策记录

| ID | 决策 | 原因 | 后果 |
| --- | --- | --- | --- |
| ADR-001 | `WorkoutService` 作为训练状态唯一所有者 | Activity 生命周期不适合长时训练 | UI 通过快照展示，状态恢复集中处理 |
| ADR-002 | 距离采用原生/GPS/步数分层降级 | 厂商能力和室内 GPS 均不稳定 | 必须显示来源并处理切换基线 |
| ADR-003 | 手机计划库作为多计划主数据源 | 手机上编辑效率更高、存储更适合复杂计划 | 同步需要 revision 和冲突规则 |
| ADR-004 | 局域网 mDNS + HTTP 作为当前传输 | 易部署且适合完整轨迹 | 仅适合受信网络，需后续协议加固 |
| ADR-005 | 厂商能力运行时探测而非按包版本猜测 | 服务存在不代表运动能力开放 | 每次固件变化都需重新验证 capabilities |
| ADR-006 | APK 通过 GitHub Release 分发，不进 Git | 避免仓库历史膨胀 | Release 必须记录哈希和构建类型 |
| ADR-007 | 睡眠使用 HealthKit Store 只读 API并保留原始 stage type | 权限边界稳定，避免依赖私有数据库及猜测未公开枚举 | 需系统授权；固件变化后复测字段单位和语义 |
| ADR-008 | 原始训练样本使用每会话追加文件，历史索引只存摘要 | 避免长训练反复序列化和整体重写大 JSON | 恢复、归档和删除必须处理目录级原子性 |
| ADR-009 | 计划完成状态与训练会话状态正交 | 达标不代表用户已经结束户外运动 | UI、检查点和控制 API 均需同时表达两个状态 |
| ADR-010 | BLE 必须先通过真机稳定性门禁再接入 SyncEngine | OWW221 后台和 GATT 角色能力尚无证据 | 未通过时继续发布可靠 LAN，不把 POC 宣称为功能 |
| ADR-011 | BLE 使用手机 Central、手表 Peripheral，LAN 降为批量加速 | OWW221 与 Xiaomi 真机已证明该角色可广播、订阅和双向分片 | 控制/计划/定位优先 BLE，历史/睡眠可走已验证 LAN |
| ADR-012 | WatchIntervals 使用独立 MCP Server 和独立 Tunnel | 业务、凭据、日志和故障域必须与其他项目隔离 | PersonalMcpGateway 不再是 Watch 运行依赖；手机 8766 成为唯一桌面业务门面 |

## 工作日志模板

```markdown
## YYYY-MM-DD：主题
- 目标/关联：REQ-*、BUG-*
- 改动：
- 决策及原因：
- 验证：命令、设备、用例、结果
- 产物：提交、APK、SHA-256、截图索引
- 遗留：新建或更新 BUG-*
```

### 2026-07-26 12:32:53 +08:00 Watch MCP 写工具参数兼容

- `watch_*` 写工具继续支持 snake_case，同时新增 `requestId`、`expectedRevision`、`commandId`、`expectedState`、`expiresAt` camelCase 别名，便于 ChatGPT 现有连接按用户验收字段调用。
- 已执行：`cd mcp; .\.venv\Scripts\python.exe -m pytest -q`、`uv run pyright`、`uv run ruff check src tests`、`git diff --check`、`.\gradlew.bat :app:assembleDebug :phone:assembleDebug`，均通过。
- 新 wheel：`mcp/dist/poyi_watch_mcp-0.20.0.dev0-py3-none-any.whl`，SHA-256 `437FFD62E814926131A2155C72A344D284F10369D24F58CE5265C061214B5099`。当前非提升 shell 不能重启 Windows 服务，已热更新 site-packages 文件；服务进程需提升权限重启后加载该兼容。

## 2026-07-26：Watch 专属 Tunnel 与 ChatGPT 真实端到端

- 关联 `REQ-SYNC-003` 至 `REQ-SYNC-005`、`BUG-017` 至 `BUG-019`、`API-013`、`API-016` 至 `API-018`。
- 创建 Watch 专属固定 Tunnel 和独立服务账号 Runtime Key；密钥立即转换为 DPAPI LocalMachine 密文，明文临时文件删除。`PoyiWatchMcp` 与 `PoyiWatchTunnel` 均以 LocalSystem/Automatic 运行，8768/8880 ready，Tunnel doctor 与 verify 通过。
- MCP 新增 OAuth Protected Resource 元数据；doctor 从 DPAPI 临时解密 Runtime Key 到进程环境并在 finally 清除，健康监听使用临时端口，避免与已运行实例冲突。
- 现有“步序运动”私人连接没有 MCP 端点编辑入口。删除旧对象后用同一名称重新建立私人开发连接并绑定 Watch Tunnel，全程未同时创建第二个同名应用，也未进行目录发布、组织/域名/身份认证。
- ChatGPT 扫描 24 个 `watch_*` 工具；真实 `watch_get_status`、`watch_list_plans`、`watch_get_latest_sleep` 成功。首次调用遇到手机业务服务离线，恢复应用前台服务后重试通过。
- ChatGPT 写入验收：`watch_sync_plans` 同 requestId 重放为 duplicate；pause 首次成功，以不同 requestId 重放同一 commandId 为 duplicate。最终状态回读为 `RUNNING + COMPLETED`。
- ChatGPT 请求 `watch://status` 返回 `Unknown resource`，而本机 `resources/read` 成功；Tunnel 未转发该 Resource 请求，登记 BUG-019。
- 现场发现 mDNS IPv6 被拼成缺少方括号的 URL并可能阻塞 IPv4；修复地址规范化、IPv4 优先、坏缓存跳过和 InvalidURL 容错，新增测试后 MCP pytest 12 项通过。
- 最终 MCP Wheel：`mcp/dist/poyi_watch_mcp-0.20.0.dev0-py3-none-any.whl`，SHA-256 `C0155E7B1545B7406D4DC66617CAFCF450A7D04DDB1B72FCA400053D5981A365`。

## 2026-07-26：0.20.0 原生配速、界面成熟度与后台链路可靠性

- 关联 `REQ-DATA-011`、`REQ-DATA-013`、`BUG-020` 至 `BUG-022`。

### 配速改用原生 GNSS 测速

- 原实现的当前速度完全由 10 秒距离窗口求得：反应滞后于实际用力变化，且定位抖动会直接反映成读数跳动。
- OWW221 固件的 HealthKit `OUTDOOR_RUN` 能力映射仍为空（见 `system-exercise-implementation.md`），因此本轮的「原生数据」指 GNSS 芯片自身的多普勒测速与 `TYPE_STEP_COUNTER`，而不是 HealthKit 运动会话。
- 新增 `SpeedFusion`：GNSS 速度为主源，距离窗口为备源，按 4 秒时间常数平滑，静止判定 0.5 m/s。不依赖 Android 类型，由 6 个纯 Java 用例覆盖优先级、过期回退、精度与异常值拦截、抖动阻尼、静止与格式化。
- 训练页主读数改为分钟/公里配速，同屏保留 km/h，并显示来源（卫星测速 / 轨迹推算 / 步数估算）。

### 界面与功耗

- 底色由 `RGB(7,9,10)` 改为纯黑：AMOLED 上这些像素不再点亮，同时深色对比更干净。
- 统一字号刻度（DISPLAY/TITLE/HEADLINE/BODY/LABEL/CAPTION），页码指示器由文本字形 `●○` 改为实际绘制的圆点。
- 训练页刷新由 2 Hz 降为 1 Hz；文本经 `Ui.setTextIfChanged` 写入，避免 `TextView.setText` 在内容相同时仍触发重排；轨迹图仅在轨迹页可见时重绘。
- 控制页区分主次：暂停为实心主按钮，结束改为同色调描边按钮，两个高饱和圆形不再等量争夺注意力。
- 首页在已完成配对后隐藏配对码，配对信息回归为一次性设置内容。

### 后台链路可靠性

- `WatchLanLocator`：把原本只存在于手机前台页面的 mDNS 发现搬到后台服务，校验 `deviceId` 后才替换已保存主机，并按在线 10 分钟、离线 1 分钟的节奏复查。
- `WatchConnectionManager`：构造时从持久化配对状态恢复 LAN，并允许独立于 BLE 结果验证 LAN，冷启动不再需要先等一次 BLE 超时。
- `PhonePlanBridgeService.serve()`：显式 `bind` + `SO_REUSEADDR`，失败按 1s→30s 退避重试并记录端口与异常，不再静默失效。
- `PhoneBootReceiver`：新增 `WATCHDOG` 动作与精确闹钟看门狗，取得投递时的临时白名单以绕过 Android 15 对后台启动前台服务的限制。

### 已执行验证

- `.\gradlew.bat :app:assembleDebug :phone:assembleDebug`、`:app:testDebugUnitTest`、`:phone:testDebugUnitTest` 全部通过。
- `mcp`：`.venv\Scripts\python.exe -m pytest -q` 12 项通过。
- 真机 MCP 全链路：`watch_get_status` 返回 `CONNECTED_BLE_LAN`、`lanAvailable=true`、`watch=online`；手机 Activity 销毁后仍由后台定位器重建 `host`。
- 真机控制链路：`watch_stop_workout` 首次 accepted（controlRevision 5→6），重复 `expected_state=running` 正确返回 `STATE_MISMATCH`，相同 requestId 重放返回 `duplicateRequest=true`。
- 真机恢复：`am crash` 后进程消失、8766 不可达；临时白名单下触发 `WATCHDOG` 广播后进程重建、`/v1/health` 恢复 401、看门狗重新挂起。

### 未覆盖风险

- 配速融合尚未在开阔户外做真实 GNSS 对比，室内无法产生有效多普勒样本。
- MIUI「自启动」为系统级开关，关闭时任何拉起路径都会失败，代码无法覆盖。
- 本轮未改动 BLE 安全配对与长时间门禁，`BUG-015`、`BUG-016` 仍开放。

## 2026-07-26：训练界面按运动仪表重做

- 用户反馈上一轮 UI 仅是抛光，整体仍不像成熟产品。本轮不再保留"居中堆文字"的骨架，按成熟运动手表的版式重做四个训练页与首页。
- 核心版式决策：
  - 数据页左对齐，配速为主读数（56 号窄体），单位挂在基线右侧；时间/距离/心率/步数一行一个语义色（黄/白/红/青），标签靠右灰阶，行高 41。
  - 新增 `Ui.numeral()`（Roboto Condensed Bold + `tnum` 等宽数字）与 `Ui.Ring`（圆帽弧线进度环）；阶段页用 198 环替代横向进度条，剩余值置于环心。
  - 控制页删去装饰标题，顶部改为实时摘要（状态/时间/距离/心率）；首页拆掉大卡片，计划名为唯一大标题，开始圆钮为唯一彩色焦点。
  - 删除各页"向左滑…"提示文案，仅保留圆点指示器；预备页就绪状态统一白色。
  - 心率无读数时训练页显示 `--`；文案化状态只出现在预备页。
- 真机截图核对：首页、预备页、核心数据页、阶段环、控制页、结束确认均按预期渲染；训练会话经"长按结束→确认"正常保存退出。
- `:app:testDebugUnitTest`、`:phone:testDebugUnitTest` 通过；`assembleDebug` 通过。
- 同轮补齐次级页面：历史列表（首页速览与完整页）改为距离优先行、时间戳右侧灰阶；详情摘要卡沿用训练页语义色（距离白/用时黄/步数青/心率红/配速绿）；`Ui.backButton()` 统一圆形返回按钮；计划页标题行与灰阶说明对齐新版式。真机核对通过，详情页暗色地图在真实瓦片上近黑底、注记可读。
- 未覆盖：StageEditorActivity 仍是旧版式（当前入口已弱化，编辑主要在手机端）；配速主读数的实跑效果仍待户外验证。


## 2026-07-26：以系统运动软件为基准的整体重构与双端功能调通

- 参考采集：真机截取 HeySports 主页、SportPrepareActivity 与 SportDetailActivity 运动中页面，量出贴边边距、顶栏（左标题+右白色大时钟）、超大黄色计时、数字+内联标签、大行距的版式规格，作为 `Ui.FIGURE_*`/`PAGE_MARGIN` 的依据。
- 手表界面：核心页完全转写系统版式（figureLine 数字+基线标签）；预备页对齐系统准备页（GPS 顶部居中、发光开始圆）；首页/预备页开始圆加径向辉光；步数移至阶段环页。
- 滑动逻辑：`WatchPagerLayout` 增加未阻尼手指跟踪 + 边缘 1/3 阻尼显示；第 0 页右滑越过阈值触发 `OnExitListener`。首页注册退出（右滑回表盘，真机验证 focus 变为 launcher）；训练页不注册（真机验证右滑只回弹不退出）。第一版把阻尼直接叠进累计量导致 280px 手指位移只剩 9px、且本机 fling 阈值偏高，改为虚拟位移 + 原始位移判定后通过。
- 手机功能调通（真机在线，未用模拟器备选）：同步走通 BLE 失败→LAN 兜底，状态「蓝牙连接 · LAN 加速」，当前安排 day1·减肥 与 10 条历史读回；睡眠 8 条系统记录正常；修复 BUG-023/024。
- 手表自愈：复现 OWW221 空闲回收导致 8765/mDNS/BLE 全部消失；`BootReceiver` 看门狗上线（BUG-025）。实测本机 ColorOS 静默丢弃第三方 `setInexactRepeating`（uid 不进 alarm 表），改用 `setExactAndAllowWhileIdle` 一次性自续后注册成功；`am force-stop` → WATCHDOG 广播 → 进程重建、`/v1/health` 401 恢复。
- 干扰处理：采样期间随心一听/focuslink 反复抢占前台，音乐应用临时 `pm disable-user` 后已恢复 `enabled`；测试流程改为每步校验 `mCurrentFocus` 再操作。
- 验证：`:app:testDebugUnitTest`、`:phone:testDebugUnitTest`、双模块 `assembleDebug`、MCP pytest 12 项、`git diff --check` 全部通过；手表五个页面与手机四个标签页真机截图核对。
- 未覆盖：pager 触感（阻尼系数/阈值）未经户外汗手实测；手表看门狗 15 分钟自续链依赖闹钟投递，deep doze 下的实际间隔未做整夜观测；手机聊天等第三方应用抢前台导致的采样中断与产品无关。


## 2026-07-26：专业跑者数据层与 Garmin 式数据屏

- 用户反馈：作为跑者，只有当前配速/距离/心率远远不够；参考 Apple Watch、Garmin、COROS。
- 差距确认：分段、爬升、最佳配速此前只在保存时由 600 点预览轨迹事后粗算，运动中一概没有。
- 新增 `LiveWorkoutStats`（纯 Java，7 项单测）：实时 1km 分段（活动时间口径，暂停不计；跨多边界循环补段；恢复后按已完成公里重建边界）、20s 滑窗步频（≥8s 跨度才出值，停下自然归零）、EMA(0.35)+2m 阈值累计爬升（下坡重置基线不累计）、1.036×65kg×km 千卡、平均/最高心率与 50-90% 五级区间。
- WorkoutService 集成：tick 喂步频窗、心率回调喂聚合、GPS 喂海拔、applyDistanceDelta 后检测分段并双震动；Snapshot 挂 `LiveView` 数据包；checkpoint 恢复后 `restore()` 对齐分段边界。
- UI：主数据页 = 大计时 + 当前/平均配速、距离/心率 2×2 网格 + `Ui.ZoneBar` 五区彩条（当前区间点亮、心率数字同区间色）；新增 2×3「更多数据」页；训练 pager 五页；每公里全屏圈卡 3 秒（首个 refresh 只同步计数，恢复会话不回放旧圈）。
- MCP：手表 `/v1/status` 新增 `workout` 实时块（经 `WorkoutService.liveWorkoutJson()` 静态句柄读取运行中服务）；真机全链验证 ChatGPT 侧可见 `state=RUNNING`、阶段 1/5、活动时长等实况。
- 验证：双模块单测（含 LiveWorkoutStats 7 项 + SpeedFusion 6 项）、assembleDebug、`git diff --check` 通过；主/次数据页、圈卡挂载真机截图核对；MCP watch_get_status 返回 workout 块。
- 未覆盖：分段圈卡与区间彩条的实跑表现（室内无法产生 1km 距离与真实心率区间）；步频对 OWW221 计步器节奏的匹配度待户外对比。

## 2026-07-26：移除手表端阶段编辑死代码

- 计划页按离线选择器重构（b084a96）后，`StageEditorActivity` 已无任何调用方，仅剩 Manifest 声明；产品方向是阶段编辑收敛到手机端与 MCP（REQ-PLAN-005/006）。
- 删除 `StageEditorActivity.java` 与 Manifest 声明；架构文档手表 UI 清单、需求场景表、REQ-PLAN-001/REQ-UI-001 验收口径同步去掉手表编辑页。
- 上一轮"StageEditorActivity 仍是旧版式"的遗留项就此关闭：不是翻新死界面，而是移除。

## 2026-07-26：手表时长进位与配速记法统一（BUG-026）

- 巡检发现三处专业性硬伤：手表端三个 Activity 各自持有 `mm:ss` 封顶的时长格式化——75 分钟长跑主计时显示 `75:32`（手机端同场训练已正确显示 `1:15:32`）；历史详情配速 `05:32/km` 与训练页 `5'32"` 记法割裂；累计爬升把 `optDouble` 原始小数直接拼进界面。
- 新增纯 Java `Format`（`duration` 超时进位 `h:mm:ss`、`distance`），与 `SpeedFusion.formatPace` 同理由保持 android-free 可上 JVM 单测；三个 Activity 的私有副本删除。
- 手表历史配速（平均/最佳/分段）统一 `SpeedFusion.formatPace`；1 公里分段的 `/km` 后缀属冗余信息，删除；爬升取整米。
- 新增 `FormatTest`（5 组用例：进位边界、钳制、距离小数）；`:app:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 未覆盖：`1:15:32` 七字符在训练页 52dp 主计时与圆环中心 48dp 的实际渲染宽度待真机截图核对（手表当前离线）。

## 2026-07-26：计划阶段行结构化与数字字形收尾

- 巡检确认阶段列表是仪表重做后仅剩的"三字段拼一句"元素：首页第三屏与计划页详情里 `1   跑步   1000米` 纯文字行，无层级、无阶段语义色。
- 新增 `Ui.stageRow()`：阶段色竖条（沿用 `Ui.stageColor` 跑步绿/快走青/休息黄）+ 灰阶序号 + 粗体名称 + 右对齐 `Ui.numeral` 目标值；两处调用点共用，背景色由调用方按所在容器指定（计划页卡内 PANEL_ACTIVE、首页黑底 PANEL）。
- 预备页倒计时 3-2-1-GO 由普通粗体换成 `Ui.numeral` 窄体等宽字形，训练相关数字全部同一字面。
- `:app:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 未覆盖：手表当前 ADB 离线（USB/TCP/mDNS 均不可达），stageRow 竖条高度、序号列宽与目标值混排（如"1分30秒"）的真机渲染待手表上线后截图核对。

## 2026-07-26：历史页按跑者日志口径重排信息

- 列表行（HistoryActivity 完整页 + 首页历史速览）的灰阶次要行由「用时 · 步数 · 心率」改为「用时 · 平均配速 · 心率」：跑步产品的历史按配速扫读（Garmin/Apple 跑步列表均不放步数）；无距离场次回退显示步数，步数完整数据仍在详情页。
- 平均配速直接以 `durationMs / distanceMeters`（毫秒/米在数值上等于秒/公里）喂 `SpeedFusion.formatPace`，与训练页同记法同口径（durationMs 为活动时间）。
- 详情分段卡两遍渲染：先找最快段（paceSecondsPerKm 最小且 >0），渲染时对该行值文字用 LIME 高亮；仅一段时不高亮（没有比较意义）。`detailLine` 增加值颜色重载。
- `:app:assembleDebug`、`:app:testDebugUnitTest` 通过（class 时间戳核对确认增量编译包含改动）。
- 未覆盖：真机渲染核对随手表上线一并补做。

## 2026-07-26：手机端格式与数据行整改（BUG-027）

- 巡检延伸到手机模块，确认三处硬伤：`HistoryDetailActivity.dataLine` 用 38 个硬编码空格分隔标签与值（伪两列，字号一变即错位）；「运动表现」「公里分段」卡用 formatDuration 拼配速（`05:32 /公里`）与同屏概览卡 `5:32 /公里` 记法割裂；爬升拼原始 double。睡眠列表整晚时长用秒表记法 `7:12:00`。
- 新增纯 Java `PhoneFormat`（duration/distance/pace/paceSeconds/minutesHuman）+ `PhoneFormatTest`；两个 Activity 私有格式化副本删除；`dataLine` 改为标签弹性宽度 + 值加粗右对齐的真两列；睡眠总长/深睡/REM 改「7小时12分」。
- 记法口径明确：手机说中文单位（`公里`、`5:32 /公里`），手表说仪表语言（`km`、`5'32"`）——伴侣文本与表盘读数是两种表面，各自内部一致。
- `:phone:assembleDebug`、`:phone:testDebugUnitTest` 通过。
- 未覆盖：平板与手表当前均 ADB 离线，真机渲染核对（尤其 dataLine 两列在窄屏的换行表现）待设备恢复。

## 2026-07-26：小米真机验证与定位中继启动崩溃修复（BUG-028）

- 设备恢复：OWW221 经 USB 重新武装网络 ADB（watch-link 脚本，192.168.1.44:5555）；伴侣端换用小米 22041216C（xaga、targetSDK 35 门禁更严），替代此前的华为平板。
- 首装即抓到 P1：应用启动后前台被其他应用抢占，同步成功回调迟到触发 `ensureLocationRelay` → location 类型 FGS 后台启动被系统拒绝 → `PhoneLocationRelayService.onCreate` 的 `startForeground` 抛 SecurityException，进程 FATAL。华为平板此前未复现（权限已授予且回调到达时仍在前台）。
- 修复：Activity `foreground` 标记门禁 + `startForegroundService` 兜底；服务侧 `startForeground` try/catch 后 `stopSelf()`。下次前台同步自动重试，不再带崩进程。
- 小米真机核对（截图 verify-0200-phone-*.png，临时文件不入库）：启动稳定驻留前台、`AndroidRuntime:E` 清零；「已完成安全配对 · 蓝牙连接 · LAN 加速」；历史 13 条读回，列表行与详情「时间与速度」卡两列排版正确（标签左、加粗值右）；睡眠 8 条，人读时长「5小时15分/深睡 1小时8分/30分/24分」各形态正确（BUG-027 视觉确认）。
- 手表侧界面核对受阻：插线充电时 heytap SysUI 充电覆盖层（DISPLAY_OVERLAY）常驻抢占输入，注入手势/按键均无法退出，待拔线后继续。

## 2026-07-26：OWW221 真机全屏幕核对通过

- 充电覆盖层阻塞的解法：heytap SysUI 充电层（DISPLAY_OVERLAY）在充电期间常驻且吃掉全部输入，注入手势/物理键码均不可退出；`dumpsys battery unplug` 模拟拔电后立即消失，核对完成后 `dumpsys battery reset` 恢复。期间 focuslink 两次抢占前台，按既有流程每步校验 `mCurrentFocus` 后重拉。
- 本轮改动逐项核对（截图临时文件已清理，不入库）：
  - 首页第三屏与计划页安排详情的 `Ui.stageRow`：跑步绿/快走青竖条、灰阶序号、右对齐等宽目标数字，两处容器背景层次正确。
  - 历史速览/完整列表：距离主读数 + 「用时 · 步数」回退分支正确（真机记录全部为室内 0 距离，配速分支暂无数据）。
  - 历史详情：摘要卡语义色、无数据 `--` 占位、缺失数据卡按需隐藏、暗色地图占位、删除按钮。
  - 预备页倒计时窄体数字字形生效；四项就绪状态配色正确。
  - 完整训练流程冒烟：开始→倒计时→主数据屏（黄大计时/双配速/2×2/五区彩条/五页点）→控制页（实时摘要、实心暂停/描边结束）→长按结束→确认→保存回首页。
- 待户外实跑：最快分段绿色高亮、历史配速 `5'32"` 记法、有距离列表行配速、`1:15:32` 七字符在 52dp 主计时的实际渲染（按 condensed 字形宽度推算 ≈180dp，行宽 350dp，无裁切风险）。

## 2026-07-26：合成长跑注入验证户外依赖 UI，并修复详情页双路径缺陷（BUG-029）

- 方法：debug 包经 `run-as` 向 `files/workouts/synthetic-ui-check-0200/` 注入合成 summary/route.ndjson/heart.ndjson（10.2 km / 活动 75:32 / 每公里配速 7'46"→6'51"→7'53" 工程化、第 5 公里最快、爬升 36 m、心率 128–171），`reconcile()` 自动收录——所有"待户外验证"的展示逻辑用真实渲染路径核对，不必等一场户外跑。数据明确标注合成、验证后删除。
- 验证通过：历史列表行 `1:15:32 · 7'24" · 157 bpm`（h:mm:ss 进位 + 配速分支 + 心率，同屏 0 距离行走步数回退对照）；详情摘要卡 `1:15:32` 黄 / `7'24"` 绿；分段卡 11 行 `07:45 · 7'46"` 格式；**第 5 公里整行 LIME 高亮**；最佳瞬时配速 `6'46"`；爬升 `36 m` 整数；心率范围 `128–171 bpm`。
- 注入过程暴露 BUG-029 两项：列表路径详情缺全部派生卡片（摘要对象无样本，与 `find()` 路径割裂——此前室内 0 米记录本就无卡，一直被掩盖）；`detailLine` 值列 180dp 固定宽把标签截成「10 公…」「实测…」。修复后列表路径重验：卡片齐全、标签完整。
- 训练页 52dp 主计时的 `1:15:32` 渲染仍未直接观测（需实跑 1 小时），但同字形在摘要卡 17dp 与列表 22dp 无裁切，且行宽余量按字形宽度推算 ≈170dp，风险关闭。
- 收尾：合成目录已删、`dumpsys battery reset` 恢复、应用重启重建索引。

## 2026-07-26：BLE 恢复矩阵夜间补测（BUG-016 范围收窄）

- BLE-005 手机半场：shell 关闭小米蓝牙→手表侧转 DISCONNECTED；重开→12 秒内 CONNECTED，无重新配对。手表半场受阻：OWW221 构建不实现 shell 蓝牙开关、设置页无开关控件，不冒险手动盲操作用户日常设备，留待人工。
- BLE-003：手表 Activity 关闭后 8765 门禁存活（401）；手机 force-stop 后 shell WATCHDOG 广播拉起进程，RCVR 态 FGS 启动按设计被拒（PhoneBootReceiver 自捕获 W 日志），完整恢复依赖 15 分钟精确闹钟白名单豁免——闹钟已确认挂起（dumpsys alarm u0a325），投递后结果另记。
- BLE-004（双端重启）不在无人值守下执行：重启会同时切断手表网络 ADB 与小米无线调试，失去取证通道。BLE-009/010 需要鉴权链路与非充电长时段，均留待专场。
- BUG-015 复核：密码学层（ECDH 配对、HMAC 挑战、AES-GCM、防重放）已有真机证据，遗留仅为解除配对 UX 与 CompanionDeviceManager 关联两项增强，降级为后续增强项处理。

## 2026-07-26：0.21.0 开篇——手表主页信息架构重构（REQ-UI-005）

- 用户反馈整体界面逻辑需要重构而非小修：旧主页是三屏横向 pager，第二、三屏是 HistoryActivity 与 PlanActivity 的缩水速览副本——同一目的地两套导航模型，速览永远滞后于正式界面，左右滑动语义也被 pager 占用。
- 重构原则「每个目的地只有一个规范界面」：主页改为单一纵向信息流——顶栏时钟 / 本周量条 / 发光开始钮 / 当前安排块（整块可点进计划选择）/ 最近训练块（点击直达详情）/ 全部历史入口 / 异常态传感器行。速览页与 `renderPagerPages` 全部删除，MainActivity 从 307 行横向导航壳变为纯内容主页。
- 新增 `WeeklyStats` 纯类（周一 00:00 中国周口径、全零记录不计入周量）+ 4 项 JVM 单测，主页「本周」条是跑者在两次训练之间打开应用最想看的数字，也是本次重构的信息增量。
- 手势语义收敛：主页右滑退出、左滑历史（沿袭旧 pager 肌肉记忆）；HistoryActivity 右滑改为返回，删除旧 pager 时代横跳计划页的残留语义。
- 双端版本号升 0.21.0；`:app:assembleDebug`、`:app:testDebugUnitTest`（含 WeeklyStatsTest 4 项）通过。真机核对推迟到用户不在场时段与图标、历史清理一并执行。

## 2026-07-26：双端图标重绘

- 旧图标是深底上一条模糊的轨迹涂鸦加两个点，小尺寸下不可辨识，也与产品的仪表语言无关。
- 新图标：#0E1113 深底 + 三段 100° 圆弧环（阶段色跑绿 #BEFF47 / 走青 #53DAE5 / 休黄 #FFB742，圆帽、20° 间隔）+ 中心黄色启动三角——「间歇 = 分段循环 + 开始」的视觉直译，与应用内 `Ui.stageColor`、阶段环完全同源。
- 实现：`mipmap-anydpi-v26` 自适应图标（前景矢量置于 66dp 安全圆内，双端 minSdk ≥26 全设备生效）；`drawable/ic_launcher` 以同设计重绘 48 视口版本，继续服务通知小图标引用。应用名「步序」按要求不动。
- 双端 assembleDebug 通过；启动器实际渲染随真机核对一并确认。

## 2026-07-26：手机端界面骨架重构与实时训练遥控

- 旧结构三宗罪：配对表单卡永久霸占首屏（配对完成后它只是杂音）；「标签」是埋在滚动流里的四颗按钮，随内容滚走；训练控制是四个对手表状态一无所知的裸按钮，按错即得 STATE_MISMATCH。
- 新骨架：固定头部（标题 + 一行连接状态：彩色状态点 + 文案 + 「连接设置 ▾」，点击展开/收起设置面板，已配对默认收起）→ 四个内容 ScrollView（FrameLayout 切换）→ 固定底部导航（计划/训练/历史/睡眠，选中态深色药丸）。内容在导航之下独立滚动，导航永不漂移。
- 训练页重构为实时遥控（REQ-DATA-015 的手机端应用）：前台且停留在训练页时每 5 秒经 `WatchConnectionManager` 读 `/v1/status`，渲染 workout 实况（状态行、tnum 大计时、距离·配速·心率·阶段 meta 行）；操作按钮按状态生成——RUNNING=暂停/结束、PAUSED=继续/结束、PREPARING=结束准备、空闲=开始训练。轮询带 in-flight 防堆积，离开页面/退后台即停。
- 连接状态点色由 `ConnectionState` 驱动：BLE 绿、纯 LAN 青、过渡态琥珀、蓝牙关/未配对红、默认灰。
- `:phone:assembleDebug`、`:phone:testDebugUnitTest` 通过；真机核对随统一设备窗口执行。

## 2026-07-26：0.21.0 真机落地——主页重构验证、全零记录清理、双端安装

- 历史清理（用户指令）：先经 `run-as tar` 流式全量备份到本地 `backups/watch-workouts-20260726/`（58 条目 173KB，已加入 .gitignore 不入库）；分析备份得 14 条记录中 12 条距离与步数双零（纯测试残留），逐目录删除，保留 2 条真实数据（244 m/340 步、2.43 km/1426 步）。后续测试记录测完即删并保留备份成为固定纪律。
- 手表 0.21.0 真机核对：新纵向主页完整渲染——顶栏时钟、「本周 2.67 km · 2 次 · 34:40」（恰为保留两条真实记录之和，周统计与清理互相印证）、状态行、发光开始钮、当前安排块（day1 + 阶段预览）、最近训练块（2.43 km · 7月25日 20:00 · 30:00 · 12'22"，配速分支正确）、全部历史行；点击最近训练直达该记录详情（find() 全量路径，摘要卡/轨迹图完整）。
- 双端 0.21.0 已安装（手表 + 小米）。手机端底部导航/实时遥控与两端启动器新图标的视觉核对推迟：核对时用户正在手机上使用聊天应用，按「在场不测」约定停止手机屏幕操作，待空闲窗口补截图。
- 删表 shell 两个工程坑记录在案：Windows Python 写出的 id 清单带 `\r` 导致 `rm` 目标名不存在而静默落空；`while read` 循环里 `adb shell` 吞掉循环 stdin 只执行首条——改 `tr -d '\r'` + `for` 循环后 12 条全部删除。

## 2026-07-26：BLE-003 闹钟恢复检查——证据不完整，如实按未证记录

- 定时检查点执行时发现实验窗口已被自己破坏：21:53 安装 0.21.0（install -r 附带 force-stop）终止了实验。21:40 闹钟投递窗口的 logcat 被 AppsFilter 噪声轮转覆盖，无直接投递记录。
- 保留证据：21:53 安装日志显示系统强停一个正在运行的 `PhonePlanBridgeService`；但 8766 宿主 `PhoneCompanionService` 从 21:28 基线到重装始终未出现。无法区分"闹钟部分恢复"与"ServiceRecord 残留"，结论按未证处理。
- 连带发现：install -r 的 force-stop 会取消看门狗闹钟——重装后手机处于无服务、无闹钟的冷状态，需一次应用启动重新武装自愈链（当前手机即处于该状态，用户在场未代为启动）。
- 重跑方案：force-stop → 15 分钟内零触碰 → 闹钟窗口立即抓 logcat + dumpsys activity services。

## 2026-07-26：主页纵向重构被否决——回滚交互逻辑，方向修正为视觉重做

- 用户裁定：旧的三屏横向翻页界面逻辑没有问题，问题一直是「UI 丑」；把力气花在信息架构重排是方向性错误（且是第二次没对准——第一次只做排版抛光被嫌变化小）。结论记入 REQ-UI-005：交互形态已定，此后的改版仅限视觉层。
- 回滚：MainActivity 恢复 3e6a4c6 之前的翻页版本（保留其后的 Format/stageRow/跑者行等一切非结构改进）；HistoryActivity 恢复横滑去计划页的旧语义。
- 保留并融入旧版式的增量：本周训练量一行小字（numeral 字面、灰阶）加在信息块尾部；开始圆（主页+预备页）换黄→橙渐变填充（Ui.gradientOvalAction），体锻风格的克制版——用户明示手表端审美放松、大改留给手机端。
- 视觉大方向经用户选定：Apple Watch 体锻风；手机端为主战场（彻底重构视觉，底部导航位置保留）。
- `:app:assembleDebug`、`:app:testDebugUnitTest` 通过。

## 2026-07-26：手机端体锻深色视觉系统落地

- 方向由用户选定：Apple Watch 体锻风，主战场手机端（底部导航位置被认可，观感被否）。新增 `Palette` 设计令牌（纯黑底/#1C1C1E 卡/#2C2C2E 高层/白字/灰字 + move 红粉、exercise 亮绿、stand 青、黄、橙、红与五个深色调和填充），全量替换两个 Activity 里 30+ 处内联颜色：鲜艳填充配黑字（同步/继续）、新建主钮用 move 红粉、删除统一深红底红字、三个添加阶段钮用各自阶段色的深调和底+亮色字、输入框深色+自定义 hint 色、列表行升一级用 CARD_HIGH 与卡片分层、导航选中态红粉字+软药丸。
- 新增 `ActivityRing`（SweepGradient 渐变弧、圆帽、暗色轨道、十二点起笔）：训练遥控卡重排为环心嵌活动计时的体锻式主视觉，环随计划阶段完成填充（红粉→橙），空闲只显暗轨。
- 手表端同轮完成回滚+克制提升（见上一条目）。`:phone:assembleDebug`、`:phone:testDebugUnitTest` 通过。

## 2026-07-26：接管后的手机视觉层级二次重构（REQ-UI-006、BUG-030）

- 在 Pixel 6 / API 35 模拟器复现首个 0.21.0 候选：虽然调色已转为体锻深色，但仍是旧表单的颜色替换——顶层大卡套计划组卡、所有操作大面积填充、纯文字底栏与重选中药丸，主次层级仍混乱。
- 保留用户认可的四目的地底栏、计划分组与安排编辑流程，只重做视觉组织：顶层内容改为无底板页面，统一大标题/一句说明；当前手表安排收敛成状态条；“新建计划”改为圆形主入口；计划组选中态从整块亮绿改为深色底+亮绿细描边；添加、编辑、删除分为强调、次要和透明危险操作。
- 底栏改为图形+文字双编码，选中态只使用能量红粉文字，不再加大块药丸；训练进度环进入独立英雄卡。计划编辑器修复返回按钮与“编辑安排”标题挤叠。
- 修复 `BUG-030`：状态读取失败时不再保留高亮“开始训练”，改为“打开连接设置”。模拟器完成设置展开/收起、计划列表、编辑器与断连训练页截图预检；真机视觉确认仍待用户空闲窗口。
