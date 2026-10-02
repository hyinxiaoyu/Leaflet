# UX-06 交付 — 零重排阅读器浮层化架构

[English](ux-06-delivery.md) | 中文

日期：2026-09-30
包：UX-06，见[UI/UX 优化任务清单](ui-ux-optimization-tasks.md) (UI/UX Optimization Tasks)
方案：[Leaflet UI/UX 体验审计与优化设计方案](ui-ux-audit-and-optimization-plan.md) (ISS-018, ISS-019, ISS-020, ISS-022)
需求：REQ-0005（翻页、触控区、可见高度）、REQ-0004（动效策略）、REQ-0003（位置保持）
状态：代码与 JVM/门禁验证已完成。新的插桩验收测试可以编译，但未运行（本机未连接设备或模拟器）；E-Ink 硬件验证仍未运行。
独立评审：未执行（执行者自述）。

## 前置条件与入口

R3-08 软件候选版本（295 项去重 JVM 测试、每变体 75 项插桩测试）是本次基线。本包只改动
阅读器布局：阅读器路由、会话、解析器、位置契约、存储接缝以及翻页距离公式均未改动。

## 变更内容

`ReaderContent` 过去把文档标题、各类提示、搜索栏、`LazyColumn` 以及进度行/阅读菜单依次堆叠在
一个流式 `Column` 中。因此每次切换 `menuVisible` 或 `searchOpen` 都会缩小或撑大承载列表的
`Box(weight = 1f)`，从而让整个正文重新布局——这正是 ISS-018 所指的墨水屏重排（reflow）与闪烁，
同时标题永久占用约 64 dp 视口（ISS-019）。

现在阅读器是一个列表加一个浮层（overlay）：

1. **冻结列表几何尺寸。** `LazyColumn` 是根
   `Column(fillMaxSize().padding(horizontal = pageMarginDp, vertical = 16.dp))` 中唯一参与测量的子项。
   它保持 `fillMaxSize()` 与正文内边距（body inset）——即下文后续审查所述的头部预留空间
   （headroom）与感知外层控件的尾部间距，它取代了初版恒定的 `contentPadding(bottom = 32.dp)`；
   列表自身的几何尺寸与外层控件（chrome）是否可见完全无关。
2. **浮层化外层控件。** 在外层 `Box` 内部、于列表之后声明，因而绘制在其上方：
   顶层（文档标题——仅在阅读菜单打开时显示——加上过期、降级与链接提示以及搜索栏）与
   底层（1 dp 分隔线上的低调贴底进度页脚，以及打开时的既有阅读菜单）。所有层都是不透明的
   `surface` 且完全静态：无动画、无透明、无水波纹（ripple）（REQ-0004）。
3. **按可见高度（visible height）翻页。** 浮层不再压缩视口，因此实测的顶部/底部外层控件高度
   会通过既有的 `PageTurn.visibleHeightPx` 从翻页距离中扣除（REQ-0005 FR-2）。因此翻页仍然移动
   `visibleHeight − overlap`，且绝不会跳过被菜单或提示遮住的内容。
4. **跳转保持可见。** 搜索跳转或同文档锚点跳转会落在列表首项，而该位置现在位于浮动条之下。
   `revealBelowTopChrome()` 会把这类跳转按实测顶部外层控件高度向下偏移，使落点块可读
   （此前的流式条天然提供了这一效果）。
5. **首次运行的触控区（tap zone）引导（ISS-020）。** 一个一次性、不透明、无动画的浮层
   用阅读器真实的三等分区域（上一页 / 阅读菜单 / 下一页）配合随模式变化的副标题来说明三个
   区域，点击任意位置或「知道了」即可关闭。整个浮层会消费该次点击，因此用于关闭的触摸
   绝不会翻页。关闭状态会被持久化（`tapZonesHintSeen`），判断逻辑是纯函数
   `TapZones.shouldShowHint`，因此提示每次安装最多出现一次，且绝不会在读取到已存值之前闪现。

位置语义未改动：`restoreBlock`、目录/锚点跳转、搜索跳转、节流保存观察器以及排版 `keepBlock`
接缝仍然都作用于普通块索引。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `ReaderContent` 重构为冻结列表 + 浮层化外层控件；按可见高度计算翻页距离；`revealBelowTopChrome`；新增 `TapZoneHintOverlay`/`HintZone` |
| `app/src/main/kotlin/io/leaflet/reader/SettingsStore.kt` | `tapZonesHintSeen` 流 + `setTapZonesHintSeen`（DataStore） |
| `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt` | `SettingsViewModel.tapZonesHintSeen` / `dismissTapZonesHint()` |
| `core/storage/src/main/kotlin/io/leaflet/storage/prefs/ReaderSettings.kt` | `TAP_ZONES_HINT_SEEN_DEFAULT` |
| `core/reader/src/main/kotlin/io/leaflet/reader/TapZones.kt` | `TapZones.shouldShowHint`（纯首次运行判断） |
| `core/reader/src/test/kotlin/io/leaflet/reader/PageTurnTest.kt` | 新增 `TapZonesTest.firstRunHintShowsOnlyWhenUnseenAndNotYetDismissed` |
| `app/src/main/res/values/strings.xml` | 七条首次运行提示字符串 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderInteractionTest.kt` | 新增 UX-06 验收测试（浮层几何、瞬时标题、引导显示/关闭/持久化、提示消费首次点击）：该类由 12 个测试增至 16 个 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt` | `pageSettingsViewModel(tapZonesHintSeen = true)` 将既有页面套件标记为已完成引导 |

## 验证

2026-09-30 于仓库根目录运行：

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:compileFullDebugAndroidTestKotlin :app:compileEinkDebugAndroidTestKotlin
```

| 检查项 | 结果 |
| --- | --- |
| `test`（JVM，全部变体） | PASS — 执行 656 个测试用例、297 个去重测试方法，0 失败、0 错误、0 跳过（基线为 296 个去重方法；本包新增 1 个） |
| `lint` | PASS |
| `ktlintCheck` | PASS |
| `:app:assembleFullDebug` / `:app:assembleEinkDebug` | PASS — `app-full-debug.apk`（28,382,037 B）、`app-eink-debug.apk`（28,784,193 B） |
| androidTest 编译（双变体） | PASS |
| 插桩 UX-06 验收测试 | NOT RUN — 未连接设备/模拟器（`adb devices` 为空，未安装 AVD） |
| E-Ink 硬件（残影、刷新、对比度） | NOT RUN — R3-09 硬件矩阵 |

设备可用后，补测插桩证据的后续命令：

```bash
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

## 逐条标准结果

| UX-06 验收标准 | 状态 | 证据 |
| --- | --- | --- |
| 切换菜单或打开搜索栏时，阅读用 `LazyColumn` 上不产生任何尺寸变化事件或布局过程 | PENDING DEVICE RUN | 结构上：列表是根 `Column` 中唯一参与测量的子项，所有外层控件都是外层 `Box` 内与之同级的浮层；`ReaderInteractionTest.menuAndSearchOverlaysNeverResizeTheReadingList` 断言在菜单打开、搜索打开与菜单关闭过程中列表矩形保持不变（<= 0.5 px） |
| 在 E-Ink 上，打开菜单不出现正文重排或文字跳动 | PENDING DEVICE RUN | 同一结构（无参与测量的几何变化）；E-Ink 波形/残影行为本身属于 R3-09 硬件范围 |
| 文档标题不再永久占用阅读空间 | PENDING DEVICE RUN | 标题仅在菜单打开时渲染于顶部浮层；`ReaderInteractionTest.documentTitleIsTransientChromeNotAPermanentHeader` 断言沉浸模式下标题不存在、打开菜单时存在 |
| 触控区引导在首次启动时出现一次，并能干净关闭 | PENDING DEVICE RUN | `TapZonesTest.firstRunHintShowsOnlyWhenUnseenAndNotYetDismissed`（JVM，PASS）以及 `ReaderInteractionTest.firstRunTapZoneHintAppearsDismissesAndIsPersisted` 与 `tapZoneHintConsumesTheFirstTapWithoutTurning` |
| 翻页仍使用实际可见高度（REQ-0005 FR-2） | PASS (JVM) | `PageTurn.visibleHeightPx` 现已接入实测外层控件高度；`turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport` 仍从两次实测翻页反推有效视口 |

## 限制与后续工作

- 浮层会在两处遮挡正文内容。各类提示与搜索栏会把自身实测高度预留为列表的头部预留空间
  （headroom），因此绝不会遮住正文文字（包括第 0 块中的命中——任何滚动都无法将其揭示，
  CR-20260930-03）；阅读菜单与标题不预留头部空间，打开时会遮住底部/顶部，翻页距离已将其
  计入。尾部间距会预留实测的底部外层控件高度，因此最后一行总能滚动到页脚和已打开菜单之外
  （CR-20260930-01）。尚未在硬件上测量。
- 已交付的保证是：切换外层控件时，阅读列表的实测几何尺寸不可能改变（已在合成页面上断言）。
  严格的「零布局过程」结论需要布局检查器逐次计数，而本环境无法产出该证据。
- 底部进度页脚会把触摸透传给触控区（点击其右侧三分之一会翻页）。目前接受该行为；
  后续可让页脚吸收触摸。
- 阅读器内部仍未应用系统窗口内边距（system insets）（状态栏/导航栏重叠属于
  ISS-001/ISS-002，由 UX-01 负责）。浮层条继承了此前流式外层控件的同一行为——既无回归，
  也未修复。
- `values-zh` 尚不存在，因此七条新增提示字符串只有英文；双语字符串资源由 UX-10 负责。
- UX-06 停工条件所要求的模拟器截图与布局检查器（layout inspector）证据未在本机产出。

## 后续代码审查 — 2026-09-30

[阅读器代码审查](code-review-2026-09-30.md) 针对提交
`5a754292c1ea02f33af0cb917f5cf24c29dbb763` 记录五个 P2 问题：页脚遮挡下的提前末尾钳制、
标题下的目录落点、首块搜索遮挡、外部链接包可见性（package visibility）预检查，以及
E-Ink 默认连续滚动。前三项影响 UX-06 验收；后两项涉及同一提交首次纳入版本控制的其他源码。
静态路径已确认，隔离提交的定向 JVM 检查执行 249 个用例，无失败或错误。

**这五个问题均已在那个提交之上修复**（见审查报告的解决部分）：

- 列表的正文内边距现在为 `contentPadding(top = 各类提示/搜索栏头部预留空间, bottom = max(32 dp, 实测底部外层控件 + 8 dp))`，翻页钳制改用列表的真实滚动边界（`canScrollForward` / `canScrollBackward`，CR-01）。这两个值都不会移动任何条目：顶部内边距只在文档开头提供滚动空间，底部内边距位于最后一个条目之后，而阅读菜单被有意排除在头部预留空间之外，因此切换菜单仍不会让正文重排。阅读器与 Settings 屏幕的预读阅读模式也改为取自显示模式（CR-05），外部链接移交不再以 `Intent.resolveActivity` 作为门槛（CR-04）。
- 新增回归：JVM `SettingsStoreReadingModeTest`（3 个测试，已执行且通过）与 `ReaderSettingsTest.firstRunReadingModeFollowsTheDisplayMode`；设备测试 `ReaderInteractionTest.tailTurnRevealsTheLastLineAboveTheFooterAndTheOpenMenu`、`tocLandingIsNotCoveredByTheTopChrome` 与 `firstBlockSearchMatchStaysBelowTheSearchBar`，它们通过 `ReaderChromeTags` 语义标签测量外层控件的真实 `boundsInRoot`。这些设备测试在双变体下均可编译，但未运行（NOT RUN，无设备/模拟器），CR-04 的区分性场景仍需要一台拥有查询不可见处理应用的设备。
- 本报告中所说的「切换时零重排」应这样理解：切换菜单时，阅读列表的实测几何尺寸与正文条目位置不可能改变；打开搜索栏（或出现提示）会增加实测的头部预留空间，这是有意为之的一次性内边距，目的是让内容保持可见而不是被遮住。
