# Changelog

本文件记录用户可感知的版本变化，格式参考 Keep a Changelog。手表端和手机端版本独立，在标题中分别标明。

## [Unreleased]

### Added

- 新增固定 Tunnel ID 的 ChatGPT 长效 MCP 连接、DPAPI 凭据存储、登录自启动、自动重连和健康检查脚本。
- Phone 新增未发布的 `SyncEnvelopeV1` 加密 V2 同步闭环：计划、计划库元数据和训练摘要使用 AES-256-GCM，持久保存 outbox/cursor/conflict/projection，并由 WorkManager 联网补偿。
- 新增 Android Keystore 包装的 device token/root，以及 PBKDF2 恢复包和绑定目标设备、nonce、公钥 fingerprint、10 分钟有效期的已授权设备批准包。

### Changed

- 手机计划库升级到 schema 3；删除操作原子记录显式 tombstone，本地读取暂时缺项不再推断为云端删除。
- Phone 禁用 Auto Backup，并显式排除加密同步 state 与局域网配对配置的 cloud backup/device transfer。

### Fixed

- 401 吊销 token 采用 compare-and-clear；只有清除持久成功才取消后台任务，已轮换的新 token 不会被旧请求误删。
- 所有同步入口共享凭据生命周期锁；ACK/conflict 必须完整覆盖当前 lease，delete ACK 与 projection 待办同一次 state commit，永久 4xx 不再进入 WorkManager 重试。
- revision 按 JS safe integer 使用严格 `long`，cursor 拒绝非 canonical 前导零；conflict 同时保留本地与解密远端候选，不自动覆盖手机计划库。
- plan delete 只有同时命中 root 加密的 `sync:library` tombstone ledger 才允许投影；训练摘要使用 allowlist 且散列 envelope ID，避免原始轨迹、逐点心率和时间型 ID 外泄。

### Known Issues

- 加密 V2 仍是本地 QA 候选：本阶段不含配置 UI、staging、Android Keystore 真机、Doze/重启或 PC-off 验收，关联 `BUG-010`。

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
