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
