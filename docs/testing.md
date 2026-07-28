# 测试与发布门禁

状态：维护中  
基线：2026-07-28

## 1. 测试原则

本项目的高风险区域不是页面能否打开，而是长时间状态、传感器切换、后台运行和跨设备同步。测试分为纯逻辑、Android 集成、模拟器界面、手表真机、手机真机和 MCP 契约六层。Phone 已有加密 V2/恢复包/schema JVM 测试，Watch 核心与 Android 生命周期覆盖仍不足，继续关联 `BUG-001`。

## 2. 每次提交最小检查

```powershell
gradle :app:assembleDebug :phone:assembleDebug
git diff --check
```

- 修改阶段/计划：验证 JSON 新旧数据读取、空计划回退、目标边界。
- 修改训练引擎：验证开始、暂停、继续、停止、自动阶段推进、完成只保存一次。
- 修改传感器：验证 GPS、步数、心率各自单独可用和切换恢复。
- 修改 API/MCP：验证 401、错误 JSON、超时、手机不在线、手表不在线和正常路径。
- 修改加密 V2：验证 canonical cursor、safe-integer revision、AES-GCM AAD、outcome 全覆盖、ACK/outbox/cursor 原子提交、conflict 双方留存、显式 tombstone/projection 重放和 401 terminal retry。
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

### 加密 V2 同步

| ID | 层级 | 验证 |
| --- | --- | --- |
| SYNC-001 | JVM | stable JSON/AAD、AES-256-GCM 往返及错误 AAD 拒绝 |
| SYNC-002 | JVM | cursor 只接受 canonical `c<base36>`，revision 接受 JS safe integer long 且拒绝小数/溢出 |
| SYNC-003 | JVM | 每条 leased mutation 必须 exactly-one ACK/conflict；缺 outcome 不推进 cursor |
| SYNC-004 | JVM | delete ACK 与 projection 待办同一 state commit；schema 2 迁移为空 tombstone，只有显式缺席 ID 生成 delete；remote delete 必须命中 root 加密 metadata ledger |
| SYNC-005 | JVM | conflict 保留本地候选与解密远端候选，不能自动覆盖手机计划库；训练 projection 使用 allowlist 排除 route/逐点心率/未知字段并散列 envelope ID |
| SYNC-006 | staging | `/sync/v2/exchange` 合同、分页、重放、revision conflict、workout immutable、401 revoke |
| SYNC-007 | 真机 | Keystore 首装/升级/锁屏/失效、恢复包、设备批准、不同 root/deviceId 隔离 |
| SYNC-008 | 真机 | WorkManager 在断网、Doze、重启和进程回收后 catch-up；前台与 Worker 不并发覆盖 state |
| SYNC-009 | PC-off | 电脑关闭期间手机产生计划/训练摘要，恢复网络后 exactly-once 收敛；连续执行三轮 |
| SYNC-010 | 安全 | APK/repo secret scan、Auto Backup/device-transfer 排除、401 后 token 仅在持久清除成功时终止 retry |

本轮收敛只允许执行 JVM、lint、APK 构建和静态安全检查，不部署、不使用 ADB/真机，也不启动云端服务；因此 SYNC-006 至 SYNC-010 中需要 staging/真机/PC-off 的部分保持未覆盖并关联 `BUG-010`。

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

## 6. 建议优先补齐的自动测试

1. `Stage`/`PlanStore` 编解码、默认计划和法特莱克识别。
2. `WorkoutRecord` schema 1/2 兼容和统计计算。
3. 距离来源切换、GPS 过滤、step counter 基线和阶段跨越算法。
4. 暂停/恢复/完成状态机和“历史只保存一次”。
5. `PlanLibraryStore`/`PhonePlanLibrary` 迁移、revision、选择与删除。
6. 手表 8765 与手机 8766 的协议契约测试。

## 7. 发布门禁

发布 APK 前必须全部满足：

- 两个模块从干净构建成功，无新增编译警告。
- P0/P1 开放缺陷为 0；例外必须在 Release notes 明示并由维护者接受。
- 与改动相关的真机和 API 用例通过，结果记录到 `project-log.md`。
- `versionCode`、`versionName` 与 CHANGELOG 一致。
- APK 使用预期签名；debug 包标记为 prerelease。
- 计算并记录两个 APK 的 SHA-256。
- `git status` 干净，Release 指向已推送提交。
