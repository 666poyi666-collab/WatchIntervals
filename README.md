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
- GPS 距离、步数估距、活动时间、当前配速（分钟/公里）与时速同屏显示，以及标准光学心率
- 当前速度优先使用 GNSS 芯片自身的多普勒测速，失效时退回距离窗口估算，界面标注实际来源
- 训练页直接显示实际步数；点击“总距离 · 轨迹”可打开离线实时轨迹图
- 阶段达标震动并自动推进；最后阶段达标后进入自由记录，直到用户手动结束
- 前台训练服务、息屏持续定位与 Wi-Fi 休眠保护
- 训练可立即开始；距离阶段优先使用实时 GPS 轨迹，GPS 精度不足时切换到系统步数传感器估距，并明确标注数据来源
- 顶部显示 GPS 权限、系统定位、搜星数量和实际定位精度；心率显示“读取中”或“请佩戴”而不是伪造数据
- 首页按 378×496 基准自动缩放，保留底部安全留白；异常权限或定位状态才显示告警，避免挤占训练主操作
- Android 13+ 会在首次训练前请求通知权限，确保前台训练通知可见
- 最多 200 条独立训练历史目录；摘要索引与完整轨迹/心率样本分离，支持详情、分页和删除
- 独立 `phone` 伴侣 App：局域网 mDNS 自动发现、六位码配对；本地计划库支持新建、命名、分组、保存、再次编辑和同步，训练控制与历史分区显示
- 手机伴侣已本地实现 `/sync/v2/exchange` 端到端加密双向同步：计划、计划库元数据和训练摘要使用 AES-256-GCM，凭据/root 由 Android Keystore 保护，支持离线恢复包、已授权设备批准、显式 tombstone、冲突保留和 WorkManager catch-up；staging 与真实 PC-off 仍待验收
- 手表首页按页面方向进入训练历史和训练计划；训练数据为第一页，实时轨迹固定在其右侧页，并支持双向跟手返回
- 手表 `8765`、手机 `8766` 协议 v2 API，以及复用同一工具核心的 stdio/Windows HTTP Gateway MCP

## 手机伴侣与本地 MCP

设备控制优先使用应用层加密 BLE，已验证 LAN 作为批量数据加速通道。手表广播 `_watchintervals._tcp.`，手机广播 `_watchintervals-phone._tcp.`；IP 只是运行时端点，设备身份由稳定 deviceId 校验。手机云同步仅接受 HTTPS `/sync/v2/exchange` 与专用 device token，MCP OAuth token 不能替代它；云端只持有计划/训练摘要密文和可验证同步元数据，不持有根密钥，也不把离线设备伪装为可控制。旧 `/sync/push` 与 plaintext V1 是已退役迁移面。远程启动训练仍依赖在线设备和后台定位授权。

首次建立空白同步空间必须在手机“恢复与设备批准”中显式初始化并立即导出离线恢复包；已有空间必须导入恢复包，或让已授权设备批准新设备。缺少 root 时客户端保持未就绪，绝不会静默生成另一把 key。当前实现只通过本地合同、JVM 和 debug 构建门禁；没有 staging deployment revision、Android Keystore 真机证据和三轮 PC-off 结果，因此发布清单保持 `supportsPcOff=false`。

```powershell
.\gradlew.bat :app:assembleDebug :phone:assembleDebug
adb -s WATCH install -r app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE install -r phone/build/outputs/apk/debug/phone-debug.apk
```

电脑创建 `%USERPROFILE%/.watchintervals.json`：

```json
{"watchDeviceId":"WATCH_DEVICE_ID","phoneDeviceId":"PHONE_DEVICE_ID","pairingCode":"六位配对码"}
```

MCP 启动命令与工具清单见 [`mcp/README.md`](mcp/README.md)。

## 数据来源与真机条件

距离来源固定为系统运动、手表 GPS、手机 GPS 和步数估距，并记录各来源距离及切换证据。原始轨迹和心率逐行追加到活动会话目录，检查点只保存有界状态；实时地图使用不超过 600 点的简化预览，不参与距离统计。结束后每条历史拥有独立摘要和样本文件，历史索引不再内嵌整条轨迹。OWW221 的厂商 `Step_detector` 实测可能返回累计值，因此优先读取 `Sensor.TYPE_STEP_COUNTER` 的相邻差。步数链路需要 `ACTIVITY_RECOGNITION`，公开心率传感器需要 `BODY_SENSORS` 权限和正确佩戴。

在室内、天空遮挡严重或手表未佩戴时，系统不会产生可用 GNSS/心率数据。系统定位搜星不再阻塞开始按钮；移动后先按步数估距并明确标注，取得坐标后再切换真实轨迹。真机轨迹验证应在开阔户外等待定位完成后，步行或跑步至少 10 米。

应用启动时对系统运动做三段式能力检测：HealthKit Provider 存在、客户端 API 版本可用、`getCapabilitiesAsync()` 明确包含 `OUTDOOR_RUN`。只有三项都通过才准备原生运动并订阅距离、心率、步数、位置与配速；Binder 连接成功本身不算功能可用。

当前实机固件的 HealthKit 服务存在且 API 可连接，但运动类型能力映射为空，因此界面显示“系统 未开放”，并继续使用 GPS/步数链路，不会卡住距离记录。完整的 Binder、protobuf、MCU 命令及真机证据见 [`docs/system-exercise-implementation.md`](docs/system-exercise-implementation.md)。

## 构建

使用 JDK 17+、Android SDK 35 和 Gradle 8.14.3：

```powershell
.\gradlew.bat :app:assembleDebug
adb -s WATCH_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

## 开源参考

定位服务、异常 GPS 点过滤和训练期间前台运行的设计参考了
[OpenTracks](https://codeberg.org/OpenTracksApp/OpenTracks)（Apache-2.0）。本项目未复制其源码。

## 开发期设备连接

`tools/watch-link.ps1` 负责重建网络 ADB 并保持开发期屏幕常亮。OWW221 未 root，`persist.adb.tcp.port` 无法写入，因此重启后 TCP ADB 不会自行恢复；脚本从 USB 侧读取当前 `wlan0` 地址而不是写死 IP，可反复执行。

```powershell
.\tools\watch-link.ps1                 # 单次重建连接并应用显示策略
.\tools\watch-link.ps1 -Install        # 注册每 5 分钟运行的计划任务
.\tools\watch-link.ps1 -Uninstall      # 移除计划任务
```
