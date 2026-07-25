# 步序项目文档

本目录是 WatchIntervals（应用显示名“步序”）的长期事实来源。README 用于快速了解和构建；本目录用于回答“为什么做、应当怎样工作、如何验证、还存在什么问题”。

## 文档地图

| 文档 | 维护内容 | 何时更新 |
| --- | --- | --- |
| [product-requirements.md](product-requirements.md) | 用户、场景、需求编号、验收标准、范围 | 新增或改变产品行为时 |
| [architecture-and-development.md](architecture-and-development.md) | 模块、状态、数据、接口、构建和代码规范 | 改架构、协议、存储或工具链时 |
| [testing.md](testing.md) | 自动化策略、手表/手机/MCP 回归矩阵、发布门禁 | 修 Bug、新增功能或发现新风险时 |
| [bugs.md](bugs.md) | 开放缺陷、技术债、历史修复和验证证据 | 发现、修复、复现或关闭问题时 |
| [project-log.md](project-log.md) | 决策背景、Vibe Coding 迭代轨迹、工作日志 | 每个有意义的开发批次结束时 |
| [system-exercise-implementation.md](system-exercise-implementation.md) | OWW221 系统运动接口专项分析 | 厂商固件或桥接实现变化时 |
| [../CHANGELOG.md](../CHANGELOG.md) | 用户可感知的版本变化 | 发布或修改版本号时 |
| [../AGENTS.md](../AGENTS.md) | AI/编码代理的项目工作约束 | 工作流或项目治理变化时 |

## 当前基线

| 项目 | 当前值 |
| --- | --- |
| 基线日期 | 2026-07-25 |
| 手表应用 | `app`，`com.poyi.watchintervals`，`0.17.0`（27） |
| 手机应用 | `phone`，`com.poyi.watchintervals.phone`，`0.10.0`（10） |
| 主要实机 | OPPO Watch 4 Pro，OWW221，378×496，Android 11 |
| 编译环境 | JDK 17、Android SDK 35、Gradle 8.14.3 |
| 发布状态 | GitHub 私有仓库；`v0.17.0` 为 debug APK 预发布 |

## 事实来源

文档基线综合了当前工作区可见的项目对话、源码、README、真机分析文档、Git 记录，以及 2026-07-23 至 2026-07-24 留存的界面/回归截图名称。截图与临时 XML 没有纳入 Git，它们只作为本地历史证据；源码和已验证运行行为的优先级更高。证据尚未确认的内容统一标为“待确认”，不写成已实现事实。

## 维护规则

1. 需求必须使用稳定编号 `REQ-领域-序号`，删除需求时保留编号并标记状态。
2. 缺陷必须使用稳定编号 `BUG-序号`，至少记录状态、严重度、影响版本、复现方法和验证证据。
3. 行为变化同时更新需求、测试和 CHANGELOG；纯重构至少更新开发文档或项目日志。
4. 接口、JSON schema、端口、权限、SharedPreferences/File 名称变化必须更新开发文档。
5. 发布前执行 [testing.md](testing.md) 的门禁，禁止只凭“能编译”判定可发布。
6. 日志不得记录配对码、API Key、Token、精确家庭网络地址或真实运动轨迹。
7. 提交说明采用 `type(scope): summary`；建议类型为 `feat`、`fix`、`docs`、`test`、`refactor`、`build`、`chore`。

## 新对话启动清单

后续让 AI/Codex 继续开发时，先要求读取本索引、需求文档、开放 Bug 和相关源码；开发结束后要求更新文档、构建、执行对应回归并记录证据。这能把 Vibe Coding 从临时对话转为可审计的工程过程。
