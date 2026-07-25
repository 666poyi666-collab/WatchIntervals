# Changelog

本文件记录用户可感知的版本变化，格式参考 Keep a Changelog。手表端和手机端版本独立，在标题中分别标明。

## [Unreleased]

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

[Unreleased]: https://github.com/666poyi666-collab/WatchIntervals/compare/v0.16.0...HEAD
[Watch 0.16.0 / Phone 0.9.0]: https://github.com/666poyi666-collab/WatchIntervals/releases/tag/v0.16.0
