# E-Ink 排版、布局与视觉策略 — 交付

[English](eink-typography-delivery.md) | 中文

日期：2026-10-02
范围：对照 `AGENTS.md` 中的 E-Ink 规则与 DisplayProfile/MotionPolicy 架构，审计阅读器与资料库界面，并落实具体的排版、页面布局与视觉策略改进。
需求：REQ-0007（FR-13–FR-15、AC-10/AC-11）、REQ-0004（显示策略跟进）、REQ-0005（未改动）
状态：仅完成实现与静态自审。**未构建、未测试、未 lint、未在设备上运行**——本主机没有 JDK、Android SDK 或模拟器，也未安装。E-Ink 硬件表现（残影、刷新、实际对比度、可读性）**未验证**。
独立评审：未执行（执行者自述）。

## 审计发现

| 方面 | 发现（改动前） | 处理 |
| --- | --- | --- |
| 正文字号 | 两种模式均为 16 sp × 档位缩放 | E-Ink 经 Profile 策略取 18 sp 基准 |
| 行高 | 1.38 / 1.72 / 2.06 × 字号；Markwon `TextView` 将其叠加在字体自然高度之上，实际行距比数值松约 0.17 em | E-Ink 为 1.45 / 1.60 / 1.80，使用精确基线间距（`setLineHeight`）；Standard 不变 |
| 页边距 | 两种模式均为 8 / 16 / 24 dp | E-Ink 16 / 28 / 44 dp |
| 行宽 | 不限制：10.3 英寸面板每行 100+ 字符 | E-Ink 限制 36 em，居中，仅流式正文 |
| 两端对齐 / 断字 | 无 | E-Ink：Markdown 段落两端对齐并断字；字面 TXT 块保持原样 |
| 正文颜色 | `TextView` 继承 Activity 主题中半透明的黑色（`#DE000000`），与 Material 配色无关 | 颜色取自 `MaterialTheme.colorScheme.onSurface`（E-Ink 下为纯黑）；E-Ink 下链接沿用正文颜色 |
| 外层文字 | Material 3 字阶：行、状态、页脚、按钮使用 11–14 sp 常规字重 | E-Ink：小字阶至少 14 sp、Medium 字重；正文 16 / 18 sp |
| 细线 | 1 dp `#999999` 分隔线与卡片描边 | E-Ink `outlineVariant` 为 `#666666` |
| 对话框 | 两个 `AlertDialog` 使用平台半透明遮罩与窗口淡入淡出 | `LeafletAlertDialog`：高对比度 Profile 下无遮罩、无窗口动画、纯白、2 dp 描边 |
| 进度文字 | 连续滚动时 Reader 页脚每跨越一个块就重组 | E-Ink：每秒至多一次可见更新（Profile 值） |
| 翻页重叠量 | 3–8%，默认 5% | **刻意不变**：REQ-0005 约束了范围，R3-05 测试锁定了该值 |
| 本就合规 | `NavHost` 即时过渡、ripple 关闭、overscroll 光晕关闭、即时滚动、菜单 fling 关闭、静态加载文字、500 ms 扫描进度节流 | 保持不动 |

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `core/eink/src/main/kotlin/io/leaflet/eink/ReadingLayoutPolicy.kt` | 新增：`ReadingLayoutPolicy`（Standard / E-Ink 预设）、`RefreshPolicy.progressMinIntervalMs`、`progressUpdateDelayMs` |
| `core/eink/src/main/kotlin/io/leaflet/eink/DisplayProfile.kt` | 接口与两个预设新增 `readingLayout` |
| `core/reader/src/main/kotlin/io/leaflet/reader/ReadingLayout.kt` | 新增：纯函数 `ReadingLayoutResolver`，Reader 与设置页预览共用 |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | 接入解析器；行宽限制与居中；`ReaderTextView` + `applyTypography`（颜色、两端对齐、断字、精确行高、恢复平台默认值）；块间距；页脚节流 |
| `app/src/main/kotlin/io/leaflet/reader/ui/ReadingSettings.kt` | 预览通过同一策略解析 |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/Theme.kt` | `LocalDisplayProfile`、外层字阶、更深的 E-Ink `outlineVariant` |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/Typography.kt` | 新增：`leafletTypography(highContrast)` |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/DialogPolicy.kt` | 新增：`LeafletAlertDialog` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryHome.kt` | 两个对话框改用 `LeafletAlertDialog` |
| `core/eink/src/test/.../ReadingLayoutPolicyTest.kt`、`core/reader/src/test/.../ReadingLayoutTest.kt`、`app/src/test/.../TypographyTest.kt` | 新增 JVM 测试（未运行） |
| `app/src/androidTest/.../ReaderEinkTypographyTest.kt` | 新增仪器测试（未运行） |
| `docs/architecture/eink.md`、`reader.md`、`docs/requirements/REQ-0004`、`REQ-0007`、`CHANGELOG.md`（及其 `.zh` 配对文件） | 策略对照表、FR-13–FR-15、AC-10/AC-11、更新日志条目 |

未新增依赖、模块、manifest、flavor 源集或厂商 SDK，也没有分页引擎。两个 flavor 共用同一个 Reader；各模式唯一的差异是 Profile 中的数值。`:core:eink` 仍只依赖 Android 基础 API。

## 行为

- Standard 解析结果与既有数值一致（16 sp、行高 `22/16 × {1.00, 1.25, 1.50}` 叠加在自然高度上、页边距 8/16/24 dp、无行宽限制、右侧参差、块间距 4 dp），由 `ReadingLayoutTest` 锁定。Standard 的两个副作用：正文现在使用主题的 `onSurface`（`#202720`），而不是平台半透明黑色，这也使其能跟随深色配色；段落容器现在会占满条目宽度，可见几何不变。
- 运行时从 E-Ink 切回 Standard 会恢复平台自身的换行与链接颜色默认值（`ReaderTextView` 在构造时记录它们）。该路径没有自动化测试。

## 验证 — 本主机 NOT RUN

请在装有 JDK 17、Android SDK 以及模拟器或设备的机器上运行：

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew test lint ktlintCheck
./gradlew :core:eink:testDebugUnitTest :core:reader:testDebugUnitTest
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
# 仅运行新增的仪器测试
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderEinkTypographyTest
```

自审无法确认的 API 与行为；若构建或测试失败，请先检查：Material 3 `AlertDialog` 的 `containerColor` / `tonalElevation` 参数与 `AlertDialogDefaults`（BOM 2024.10.01）；`TextView.setLineHeight`（API 28）以及 `ReaderTextView` 中读取的平台默认值 getter；`DialogPolicy.kt` 中沿父级查找 `DialogWindowProvider`；新增文件的 ktlint import 顺序与换行。

## 未验证 — 真实 E-Ink 设备（6 / 7 / 10.3 英寸；R3-09）

- 18 sp 默认字号与 Medium 字重小字的可读性；外层文字 14 sp 是否足够。
- 1.45–1.80 行高（拉丁文与 CJK）、16–44 dp 页边距，以及 10.3 英寸面板上的 36 em 行宽。
- 两端对齐与断字：6 英寸窄行上的“河流”空隙，以及文本语言与设备语言不同时的断字规则。
- `#666666` 细线与 2 dp 对话框描边的对比度；使用 `#EEEEEE` 填充时选中状态是否清晰。
- 无遮罩对话框：窗口动画是否真的消失、是否没有残留过渡。
- 页脚每秒一次的更新节奏：残影，以及翻页后页脚延迟更新的观感。
- 5% 翻页重叠（约一行）以及页面边缘被截断一半的文字行。
- 残影、刷新与局部/全量重绘表现总体而言；通用控制器并不控制硬件。

## 已知限制与后续

- 黄色“其他匹配项”搜索高亮在灰度屏上近乎白色，搜索 `TextField` 聚焦时光标闪烁；两者都需要设备检查，且高亮需要一个随模式变化的令牌并穿过多个调用点。
- 字面 TXT / 降级块不做两端对齐与断字（Compose `Text`，按设计原样显示）。
- 断字遵循设备语言环境的规则。
- 设置页预览使用 Compose `Text`，其两端对齐只是对 Reader 中带断字的 `TextView` 的近似。
- 不声称下一任务的前置条件已满足：R3-09（设备矩阵）与 R3-10（所有者决策）仍被阻塞。未提交、未推送任何内容。
