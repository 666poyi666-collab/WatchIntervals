# WatchIntervals Agent Instructions

本文件约束所有参与本仓库开发的 AI/编码代理。项目事实以 `docs/README.md` 索引的文档和当前源码为准。

## 开始工作前

1. 阅读 `docs/README.md`。
2. 阅读与任务相关的 `docs/product-requirements.md`、`docs/bugs.md` 和 `docs/testing.md`。
3. 检查 `git status`，保留用户已有的未提交改动。
4. 从源码、可复现行为和已编号文档确认现状，不把临时截图名当成最终事实。
5. 遵守用户在项目上下文中明确给出的代理、模型和工具限制，不调用被禁用的第三方代理。

## 实施约束

- 功能变更关联或新增 `REQ-*`；缺陷修复关联或新增 `BUG-*`。
- `WorkoutService` 继续作为活动训练状态的唯一所有者。
- 修改数据 schema 时保留向后读取和损坏数据处理。
- 修改传感器融合时明确数据来源、过期条件和切换基线。
- 修改 8765/8766 API 时同步客户端、MCP、开发文档和契约测试。
- 修改手表 UI 时以 378×496 真机布局为基准，检查文字、底部安全区和横纵手势冲突。
- 不提交真实配对码、API Key、Token、精确网络地址、运动轨迹、签名密钥、构建目录、分析 APK 或临时截图。
- APK 作为 GitHub Release 附件发布，不进入 Git 历史。

## 完成条件

1. 执行与改动相称的测试，最低执行两个 Android 模块构建和 `git diff --check`。
2. 更新需求、架构、测试、Bug 台账、项目日志、CHANGELOG 中受影响的部分。
3. 在结果中报告实际执行的命令、结果、未覆盖的真机风险和产物路径。
4. 发布时记录版本、提交 SHA、APK SHA-256、构建类型和开放问题。

只改文档时可省略 Android 构建，但仍需验证 Markdown 本地链接和 `git diff --check`。
