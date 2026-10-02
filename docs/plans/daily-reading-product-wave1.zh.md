# 日常阅读产品化第一轮

[English](daily-reading-product-wave1.md) | 中文

日期：2026-10-01
需求：[REQ-0013](../requirements/REQ-0013-daily-reading-home.md)，`IN_PROGRESS`。
状态：已实现 — 聚焦验证通过；Android/设备验收待执行。
用户优先级：日常阅读闭环，已在本次对话明确选择。
证据：[机器可读结果与源码哈希](review-evidence/2026-10-01-product-wave1/results.json)。

## 产品结果

应用现在从阅读首页开始，而非堆叠的独立控件。单一惰性滚动页包含最新文档、文件夹卡片、最近历史及设置/关于，页头也可直接进入设置。首次使用卡说明如何添加文件夹以及原文件保留。加载中的索引不会冒充空集合，失败时可以重试。真实资料库和最近阅读状态流仍是唯一数据来源，不虚构进度或文件数。

继续入口使用既有文档 ID/Reader 位置恢复链路。文件缺失/授权丢失的记录仍可见，打开恢复选项，不假装来源可读。资料库长按移除仍需确认。清空历史现在须确认，仅调用既有辅助历史操作。

阅读设置展示真实 sp、行距倍率和 dp 边距。即时预览使用 ReaderStyle 相同的 16 sp 正文字号基线、22 sp 行高基线、字号档位、字体允许列表及边距档位。统一静态选中表面替换不对称实心/空选按钮和圆点字符。保留既有偏好与阅读默认值。

所有导航内容共享系统栏/刘海内边距和主题背景，本次新增边距策略不包含 IME。系统栏图标对比度匹配 Standard/系统深色与强制浅色 E-Ink。Standard 浅色采用克制的绿色阅读强调色；E-Ink 全部 Material 颜色角色为不透明灰度，保留无运动/无涟漪策略。

## 工作包与边界

| 任务 | 交付物 | 状态 |
| --- | --- | --- |
| PD-01 首页与返回阅读 | 单一惰性首页、真实元数据、继续入口、首次引导/加载/重试、历史清空确认 | 已实现；页面执行待补 |
| PD-02 阅读控件 | 统一可选触控目标、实际单位、即时预览、既有设置持久化 | 已实现；页面执行待补 |
| PD-03 共享壳层 | 系统栏/刘海边界、图标对比度、完整 E-Ink 调色板 | 已实现；设备测量待补 |
| PD-04 验证与交接 | 资源链接、全新双发行版源码/页面编译、JVM 策略/状态回归、双语文档 | 聚焦检查通过；完整 app/设备门禁待补 |

未新增账号、同步、编辑器、全资料库搜索、数据库迁移、依赖、签名材料、项目许可证或硬件刷新控件。本轮不宣布 UX-01/02/03/04/10 全部完成：外部打开 intent、阅读器搜索高亮清理及全局语言配对仍归属原范围。保留无关未提交的阅读器、浏览和审查改动。

## 实现

| 区域 | 文件与改动 |
| --- | --- |
| 首页 | 新增 `ui/library/LibraryHome.kt`；既有 `LibraryListScreen` API 从 `LibraryScreens.kt` 移动；平铺稳定键资料库/历史行、页头/页脚入口及确认对话框 |
| 列表生命周期 | `LibraryViewModel.kt`：首次加载/失败标识与重试，每资料库一个受跟踪的扫描状态订阅，移除/重试时取消旧观察器；状态失败不取消文件夹列表 |
| 最近行 | `RecentSection.kt`：共享生产行/副标题绑定，静态本地化相对时间，保留缺失/授权丢失提示 |
| 设置 | 新增 `ui/ReadingSettings.kt`；既有 `SettingsScreen` API 从 `LeafletNav.kt` 移动；预览与控件使用既有 SettingsViewModel/DataStore 键 |
| 壳层/配色 | `LeafletNav.kt`、`MainActivity.kt`、`ui/theme/Theme.kt`：边界、图标对比度和集中 ColorScheme |
| 资源/测试 | 英文/中文字符串、`DailyReadingPageTest`（6 方法）、`LibraryHomeLoadTest`（3 方法）、`ColorSchemeTest`（1 方法） |

没有新增原文件写入路径。当前 UI 接缝未暴露阅读比例，因此不显示虚构百分比；文件夹数量使用真实资料库行。删除历史不移除阅读位置。

## 实际验证

macOS ARM64、JDK 17.0.9、冻结 Kotlin/Compose 编译器 2.0.21 和缓存解析依赖。使用 build-tools 35.0.0 的 AAPT2 重新编译变更资源及 48 组缓存依赖资源，与既有各发行版合并资源/manifest 链接，生成真实更新后的 R 符号。这是资源链接验证，不是重新构建可分发 APK。

| 检查 | 结果 |
| --- | --- |
| 新加载/配色及既有设置/元数据契约 | PASS — 执行 9 项：首页加载 3、灰度方案 1、阅读默认/持久化 3、最近元数据发布 2 |
| 既有导航/取消/选择回归与独立探针 | PASS — 24 + 2，两次运行共 35 条聚焦 JVM 执行 |
| Full/Eink 变更资源编译/链接及 Java R 生成 | PASS — 两个变体 |
| Full/Eink 受影响 Kotlin/Compose 源码及页面套件重新编译 | PASS — main、androidTest 组件均通过，使用真实依赖/生成符号而非资源桩 |
| 改动文件 ktlint 1.0.1 | PASS — 保留 app 的 Compose 函数命名例外 |
| 六项生产内容页面用例 | 已编译；Android 上 NOT_RUN |
| 全部 Gradle 套件/lint/APK 组装、Android 页面、E-Ink 实机/TalkBack | NOT_RUN — 当前沙箱阻止 Gradle/ADB socket，不新增完整 app/设备验收声明 |

页面用例覆盖可用的空状态操作、加载中不出现错误首次引导、20 个资料库/30 条历史下可达页脚与继续入口、历史清空确认/取消、恢复交接、预览响应真实 SettingsViewModel。可控元数据驱动生产内容，不使用 UI 的测试专用镜像。

初次手工资源链接缺少依赖 drawable，补入真实依赖资源集后解决。首次独立页面编译缺少用于内部生产内容的 Kotlin friend path，检查器已修正。预览初稿使用了字号基线计算行高，对照仓库后在最终验证前修正为 Reader 的 22 sp 基线。这些问题未隐瞒，也未计作成功门禁。

## 审查与下一门禁

本轮交付真实首页/设置/壳层代码切片，不代表发布或整个产品已完成。UI 可读性、大字号布局、对话框交互、系统栏行为、触控目标和 E-Ink 残影仍需设备证据。既有部分翻译抑制未移除，也不声称已完成全量双语界面。

环境允许 socket 后，使用 JDK 17 执行：

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.DailyReadingPageTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.DailyReadingPageTest
```

临时编译/资源输出、提取资源集、格式配置和日志仅创建在 `/tmp/leaflet-product-wave1.KM2uPO`，记录证据后已删除。保留结果/源码哈希是明确的交付证据，位于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-product-wave1/`。不宣称截图、重建 APK、提交或发布。本轮不覆盖此前验收/自报文档。
