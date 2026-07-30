# 手机端视觉与图标设计基线

状态：已落地，持续回归  
基线：2026-07-30  
关联：`REQ-UI-006`、`REQ-UI-011`、`REQ-UI-012`、`PT-026`、`PT-027`

## 1. 官方参考文件

本批检索了当前 Apple 与 Android 官方设计资源；只提炼布局、层级、可访问性和自适应图标原则，不把 Apple 模板或符号复制进 Android 产品。

| 资源 | 用途 | 本项目处理 |
| --- | --- | --- |
| [Apple Design Resources](https://developer.apple.com/design/resources/) 与 [iOS/iPadOS/macOS 27 设计套件公告](https://developer.apple.com/news/?id=e2lxw9l1) | 核对最新 Figma/Sketch 组件、状态与缩放原则 | 仅研究，不下载、不提交、不派生产品素材 |
| [Apple HIG：Materials](https://developer.apple.com/design/human-interface-guidelines/materials) | 区分内容层和 Liquid Glass 功能层 | 玻璃感只用于浮动底栏和连接设置；训练数据卡保持实色 |
| [Apple HIG：Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars) | 顶级目的地、短标签、稳定可见性 | 固定计划/训练/历史/睡眠四个目的地，底栏不承载动作 |
| [Apple HIG：Typography](https://developer.apple.com/design/human-interface-guidelines/typography) 与 [Accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility) | 大标题层级、可读性、触控目标 | 页面标题 34sp；正文/标签使用系统字体；交互目标至少 48dp |
| [Android edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge) | Android 15 系统栏与安全区 | 使用实时 `WindowInsets` 调整顶部、底栏和滚动尾部留白 |
| [Android adaptive icons](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive) | 自适应蒙版与主题图标 | 108dp 前景/背景分层，并提供 Android 13 monochrome 层 |

Apple Design Resources 的[许可协议](https://developer.apple.com/support/downloads/terms/apple-design-resources/Apple-Design-Resources-License-20230621-English.pdf)只允许为 Apple OS 产品制作界面 mock-up，并排除非 Apple OS mock-up 与把模板内容嵌入软件。本项目因此不使用 Apple UI Kit、SF Pro、SF Symbols、Activity Rings 路径或其改造版本；Android 包内的图形全部是原创几何。

## 2. 视觉层级

- 内容层：纯黑画布、`#1C1C1E` 主卡、`#2C2C2E` 高层卡，承载计划、训练指标、历史和睡眠事实。
- 功能层：底部导航和连接设置使用半透明深色渐变、1dp 高光描边、同心圆角和轻微 elevation；不在内容卡中重复叠玻璃。
- 强调色：使用项目原创珊瑚 `#FF4D67`、薄荷 `#84E66A`、青蓝 `#48CBEA`，不复用 Apple Activity Rings 官方三色。
- 排版：页面当前目的地使用 34sp 大标题；产品名缩为 18sp 品牌眉题，避免与页面标题等权重复；数字继续启用 tabular figures。
- 导航：滚动内容延伸到底栏之后，尾部留白保证最后一项可完整滚出；底栏始终浮于内容上方，四个目的地保留短中文标签。

## 3. 原创图标系统

`PhoneSymbolView` 在 24×24 逻辑视口用 `Canvas`/`Path` 绘制计划、训练、历史、睡眠、返回和定位图形。圆帽、圆角连接和统一光学尺寸替代 OEM 字体中的 `▦`、`▶`、`◷`、`☾` 等 Unicode 图标；选中态增加笔画权重和珊瑚色胶囊，父级提供中文 `contentDescription` 与选中状态。

启动器标志改为原创“间歇路线”：薄荷和青蓝两段往返路径最终汇入珊瑚前进箭头，负空间表达阶段次序与向前训练。资源包括：

- `drawable/ic_launcher_foreground.xml`：彩色 108dp 自适应前景；
- `drawable/ic_launcher_monochrome.xml`：主题图标遮罩；
- `drawable/ic_launcher.xml`：旧启动器/通知兼容矢量；
- `mipmap-anydpi-v26/ic_launcher*.xml`：普通与圆形自适应入口。

## 4. 验证边界

- API 35 模拟器 1080×2400 已验证计划页、断连训练页、连接设置、四目的地浮动底栏、系统栏和无 Unicode 底栏图标；UI hierarchy 确认四个目的地均有独立中文可访问名称和选中状态。
- `PhoneNavigationSpecTest` 固定四目的地顺序、唯一原创 symbol 与非空可访问名称；`PhoneCloudSetupSpecTest` 防止已退役 V2 加密流程重新进入活动设置页。
- `PT-026` 的真实手机、长文案与大字体，以及 `PT-027` 的多启动器蒙版/主题图标仍需在发布候选真机执行；模拟器证据不能替代这些外部渲染差异。
