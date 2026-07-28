# 系统运动实现路径

本文记录 OWW221（Android 11）中 `HeyHealthService.apk` 与 `HeySports.apk` 的静态分析结果，以及在真实手表上的 Binder 验证。应用运行时仍以能力探测结果为准，不依赖包版本号猜测能力。

## 入口与权限

- 健康服务包：`com.heytap.wearable.health`
- 实机版本：`4.1.3_a09f60c_260616`
- 服务：`com.heytap.wearable.health.healthkit.exercise.ExerciseService`
- bind action：`heytap.wearable.intent.action.BIND_EXERCISE_SERVICE`
- Binder descriptor：`com.oplus.wearable.health.service.client.impl.IExerciseApiService`
- 客户端入口：`com.oplus.wearable.healthkit.impl.ServiceBackedHealthKit$exerciseClient$2`
- 权限：`heytap.wearable.permission.health.BIND_CLIENT_SERVICE`、`heytap.wearable.permission.health.BIND_EXERCISE_SERVICE`、`heytap.wearable.permission.healthkit.PROVIDER`

上述三个权限在本固件中 protection level 均为 `normal`。权限可授予只代表允许连接服务，不代表某种运动类型已开放。

## Binder 事务表

| code | 客户端语义 | 服务端路径 |
| ---: | --- | --- |
| 1 | 查询 API 版本 | HealthKit/Exercise API version |
| 2 | `prepareExerciseAsync` | 预热配置 -> MCU prepare |
| 3 | `startExerciseAsync` | 运动配置 -> MCU start |
| 4 | `pauseExerciseAsync` | 控制命令 pause |
| 5 | `resumeExerciseAsync` | 控制命令 resume |
| 6 | `endExerciseAsync` | 控制命令 end |
| 7 | 当前运动信息 | current exercise info |
| 8 | 注册更新监听 | update listener register |
| 9 | 注销更新监听 | update listener unregister |
| 10 | `getCapabilitiesAsync` | 支持的运动类型及数据能力 |
| 11 | 发送扩展信息 | extra info |
| 12 | 手动分段 | mark lap |
| 13 | 最近结束运动 | last ended data |

接口请求、返回值和事件对象都继承 `ProtoParcelable`，写入 `Parcel` 的主体是 protobuf 字节，而不是 Java `Serializable` 对象。因此直接手写 Binder 调用时必须复用匹配固件版本的 protobuf schema。

## 配置与数据路径

跑步类型为 `ExerciseProto.ExerciseType.OUTDOOR_RUN`，数值 `0x2714`。预热使用 `ExerciseProto.WarmUpConfig`，正式记录使用 `ExerciseProto.ExerciseConfig`。

预热订阅：

- `Distance`
- `Heart Rate`
- `steps`
- `Location`
- `GPS satellite num`

正式运动样本订阅：

- `Active Duration`
- `Distance`
- `Heart Rate`
- `steps`
- `Location`
- `Pace`
- `GPS satellite num`

统计订阅：

- `Distance Total`
- `Active Duration`
- `Steps Total`
- `Avg Heart Rate Stats`

更新监听由 `ExerciseUpdateCallback` 接收。应用优先读取 `Distance Total`；没有统计值时把连续 `Distance` 样本按累计基线求差，同时接收 `Heart Rate` 与 `Steps Total`。反射加载前会验证混淆类中的数据类型集合确实具有名称字段和 protobuf 字段，避免固件升级后误把无关静态集合当成数据类型表。

静态能力矩阵中 `OUTDOOR_RUN` 一共列出 23 个 sample 类型：`Heart Rate`、`Elevation`、`Speed`、`Step per minute`、`Distance`、`Stride`、`steps`、`Pace`、`Calories`、`Dynamic Calories`、`Static Calories`、`Avg Pace`、`Best Pace`、`Avg Speed`、`Max Speed`、`Total Ascent`、`Total Descent`、`Max Elevation`、`All duration`、`Active Duration`、`All Avg Speed`、`Location`、`GPS satellite num`。对应 stats 矩阵有 27 项，其中已确认 `Distance Total` 的载荷类型为 `Long`，`Per km Pace Stats` 为 double array。`Location` 载荷为纬度、经度、高度、方位、速度、状态六元组，时间字段为 epoch seconds。APK 没有为这些数值保留可独立验证的单位元数据，所以未收到真机样本前不对距离和配速缩放作额外假设。

## MCU 命令路径

| 功能 | module | command |
| --- | ---: | ---: |
| prepare / start | `0x40` | `0x03` |
| pause / resume / end / lap | `0x40` | `0x05` |
| extra info | `0x40` | `0x65` |
| last ended data | `0x40` | `0x19` |

调用链为：应用客户端配置 -> HealthKit Binder -> `ExerciseService` -> protobuf 请求 -> MCU 通道。系统运动 APK 的页面不是数据源；真正的运动会话由健康服务维护。

系统运动包本身还保留一条旧私有架构路径：它请求 `BIND_HEALTH`、`BIND_CONN`、`BINDER_PROVIDER`、`PROVIDER`、`WRITE_SECURE_SETTINGS` 等系统权限，并直接引用隐藏的 `android.app.wear.McuManager`。`SportPrepareActivity` 入口受 signature 权限 `heytap.wearable.permission.sports.VIEW` 保护，因此第三方包不能通过启动原生页面获得同等访问级别。旧运动路线不在 HealthKit `ExerciseSessionRecord` 中，而保存在健康服务 Room 表 `sport_gps`（`sport_id/time_stamp/longitude/latitude/speed/state`），系统运动通过 `ISportAidlInterface2.queryGpsByte(sportId)` 读取压缩 protobuf。`com.heytap.wearable.health.binder` 虽使用 normal permission，`BinderProvider.query()` 仍会校验调用包签名，第三方签名会被 `signature not match` 拒绝，不能把这条私有 Binder 当作可用的历史导入接口。

## 系统运动的定位路径

`HeySports.apk` 的准备页并不以 Android `LocationListener.onLocationChanged()` 作为“定位完成”的判据。反编译得到的实际链路为：

1. `GpsSignalManager` 向 `McuManager` 注册 `module 8 / event 6` 与 `module 8 / event 7`。
2. 户外跑准备时向 `module 8 / command 5` 发送 `GpsSwitch` protobuf。开启户外跑的载荷为 `08 01`，关闭载荷为空。
3. `event 7` 返回 `GpsLocateData`：field 1 为 `gpsSnr`，field 2 为 `gpsLocate`；只有 `gpsLocate == 1` 时原生界面才显示定位成功。
4. 信号分级为：SNR `<18`、`18–24`、`>=25`，分别对应弱、中、强三级。
5. 原生应用约 5 秒后还会请求 `gps` provider（5 秒间隔），首个位置到达后立即移除。该监听用于 GPS 时间同步，并非原生准备页的定位完成判据。

应用现已复用相同的 MCU 定位状态路径，同时继续用公开 `LocationManager` 获取经纬度。MCU 的 `gpsLocate` 只表示系统定位状态，本身不携带轨迹坐标，不能直接累计路程。实机日志已确认第三方应用能够完成监听注册、开启和成对关闭：

```text
Legacy system GPS listener registered
Legacy system GPS opened
MCU GPS located=false snr=0
Legacy system GPS closed
```

本次室内测试中原生运动与本应用的 GPS provider 最后位置都为 `null`，所以 `located=false` 是设备未获得卫星 fix，而不是桥接失败。界面不再把它表现成阻塞：训练可立即开始，开阔环境取得坐标后使用真实轨迹；搜星期间由系统步数传感器估距。

后续真机发现 OWW221 的厂商 `Step_detector` 行为与标准 Android 语义不完全一致：事件值可能是累计量。直接把事件值当“本次步数”会造成两步被放大为数百米。应用已改为优先注册原生 `Step_Sensor`（`TYPE_STEP_COUNTER`），仅用相邻累计值之差作为实际步数；`Step_detector` 只在累计传感器缺失时启用，并同时兼容标准单步值和累计值。

系统运动 `SportService` 的旧架构还会注册 MCU `341`、`343`、`513` 以及 `module 8 / event 18、31、34、35` 等运动会话事件。它们承载的是完整运动会话、提醒和结果数据，而定位准备状态仍独立走 `8/5、8/6、8/7`。当前固件对第三方开放的 HealthKit capabilities 为空，所以应用保留能力门控，不再把这一固件状态显示成启动错误；实际记录由连续 GPS、累计步数差和光学心率完成。

轨迹采集使用 1 秒 GPS 请求并在前台服务中持续运行。只有精度、时间间隔和速度过滤通过的坐标才累计距离；合格坐标逐行追加到会话文件，地图只绘制最多 600 点的简化预览。MCU `gpsLocate` 与 SNR 负责复刻系统准备页的搜星状态，Android GPS 坐标负责绘制真实路线，两者不会混作同一种数据。

## 固件能力门控

反编译样本包含完整的数据能力矩阵，包括距离、心率、步数、配速、位置、GPS 卫星数以及 `OUTDOOR_RUN` 等多种运动类型。但服务实际校验读取 `w4.a.a`，本机初始化结果为空集合。因此静态存在某类型不代表运行时开放。

实机探测顺序：

1. HealthKit Provider 可查询。
2. `isAvailable()` / API 版本可用。
3. Binder transaction 10 成功返回 capabilities。
4. capabilities 的运动类型映射为空，不包含 `OUTDOOR_RUN`。
5. 应用停止原生 prepare/start，标记“系统未开放”，继续 GPS 与步数采集。

关键日志：

```text
System exercise operation succeeded: capabilities
UnsupportedOperationException: firmware does not expose OUTDOOR_RUN through HealthKit
```

更早的链路验证还确认服务能够绑定、监听能够注册，且系统认证层记录 `com.poyi.watchintervals not need authentication`。这些日志只证明 transport 可达；在 capability 校验通过并收到运动数据前，不视为原生运动已启动。

## 应用集成与回退

`SystemExerciseBridge` 从已安装的健康服务 APK 动态加载同版本客户端，避免把另一版本 SDK 类打进应用。所有调用串行执行并等待 `Future` 完成；关闭时先结束仍打开的原生会话，再移除监听，关闭后丢弃异步回调。

距离选择策略：

1. 原生累计/增量距离样本在 10 秒内新鲜时，使用原生距离。
2. 原生样本停滞后恢复 GPS 轨迹；GPS 点只在精度和速度过滤通过后累计。
3. 没有近期可用 GPS 时，以步数乘步长估距，并在 UI 标为近似值。
4. 原生累计距离恢复时，第一条只建立基线，防止同一段路程重复累加。
5. 距离一次跨过阶段终点时，剩余增量继续应用到紧邻的距离阶段。

这套路径保证 HealthKit 未开放、连接失败或运动数据中断时，训练计时、心率与距离回退链路仍可独立运行。
