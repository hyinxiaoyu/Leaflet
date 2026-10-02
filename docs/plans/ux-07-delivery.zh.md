# UX-07 交付 — 表格对齐、图片尺寸与复制反馈

[English](ux-07-delivery.md) | 中文

日期：2026-09-30
工作包：[UI/UX 优化任务](ui-ux-optimization-tasks.md) 中的 UX-07，ISS-023/024/025
需求：[REQ-0007](../requirements/REQ-0007-typography-tables-code.md)、[REQ-0009](../requirements/REQ-0009-local-resources-links.md)
状态：IMPLEMENTED — 软件验收通过；E-Ink 实机 NOT_RUN。
审查：执行者自查；未进行独立代理审查。

## 产品决策与验收

用户请求授权继续打磨产品。本轮选择已有 P1 渲染任务，缺陷已有明确证据：每行独立表格列宽导致错位，解码后的 16×16 图标在模拟器上放大到 840×840 物理像素，代码复制反馈从不消失。既有 READY 需求覆盖这些路径；验收补充已在两种语言中同步。

- 表格根据全部带样式单元格统一列宽，保留空位置，网格分隔线为 1 dp、表头分隔线为 2 dp，并维持横向可达性和基于位置的搜索高亮。
- 图片居中显示已解码的物理尺寸；仅当视口宽度或 320 dp 高度限制要求时等比例缩小。正确处理设备密度，不限制长图或全景图的宽高比。
- 复制保留去除围栏后的精确正文。反馈自最近一次点击起在现有单行操作按钮中保持两秒，不插入新的反馈行；连续点击重新计时。

停工条件：以上验收路径、相关既有页面回归、JVM 测试、lint、ktlint 和两个调试构建全部通过。硬件刷新和残影属于独立的未验证门禁。本轮不变更依赖、数据库、公共 API、编辑器、网络服务或发布配置。

## 实现与变更文件

`TableGrid` 使用 Compose 内在测量 (intrinsic measurement) 获取真实带样式内容的尺寸，然后对每个单元格做一次固定尺寸测量。同一列所有单元格共用最大宽度，同一行所有单元格共用最大高度。边框使用 `onSurface`。既有 `TableBlockEntry` 仍负责搜索坐标映射和横向滚动。`boundedImageDimensions` 在物理像素中应用不大于一的缩放比例；`ImageEntry` 保留占满宽度的解码容器，将位图布局尺寸换算回 dp。组合生命周期内的 `LaunchedEffect` 取消或重启复制反馈计时。

```text
app/src/main/kotlin/io/leaflet/reader/ui/reader/
  ReaderScreen.kt       # bind grid, bounded centered bitmap, transient copy action
  TableGrid.kt          # shared widths/heights and grid dividers
  ImageDimensions.kt   # pure bounded physical-pixel sizing
app/src/test/kotlin/io/leaflet/reader/ui/reader/
  ImageDimensionsTest.kt
app/src/androidTest/kotlin/io/leaflet/reader/ui/
  ReaderRenderingLayoutTest.kt
  PageViews.kt          # native-heading visibility helper
  ReaderEntryTest.kt / ReaderDisplayTest.kt / ReaderImageTest.kt
docs/development/ux07-screenshots/  # eight before/after captures
```

本次同时更新双语 REQ-0007/0009 验收补充、阅读器架构、UX-07 任务卡、本交付报告和 CHANGELOG。ADR-0004/0005/0006 仍适用，没有替代既有架构决策。保留 AGENTS 核心约束：原文件只读、UI/ViewModel/storage 所有权、离线图片策略、两种发行版共享阅读器，不承诺硬件刷新能力。Full 与 Eink 使用同一实现；Standard 与 E-Ink 运行模式均有测试。本次会话中同时出现 UX-06 浮层、阅读默认值、设置及其测试的并行修改，已保留；这些行为归属独立代码审查交付。最终集成检查包含该工作区。

## 验证

环境：macOS ARM64、JDK 17.0.9、仓库冻结工具链、Android 15 ARM64 模拟器（`dev_arm64_api35`、1080×2400、密度 420 dpi）。页面测试使用真实阅读器与解析器，通过既有替身存储接缝 (fake storage seam) 驱动；图片管线测试执行真实测试 provider。JVM 样本测试包括 `tables.md` 和 `relative-images.md`；页面几何使用对应的聚焦样本。

| 检查 | 结果 |
| --- | --- |
| 新页面测试针对改动前 `ReaderScreen.kt`（`5a75429`） | 预期失败：6/6；列起点相差 169 px，小图标测得 840 px，长图被放大，两项复制超时断言失败 |
| 全部 JVM 变体 | PASS：694 条记录 / 307 个去重方法，失败、错误、跳过均为零；新增六项尺寸测试，加上并行工作中的四项阅读默认值测试 |
| `lint` / `ktlintCheck` | PASS |
| Full/Eink 调试 app 与 androidTest APK 构建 | PASS |
| 最终 Full 页面回归 | PASS：57/57；最新并行目录调整追加 9/9 项渲染、目录与搜索复验 |
| 最终 Eink 页面回归 | PASS：57/57；最新并行目录调整追加 9/9 项渲染、目录与搜索复验 |
| 截图审查 | PASS：列对齐与边框、小图片自然尺寸、按钮内复制反馈 |
| E-Ink 实机对比度、刷新、残影；TalkBack | NOT_RUN |

首次扩展的 54 项测试在每个发行版各失败六项旧断言，它们仍等待 UX-06 已移除的常驻 Compose 外层标题。文档标题仍通过原生 Markwon TextView 可见。`PageViews.hasVisibleText` 现在检查实际视图及其可见边界；三个受影响测试类仍保留正文、目录、搜索、图片、模式和旋转断言。并行代码写入还造成一次临时标签未解析的编译失败，完成后的代码已重新检查。格式违规已修正，未改变行为。共享工作区构建还间歇性无法解析源码仍存在的函数；最终验证因此在 `/tmp` 下的隔离源码副本中执行，Kotlin 源码和构建配置经 SHA-256 比较与工作区一致。

一项并行新增搜索回归最初失败：辅助断言检查的是最上方的前一段，而非当前落点块；两次向前步进还会循环到末项，无法返回首项。测试现在测量当前命中的原生视图，只返回一步并检查当前索引；聚焦设备复验通过。此修正未改变产品行为。

初次开发检查发现约束扩展函数缺少导入和一处 ktlint 空行违规，均已修正。组合构建中 Kotlin 在进程内编译时还耗尽了默认 512 MiB Gradle 堆。改为 2 GiB 堆、两个 worker、关闭并行执行后通过；这些仅作用于本次命令，未修改仓库配置。既有弃用警告仍存在。

实际最终构建命令（已设置 JDK 17 和隔离的临时 `GRADLE_USER_HOME`）：

```bash
./gradlew test lint ktlintCheck \
  :app:assembleFullDebug :app:assembleEinkDebug \
  :app:assembleFullDebugAndroidTest :app:assembleEinkDebugAndroidTest \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' \
  --max-workers=2 --no-parallel --offline --no-daemon
```

选择的页面套件为 `ReaderRenderingLayoutTest`（6）、`ReaderInteractionTest`（19）、`ReaderEntryTest`（5）、`ReaderSyntaxPageTest`（5）、`ReaderImageTest`（1）、`ReaderImagePipelineTest`（6）、`ReaderResourceTest`（9）和 `ReaderDisplayTest`（6）：并行工作增加三项浮层回归后，每个发行版 57 项。两个 app/test APK 对都使用 `adb install -r` 安装，再使用 `adb shell am instrument -w -r -e class <comma-separated-suite-names> -e renderingScreenshotPrefix <prefix> <test-package>/androidx.test.runner.AndroidJUnitRunner` 执行。测试包为 `io.leaflet.reader.debug.test` 和 `io.leaflet.reader.eink.debug.test`。

最后一处并行目录改动在落点补偿中扣除了关闭菜单时消失的标题。57 项扩展回归通过后，重新执行两个发行版构建、全部 JVM 与静态门禁，再执行六项 UX-07 用例及 `tocClickLandsOnItsSection`、`tocLandingIsNotCoveredByTheTopChrome`、`firstBlockSearchMatchStaysBelowTheSearchBar`：每个发行版 9/9，通过且零跳过。最终测试源码与配置快照和工作区一致。可安装调试 APK 及 SHA-256、测试摘要保留在 [app/build/outputs/ux07](../../app/build/outputs/ux07/verification.json)。

通过 Gradle 复验时，对新聚焦套件使用 `:app:connectedFullDebugAndroidTest` 和 `:app:connectedEinkDebugAndroidTest`，并传入 `-Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderRenderingLayoutTest`；移除 class 过滤器可执行全部 app 套件。

## 视觉证据

以下为 instrumented 模拟器宿主内真实生产阅读器的截图，使用合成文档与位图。它们证明软件布局，不代表 E-Ink 硬件表现。

| 场景 | 修改前 | 修改后 |
| --- | --- | --- |
| E-Ink 运行模式表格 | [截图](../development/ux07-screenshots/table-eink-before.png) | [截图](../development/ux07-screenshots/table-eink-after.png) |
| Standard 运行模式表格 | [截图](../development/ux07-screenshots/table-standard-before.png) | [截图](../development/ux07-screenshots/table-standard-after.png) |
| 16×16 本地图标 | [截图](../development/ux07-screenshots/small-image-before.png) | [截图](../development/ux07-screenshots/small-image-after.png) |
| 复制反馈 | [截图](../development/ux07-screenshots/copy-feedback-before.png) | [截图](../development/ux07-screenshots/copy-feedback-after.png) |

## 边界、下一候选与清理

E-Ink 实机验证、TalkBack、厂商刷新控制和发布决策仍未完成。本轮未发布产品或提交代码。像素取整可能相差一个像素。未改动的图片加载生命周期和解码预算保留 REQ-0009 所述限制。

下一候选是既有 UX-04/05 导航工作：`LibraryBrowseScreen` 的路径仍为纯文字，扫描控件会改变列表纵向位置，文件行只有 4 dp 纵向内边距。先满足 UX-01/02 和 UX-04 前置条件，再增加祖先目录面包屑 (breadcrumbs)、固定扫描状态区域和 48 dp 行触控目标。本节是后续建议，本轮未实现。

临时模拟器与 Gradle 副本、源码备份、拉取的截图和日志仅创建在 `/tmp/leaflet-ux07.ml4UAV` 与 `/tmp/leaflet-ux07-gate.log`；清理已完成：模拟器已停止，上述临时路径已删除。八张有价值截图有意保留在 `/Users/clover/source_dev/Leaflet/docs/development/ux07-screenshots/`。最终调试 APK 和 `verification.json` 保留在 `/Users/clover/source_dev/Leaflet/app/build/outputs/ux07/`，作为可审阅交付产物。隔离构建报告随临时源码副本删除；主工作区已有构建产物属于并行工作，已保留。既有用户文件和测试样本保持原样。
