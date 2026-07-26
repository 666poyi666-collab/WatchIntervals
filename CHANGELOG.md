# Changelog

本文件记录用户可感知的版本变化，格式参考 Keep a Changelog。手表端和手机端版本独立，在标题中分别标明。

## [Watch 0.19.0 / Phone 0.19.0] - 2026-07-26

### Added

- 手机 Central/GATT Client 与 OWW221 Peripheral/GATT Server 正式替代 debug ping/pong POC，支持分片 request/response、MTU、CCCD indication、自动重连和连接状态。
- 计划 outbox、计划回读与手机定位中继接入 BLE；LAN 保留为历史/睡眠等批量数据的加速链路。
- 手表 BLE 与 8765 LAN 使用共享 `WatchCommandRouter`；手机业务统一经 `WatchConnectionManager` 选择传输。

### Changed

- 手机主页面隐藏手工 IP，BLE 连上后自动同步；IP 仅作为后台 LAN 发现和加速端点。
- 手表和手机版本统一为 0.19.0 debug 候选。

### Fixed

- 修复 checkpoint 写入 offset 前未无条件 flush 缓冲样本的问题。
- 修复 Xiaomi 认证完成后 GATT 写入竞态、Android 13 旧写 API失败，以及 MTU 517 生成 514 字节属性值超过 512 字节上限的问题。
- 修复 BLE 权限缺失且 LAN 可用时每秒重置退避并反复扫描的问题。
- 修复 Xiaomi 对短时间重复 BLE 扫描限流导致第四轮重连超时的问题；首次发现后改用已验证设备直接 GATT 重连。

### Known Issues

- 首次公钥交换、长期密钥、挑战响应、会话加密和防重放已通过 OWW221/Xiaomi 真机验证。
- 无共同 Wi-Fi/无线 ADB、5 分钟息屏、10 次重连、100 次请求和 15 分钟连续运行已通过；双端重启、蓝牙开关恢复、分页续传和非充电功耗门禁尚待执行。

## [Unreleased]

### Added

- 新增独立 `PoyiWatchMcp` 和 `PoyiWatchTunnel` WinSW 服务、Watch 专属端口/DPAPI 数据目录、24 个 `watch_*` 工具及 8 类分页 Resource，不再把 Watch 业务注册到统一 PersonalMcpGateway。
- 手机 8766 新增 Bearer Token、`/v1/health`、`/v1/capabilities`、稳定设备 ID、严格写入元数据和控制命令校验；Watch MCP 仅通过 mDNS 发现手机业务门面。
- 提交 Gradle 8.14.3 Wrapper 和 GitHub Actions，自动测试、lint、构建两端 debug APK，并生成哈希和构建信息。
- 活动训练采用追加式轨迹/心率文件、有界原子检查点；历史改为独立记录目录和摘要索引，并提供详情及 route/heart 分页 API。
- 新增 10 秒平滑当前/最高速度、四类距离来源证据、计划内/自由记录距离及四页手表训练界面。
- 新增手机 mDNS 广播、设备 ID 校验和分层离线错误；独立 Watch Tunnel 只连接本地 Watch MCP。
- 新增计划持久 outbox、operationId、revision、ACK 和删除操作基础协议。
- 手机计划 API 新增 `requestId`、`expectedRevision`、首次结果持久重放与崩溃恢复契约，供独立 Watch MCP 安全重试写入。

### Changed

- 最后一个计划阶段达标不再结束训练；进入自由记录并继续计时、定位和采样，只有手动结束才归档。
- pause/resume 使用明确动作和带前置状态、过期时间的幂等命令，不再映射为 toggle。
- 历史列表只返回摘要，完整样本通过记录详情和游标分页读取。

### Fixed

- 修复独立 Watch MCP WinSW 旧安装保留虚拟服务账户导致 LocalMachine DPAPI/token 文件 ACL 不一致、服务 1067 退出的问题；安装脚本现在强制使用 LocalSystem 并授予 SYSTEM 读取敏感配置。
- 修复安全 BLE 配对后旧 6 位码被清除，手机 `/v1/auth/token` 无法为独立 Watch MCP 签发 token 的问题；现在可使用已配对长期 LAN 凭据完成一次性 bootstrap。
- 修复独立 Watch MCP 在 streamable HTTP stateless 生命周期中关闭全局 HTTP 客户端后，第二次及后续工具调用报 client closed 的问题。
- 修复旧 schema 2 记录缺少新速度/自由记录字段时产生 NaN、阻断整批历史迁移的问题。
- 修复活动进程重建后首页“继续”进入准备页且训练核心页计时显示 00:00 的问题。
- 修复 378×496 首页长要求文本挤压配对码、计划入口和底部安全区的问题。
- 修复检查点后额外 NDJSON 行未进入累计统计却在恢复后继续保留的问题；恢复现在按已确认 offset 截断完整或损坏尾部，并忽略不可解析行计数。
- 修复 Gateway 写计划响应丢失或手机进程在提交后终止时可能重复执行，以及旧 revision 未返回 409 的问题。

### Known Issues

- `0.18.0/0.11.0` 为 debug 候选，尚未完成三次 30–60 分钟户外对比、进程终止矩阵、378×496 全页面截图和功耗测试。
- BLE 已接入计划、控制选择和定位中继；安全配对、防重放、息屏、10 次重连、100 次请求及 15 分钟连续运行已验证，双端重启和真实非充电功耗仍待执行。
- 独立 Watch MCP 已通过本地契约门禁；Watch 专属 Tunnel 与 ChatGPT 应用因账号未登录尚未完成真实远程绑定。

## [Phone 0.10.1 / MCP 0.5.1] - 2026-07-25

### Fixed

- 修正手机睡眠页遗漏后续 session 的问题；缺失评分和血氧不再显示为 0，MCP 汇总同时返回各指标有效样本数和缺失数。

## [Watch 0.17.0 / Phone 0.10.0 / MCP 0.5.0] - 2026-07-25

### Added

- 手表通过系统 HealthKit Store 读取详细睡眠，手机新增睡眠页，MCP 新增最近睡眠、睡眠列表和睡眠汇总工具。
- 睡眠响应包含评分、血氧、OSA 原值、心率/呼吸范围、多个 session 及完整原始阶段时间线，并明确授权和错误状态。

### Fixed

- MCP `set_training_plan_profile` 现在持久写入手机主计划库并选择、同步、回读校验手表；同步 pending 或两端数据不一致时返回错误，不再产生短暂的手表-only 计划。

### Documentation

- 建立长期项目文档索引、PRD、架构开发规范、测试门禁、缺陷台账和开发决策日志。

## [Watch 0.16.0 / Phone 0.9.0] - 2026-07-24

### Added

- 支持跑步、快走和休息阶段，目标可使用距离或时间。
- 支持默认间歇计划、法特莱克模板和命名/分组多计划库。
- 支持 GPS、步数、心率、总时间、配速、轨迹和数据来源展示。
- 支持训练准备、暂停、继续、停止、自动阶段推进、震动和异常恢复。
- 支持最多 200 条训练历史、完整轨迹、阶段结果和删除。
- 新增手机伴侣应用，提供 mDNS 发现、六位码配对、计划同步、历史详情和手机定位中继。
- 新增本地 MCP，提供计划、训练、统计、历史和轨迹工具。

### Changed

- GPS 未定位不再阻塞训练开始；必要时使用步数估距。
- 原生运动数据按运行时能力探测启用，过期后自动回退。
- 训练数据页和右侧轨迹页改为双向跟手分页。
- 手表界面按 378×496 基准适配并保留底部安全区。

### Known Issues

- 当前为 debug APK 预发布。
- pause/resume 远程接口当前基于 toggle，不具备重试幂等性。
- 当前 OWW221 固件未开放可用的系统户外跑能力映射。
- 自动化测试和 Gradle Wrapper 尚待补齐。

[Unreleased]: https://github.com/666poyi666-collab/WatchIntervals/compare/v0.17.0...HEAD
[Watch 0.17.0 / Phone 0.10.0 / MCP 0.5.0]: https://github.com/666poyi666-collab/WatchIntervals/compare/v0.16.0...v0.17.0
[Watch 0.16.0 / Phone 0.9.0]: https://github.com/666poyi666-collab/WatchIntervals/releases/tag/v0.16.0
