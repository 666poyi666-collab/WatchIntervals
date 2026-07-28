# 缺陷与技术债台账

状态：维护中  
基线：2026-07-28

严重度：P0 数据损坏/训练核心不可用；P1 核心行为错误或高风险；P2 有降级路径；P3 体验或维护问题。状态使用 `Open`、`In Progress`、`Fixed`、`Verified`、`Won't Fix`。

## 1. 开放项

### BUG-001：自动化测试覆盖仍不完整

- 状态：In Progress
- 严重度：P1
- 影响：所有当前版本
- 现象：Phone 已有加密 V2、恢复包和 schema 迁移 JVM 测试；Watch `app` 核心训练状态、传感器和数据迁移仍缺自动化，Android Keystore/WorkManager 也未做真机或 instrumentation 验证。
- 风险：传感器切换、暂停、恢复和历史 schema 修改容易产生回归。
- 处理：按 `testing.md` 的建议清单继续建立 Watch 纯 Java、Robolectric/仪器、WorkManager/Keystore 和 API 契约测试。
- 关闭条件：核心状态机、编解码和协议在 CI 中自动执行。

### BUG-002：pause/resume API 实际采用 toggle，调用不幂等

- 状态：Open
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

- 状态：Open
- 严重度：P2
- 影响：新开发环境、CI
- 现象：README 使用 `gradle`，但仓库没有 `gradlew` 和 `gradle/wrapper`；依赖本机安装或缓存的 8.14.3。
- 处理：使用 8.14.3 生成 wrapper，提交 wrapper 配置和校验后的脚本。
- 关闭条件：全新环境可执行 `./gradlew :app:assembleDebug :phone:assembleDebug`。

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

### BUG-010：加密 V2 同步缺少发布级环境验收

- 状态：Open
- 严重度：P1（只阻断 V2 候选发布，不影响当前局域网主流程）
- 影响：未发布 Phone 加密 V2 同步候选
- 现象：AES-GCM/outbox/cursor/ACK、schema 3 tombstone/projection、Keystore 包装和 WorkManager 已有 JVM/构建门禁，但本阶段明确不含配置 UI、staging、Android Keystore 真机、Doze/重启和三轮 PC-off 验收。
- 风险：不能证明 OEM Keystore 生命周期、后台调度恢复、设备吊销后的真机终态，以及电脑关闭时的端到端收敛。
- 处理：在独立发布阶段补 provisioning surface，并执行 `testing.md` 的 SYNC-006 至 SYNC-010；保留 `supportsPcOff=false` 等价发布声明直至全部通过。
- 关闭条件：staging 合同、恢复/批准、token revoke、Doze/重启及三轮 PC-off 均有脱敏证据，且用户明确接受开放问题。

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
