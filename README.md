# 间歇跑（OPPO Watch）

为 OPPO Watch 4 Pro 实机（系统型号 `OWW221`，378×496，Android 11）设计的独立训练应用。

## 项目文档

长期维护文档统一从 [`docs/README.md`](docs/README.md) 进入：

- [`product-requirements.md`](docs/product-requirements.md)：用户需求、范围、验收标准
- [`architecture-and-development.md`](docs/architecture-and-development.md)：架构、数据、接口、开发和发布规范
- [`testing.md`](docs/testing.md)：测试策略、真机回归清单和发布门禁
- [`bugs.md`](docs/bugs.md)：已知问题、技术债与历史缺陷
- [`project-log.md`](docs/project-log.md)：Vibe Coding 决策和开发日志
- [`CHANGELOG.md`](CHANGELOG.md)：面向版本的变更记录

功能、缺陷或架构发生变化时，代码提交必须同时更新对应文档；具体规则见文档索引。

当前版本支持：

- 自定义跑步、快走、休息阶段，目标可按距离或时间设置
- 内置“1 公里跑 + 200 米快走”和法特莱克模板
- GPS 距离、步数估距、总用时、平均配速和标准光学心率显示
- 训练页直接显示实际步数；点击“总距离 · 轨迹”可打开离线实时轨迹图
- 达标震动提醒并自动进入下一阶段
- 前台训练服务、息屏持续定位与 Wi-Fi 休眠保护
- 训练可立即开始；距离阶段优先使用实时 GPS 轨迹，GPS 精度不足时切换到系统步数传感器估距，并明确标注数据来源
- 顶部显示 GPS 权限、系统定位、搜星数量和实际定位精度；心率显示“读取中”或“请佩戴”而不是伪造数据
- 首页按 378×496 基准自动缩放，保留底部安全留白；异常权限或定位状态才显示告警，避免挤占训练主操作
- Android 13+ 会在首次训练前请求通知权限，确保前台训练通知可见
- 最多 200 条完整训练历史，包含距离、实际步数、平均心率、计划和轨迹点；手表端可查看详情、轨迹和删除记录
- 独立 `phone` 伴侣 App：局域网 mDNS 自动发现、六位码配对；本地计划库支持新建、命名、分组、保存、再次编辑和同步，训练控制与历史分区显示
- `phone` 内置未发布的加密 V2 同步闭环：计划、计划库元数据和训练摘要使用 AES-256-GCM，持久保存 outbox/cursor/conflict/projection，并由 WorkManager 在联网后补偿；本阶段不包含配置 UI
- 手表首页按页面方向进入训练历史和训练计划；训练数据为第一页，实时轨迹固定在其右侧页，并支持双向跟手返回
- 手表 `8765` 配对 API 与电脑本地 stdio MCP，可由 ChatGPT 查询计划/记录/完整轨迹并开始、暂停、继续或结束训练

## 手机伴侣与本地 MCP

当前传输以同一局域网直连为主，使用 `_watchintervals._tcp.` mDNS 自动发现；这比 BLE 更适合连续同步完整轨迹，BLE 保留为后续无 Wi-Fi 时的发现/兜底通道。手表首页显示六位配对码。远程启动训练需要允许后台定位，应用会在首次本地开始训练时单独请求该权限。

加密 V2 云同步当前仅是本地 QA 候选：canonical 路由为 HTTPS `POST /sync/v2/exchange`，device token 与同步 root 由 Android Keystore 包装，删除只接受显式 tombstone。当前没有随本阶段交付 provisioning UI，也没有 staging、Keystore 真机、Doze/重启或 PC-off 验收证据，因此不得描述为已发布或已具备 PC-off 能力。

```powershell
gradle :app:assembleDebug :phone:assembleDebug
adb -s WATCH install -r app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE install -r phone/build/outputs/apk/debug/phone-debug.apk
```

电脑创建 `%USERPROFILE%/.watchintervals.json`：

```json
{"host":"WATCH_IP","port":8765,"pairingCode":"六位配对码"}
```

MCP 启动命令与工具清单见 [`mcp/README.md`](mcp/README.md)。

## 数据来源与真机条件

距离来源按顺序选择：手表 HealthKit 原生运动距离、Android `LocationManager` GPS 轨迹、系统步数传感器估距。OWW221 的厂商 `Step_detector` 实测可能返回累计值而不是标准单步值，因此现在优先读取 `Sensor.TYPE_STEP_COUNTER` 并用相邻累计值求实际步数差；只有缺失累计传感器时才兼容 detector。原生距离超过 10 秒没有新样本会自动退回 GPS/步数，恢复后的第一条累计值只建立新基线，避免重复计距。GPS 使用 1 秒连续定位、一次性首定位和 `GnssStatus.Callback` 搜星状态，只累计连续且速度合理的点；合格坐标同时加入最多 600 点的本地轨迹图并随训练检查点保存，进程恢复后轨迹仍可继续，GPS 恢复后自动从步数估距切回真实轨迹。步数链路需要 `ACTIVITY_RECOGNITION` 权限。心率优先接受原生运动样本，同时保留公开 `Sensor.TYPE_HEART_RATE` 读取，需要 `BODY_SENSORS` 权限和手表正确佩戴。

在室内、天空遮挡严重或手表未佩戴时，系统不会产生可用 GNSS/心率数据。系统定位搜星不再阻塞开始按钮；移动后先按步数估距并明确标注，取得坐标后再切换真实轨迹。真机轨迹验证应在开阔户外等待定位完成后，步行或跑步至少 10 米。

应用启动时对系统运动做三段式能力检测：HealthKit Provider 存在、客户端 API 版本可用、`getCapabilitiesAsync()` 明确包含 `OUTDOOR_RUN`。只有三项都通过才准备原生运动并订阅距离、心率、步数、位置与配速；Binder 连接成功本身不算功能可用。

当前实机固件的 HealthKit 服务存在且 API 可连接，但运动类型能力映射为空，因此界面显示“系统 未开放”，并继续使用 GPS/步数链路，不会卡住距离记录。完整的 Binder、protobuf、MCU 命令及真机证据见 [`docs/system-exercise-implementation.md`](docs/system-exercise-implementation.md)。

## 构建

使用 JDK 17+、Android SDK 35 和 Gradle 8.14.3：

```powershell
gradle :app:assembleDebug
adb -s 192.168.1.44:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

## 开源参考

定位服务、异常 GPS 点过滤和训练期间前台运行的设计参考了
[OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks)（Apache-2.0）。本项目未复制其源码。
