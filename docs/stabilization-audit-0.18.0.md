# 0.18.0 稳定化审计

状态：内部测试候选，不可正式发布  
审计日期：2026-07-25  
冻结分支：`feat/0.18.0-stabilization`  
冻结提交：`fe1fefbcc8d499e26d51e51412ed1e9a65bd3c90`

## 1. 冻结证据

| 项目 | 结果 |
| --- | --- |
| 手表版本 | `0.18.0`，versionCode 28，debug |
| 手机版本 | `0.11.0`，versionCode 12，debug |
| MCP 版本 | `0.6.0` |
| 冻结提交 | 42 个文件，1869 行新增、260 行删除 |
| APK | `dist/0.18.0-debug/`，被 Git 忽略 |
| 手表 APK | 9,432,142 bytes；SHA-256 `8DA31F39A1172DB4C43CADE5ED2187C0BE49B7BD4487644ACF6882CD75CE4F44` |
| 手机 APK | 958,597 bytes；SHA-256 `72A0A14E0BB04869E51A4E356941AC6604F8F5BFAAB8CBC7EA6C07DA845CE15D` |

本地执行并通过：

```powershell
.\gradlew.bat test lint :app:assembleDebug :phone:assembleDebug
python -m unittest discover -s mcp\tests -v
powershell -ExecutionPolicy Bypass -File mcp\tests\test_persistent_tunnel.ps1
git diff --check
```

手表 JVM 测试现为 7 项、MCP 测试 10 项通过；Gateway `/healthz` 和 MCP `initialize` 本地烟雾通过。GitHub Actions run `30164226710` 已完整通过并上传 APK 与报告产物。

## 2. NDJSON 与历史审计

已确认：

- 文件位于应用私有 `files/active_workouts` 和 `files/workouts`，未使用公共存储。
- route/heart 使用 `FileOutputStream(file, true)` 和 `BufferedWriter` 真正追加。
- 活跃写入约每 5 秒 flush、每 15 秒 fsync；暂停、计划完成、检查点强制保存和结束 close 会强制同步。
- 读取逐行解析，损坏行被跳过；损坏尾行不会使整条训练不可读。
- summary 使用临时文件、fsync 和替换；活动目录移动成功后才删除 checkpoint，失败时保留可恢复目录。
- 删除历史和淘汰第 201 条记录会删除对应目录；启动 reconcile 重建、排序并裁剪摘要索引。
- 旧 `workout_history.json` 逐条迁移，首次成功迁移保留备份，下次启动删除。

后续修正：

- checkpoint 的 route/heart offset 已改为权威提交边界；恢复累计值前截断 offset 后的完整或损坏尾行，避免样本与统计不一致，见 `BUG-012`。
- 有效行计数改为只统计可解析 JSON 行，损坏行不再抬高 `routePointCount`。
- 修复版已通过 USB `install -r` 覆盖至 OWW221，应用数据保留，网络 ADB 在安装后仍在线。
- 尚无真实进程终止、文件中断和 7200/14400 个 Location 文件样本测试；现有 14400 测试只覆盖指标增量守恒。

## 3. 历史 API 审计

- `GET /v1/history` 只读取最多 200 条摘要索引，不读取 route/heart NDJSON。
- 详情只在指定记录时读取，轨迹简化为最多 1000 点并返回原始点数和截断标记。
- route/heart 使用不可变历史记录内的整数样本 cursor，记录归档后新增历史不会造成当前记录分页漂移。
- 当前每页仍解析整份样本文件，不是真正流式分页，见 `BUG-013`。

## 4. Outbox 能力边界

| 能力 | 当前状态 |
| --- | --- |
| 持久 operationId | 已实现，SharedPreferences commit |
| ACK 后清理 | 已实现，`applied`/`already_applied` 清理 |
| 重复去重 | 已实现，手表保留有限 operationId 结果 |
| library revision 冲突 | 已实现，旧 revision 返回 conflict |
| 重启恢复 pending | 已实现 |
| 完整库快照压缩 | 已实现，最新快照替代旧 pending 快照 |
| MCP 手表回读 | 已实现；pending 不返回 verified success |
| per-plan revision/operation | 未实现 |
| tombstone | 未实现；delete 仍携带删除后的完整库 |
| 自动退避/死信 | 未实现 |
| 普通手机 UI 回读验证 | 未实现 |

因此 2B 只能标记为基础链路，见 `BUG-014`。

## 5. Lint 分类

本次 lint 为 94 条 Warning、0 Error。没有 `MissingPermission`、`ForegroundServiceType`、`NewApi` 或 `BatteryLife` 项。由于没有 main 分支同工具链基线，无法严谨判断哪些是本次新增；“改动文件”仅表示警告所在文件被修改，不等于警告由本次产生。

| ID | 数量 | 位于改动文件 | 风险判断 |
| --- | ---: | ---: | --- |
| SetTextI18n | 54 | 34 | 国际化/维护，稳定化后整理 |
| DiscouragedApi | 9 | 9 | UI/平台 API，真机回归时核查 |
| LockedOrientationActivity | 8 | 8 | 手表/手机固定方向设计，低风险 |
| RtlHardcoded | 7 | 3 | RTL 适配，当前中文界面可暂缓 |
| ApplySharedPref | 3 | 至少 1 | outbox 有意使用同步 commit 保证落盘，其余后续审计 |
| ClickableViewAccessibility | 3 | 0 | 既有可访问性债务 |
| PrivateApi | 2 | 0 | 厂商桥接，高风险但有运行时降级 |
| UnsafeProtectedBroadcastReceiver | 2 | 0 | 既有接收器，需后续权限审计 |
| 其他单项 | 6 | 混合 | InternalInset、OldTargetApi、UnusedAttribute 等 |

## 6. 真机与网络门禁

初次审计时没有 OWW221。随后完成旧 APK/私有数据备份，通过网络 ADB 从 0.17.0 覆盖升级，并完成 15 秒计划、自由记录、暂停恢复、幂等结束、旧历史迁移、覆盖安装恢复和四页 UI 短测。详细证据见 `testing.md` 的“0.18.0 OWW221 短测证据”。

仍需按顺序完成：

1. 普通进程 kill/crash 与用户 force-stop 分开验证；覆盖安装恢复已通过。
2. 10–15 分钟户外预试；378×496 四页室内截图已通过。
3. 三次 30–60 分钟：手表独立、手机辅助、网络中断。
4. 手机/手表 IP 变化、路由器重启、Windows 睡眠恢复和连续状态调用。
5. 真实 ChatGPT Secure MCP Tunnel 绑定与分层离线错误。
6. BLE 息屏、后台、双端重启、重连和 12 小时门禁；通过前不接入 SyncEngine。

正式签名、GitHub Release 和合入 main 仍须等待户外、网络、Tunnel 和相关真机门禁完成。
