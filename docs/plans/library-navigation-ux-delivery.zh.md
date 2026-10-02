# 资料库导航体验交付（NB-00–NB-05）

[English](library-navigation-ux-delivery.md) | 中文

日期：2026-10-01
包：UX-05 → NB-00–NB-05（[计划](library-navigation-ux-plan.zh.md)、[任务](library-navigation-ux-tasks.zh.md)）
需求：[REQ-0012](../requirements/REQ-0012-library-navigation.zh.md)（`READY`，对 REQ-0001/REQ-0004 的局部补充）
基线：`HEAD = 5a754292c1ea02f33af0cb917f5cf24c29dbb763`，加上未提交的 UX-06/UX-07 与审查跟进工作，原样保留
状态：DELIVERED — 软件已验证，设备待补

## 结果

文件夹浏览现在可以通过面包屑 (breadcrumbs) 导航；扫描状态变化不再移动文件列表；每个文件/文件夹行都是尺寸充足、类型明确的触控目标。

- **面包屑（NB-02，AC-1/AC-2/AC-3）。** 浏览页在高度受限、可横向滚动的一行中显示资料库根目录与当前前缀每个路径分段对应的一项。点击非当前项会通过受校验的 ViewModel 动作打开该精确前缀；当前项为粗体、带有状态描述且没有点击动作。即使标签在视觉上被省略号截断，完整名称仍保留在无障碍树中。
- **带校验的祖先状态（NB-01）。** `Breadcrumbs.kt` 生成这些项，并按整段路径校验目标（`a` 不是 `ab` 的祖先）。点击以 `AncestorRequest` 形式携带渲染时的资料库与前缀，`LibraryViewModel.openAncestor` 要求两者仍是当前值才会移动；兄弟目录、越出目录树、无关前缀、外部传入的路径、来自其他资料库的目标、在用户已离开的前缀上渲染的请求，以及当前目录，都会被忽略且不改变状态（AC-2、NAV-ACC-01）。整个过程不涉及 SAF 查询或文件读取。
- **保留的扫描状态区域（NB-03，AC-4）。** 由同一个区域承载状态文本与唯一一个动作（Refresh / Cancel / Retry），覆盖所有状态。文本始终预留两行、动作始终存在，因此 `IDLE → SCANNING → CANCELLING → CANCELLED`、`SCANNING → SUCCEEDED`、`SCANNING → FAILED → Retry` 都无法改变该区域的尺寸——也就无法移动它下方的文件列表。恢复提示仅由 `progress` 承载；浏览页不再在列表上方渲染第二个随状态出现的提示块。
- **行（NB-04，AC-5）。** 每个文件/文件夹行整行可点击，高度至少 48 dp，并随换行名称或 200% 字号增长。文件夹行与文档行带有仓库内自绘矢量图标和可读的 MD / MARKDOWN / TXT 标签，因此区分不只依赖颜色。点击文件仍把同一文档 id 交给 Reader。
- **受限的列表视口。** 浏览 `LazyColumn` 现在是 `weight(1f)` 视口，而不是屏幕 `Column` 中无权重子项：目录内容较多时它会滚动并让 Back 控件留在屏幕上，且它正是稳定性检查所测量的列表。切换资料库或前缀时它会被滚回顶部，因此已滚动的目录不会把偏移带入下一个目录（FR-6/AC-6）。

## 需求门禁（NB-00）

[REQ-0012](../requirements/REQ-0012-library-navigation.zh.md) 作为 READY 局部需求创建，并把计划的 AC-N1–AC-N6 承接为 AC-1–AC-6。计划的 [NB-00 就绪记录](library-navigation-ux-plan.zh.md#nb-00-readiness-record-2026-10-01) 记录了 UX-01（窗口安全区）、UX-02（E-Ink 调色板）与 UX-04（统一资料库屏）**尚未交付**，并记录了使 NB-02/03/04 仅在 `LibraryBrowseScreen` 内推进的依赖收窄。用户在 2026-10-01 明确授权在前置未完成的情况下继续；UX-01/UX-02/UX-04 仍归各自工作包所有，此处不作任何完成声明。

## 变更文件

| 文件 | 变更 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/Breadcrumbs.kt` | 新增——`BreadcrumbSegment`、`AncestorRequest`、`breadcrumbSegments`、`isAncestorTarget`、前缀归一化 |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/BrowseLevel.kt` | `childPath` 改为 `internal`，让面包屑构建复用同一条路径拼接 |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | 带来源校验的 `openAncestor(AncestorRequest)`；`cancelRefresh` 仅在存在活动扫描时生效；浏览失败/取消改为经 `progress` 上报；移除浏览 `notice` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryScreens.kt` | `BrowseTags`；面包屑行（携带来源请求）；保留的 `ScanStatusRegion`；≥48 dp 的类型化 `BrowseRow`；受限列表视口与前缀变化回顶 |
| `app/src/main/res/values/strings.xml` | 新增浏览标签（面包屑根/分隔符/当前项、行文件夹/文档、扫描进度） |
| `app/src/main/res/values-zh/strings.xml` | 新增——本切片标签与状态区动作的简体中文对应文本 |
| `app/src/eink/res/values/strings.xml`、`app/src/full/res/values/strings.xml` | 在 UX-10 交付完整对齐前抑制 `MissingTranslation` |
| `app/src/main/res/drawable/ic_folder.xml`、`ic_document.xml` | 新增——自绘 24 dp 图标，无图标依赖、未复制第三方路径 |
| `app/src/test/kotlin/io/leaflet/reader/ui/library/BreadcrumbTest.kt` | 新增——8 个纯映射/校验方法 |
| `app/src/test/kotlin/io/leaflet/reader/ui/library/LibraryAncestorNavigationTest.kt` | 新增——11 个 ViewModel 转换方法（含 NAV-ACC-01 来源校验回归） |
| `app/src/test/kotlin/io/leaflet/reader/ui/library/LibraryScanCancelTest.kt` | 新增——3 个 NAV-ACC-02 取消所有权方法 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` | 新增——11 个针对真实页面的用例（真实扫描器 + 内存 Room 索引；逐相位门控、按行定位断言、十层/窄屏、200% 长中文名、空目录、两种运行模式、前缀回顶） |
| `docs/requirements/REQ-0012-library-navigation.md` / `.zh.md` | 新增——READY 局部需求及双语配对 |
| `docs/requirements/README.md` / `.zh.md` | REQ-0012 索引条目 |
| `docs/plans/library-navigation-ux-plan.md` / `.zh.md` | 状态、NB-00 就绪记录、依赖收窄 |
| `docs/plans/library-navigation-ux-tasks.md` / `.zh.md` | 交付状态与逐卡小结 |
| `docs/plans/ui-ux-optimization-tasks.md` / `.zh.md` | UX-05 状态 |
| `CHANGELOG.md` / `CHANGELOG.zh.md` | 用户可见变更条目 |

## 验证（2026-10-01）

macOS ARM64、JDK 17、Gradle 8.13，使用 `--offline` 走既有依赖缓存。

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 全量 JVM 套件（所有模块与变体，强制重跑） | 删除 test-results 目录后执行 `./gradlew --offline --no-build-cache --max-workers=1 :app:testFullDebugUnitTest :app:testEinkDebugUnitTest :app:testFullReleaseUnitTest :app:testEinkReleaseUnitTest :core:model:test :core:storage:testDebugUnitTest :core:storage:testReleaseUnitTest :core:markdown:testDebugUnitTest :core:markdown:testReleaseUnitTest :core:reader:testDebugUnitTest :core:reader:testReleaseUnitTest :core:eink:testDebugUnitTest :core:eink:testReleaseUnitTest` | PASS——844 个用例、347 个去重方法，0 失败、0 错误、0 跳过 |
| NB-01/NB-03 新回归 | 含于上一行 | `BreadcrumbTest` 8 个、`LibraryAncestorNavigationTest` 11 个、`LibraryScanCancelTest` 3 个方法，全部通过 |
| 独立验收复检（用其探针与运行器） | `python3 docs/plans/review-evidence/2026-10-01-navigation-acceptance/run_navigation_checks.py --java …/jdk-17/…/java` | PASS——退出码 0：既有 21 个测试 OK，`NavigationAcceptanceProbe` 两例 OK（修复前为 2/2 失败）。见 [recheck-after-fix.txt](review-evidence/2026-10-01-navigation-acceptance/recheck-after-fix.txt) |
| Android Lint（所有变体） | `./gradlew --offline lint` | PASS，前提是下述有范围的 `MissingTranslation` 处理 |
| ktlint | `./gradlew --offline ktlintCheck --continue` | PASS（所有模块） |
| 两个 debug 发行版本 | `./gradlew --offline :app:assembleFullDebug :app:assembleEinkDebug` | PASS——`app-full-debug.apk` 28,386,595 B，`app-eink-debug.apk` 28,827,351 B |
| 插桩编译（两个发行版本） | `:app:compileFullDebugAndroidTestKotlin`、`:app:compileEinkDebugAndroidTestKotlin` | PASS |
| 插桩页面验收 | `:app:connectedFullDebugAndroidTest`、`:app:connectedEinkDebugAndroidTest` | **NOT_RUN**——没有连接设备或模拟器（`adb devices` 为空） |
| 真实 E-Ink、硬件刷新/残影、TalkBack | 手工 | **NOT_RUN** |

插桩 `LibraryBrowsePageTest` 使用真实 `LibraryViewModel`、真实 `LibraryScanner` 与内存 Room 索引驱动真实 `LibraryBrowseScreen`；只有 SAF 元数据来自一个受门控的假 `TreeLister`。它覆盖祖先点击、当前分段空操作、≥48 dp 的面包屑/行测量、按行定位的类型提示、文档 id 交接、200% 字号下的长中文名、窄屏十层路径可达性、空目录下的状态区稳定性、两种运行显示模式、前缀切换回顶，以及完整的扫描状态矩阵（含列表视口、滚动偏移与可见行测量，`SCANNING` 与 `CANCELLING` 逐相位确定性地测量）。它可以编译，但尚未运行。

## 对独立验收的整改响应（NAV-ACC-20261001）

[独立验收](library-navigation-ux-acceptance-2026-10-01.zh.md) 给出 **NOT_ACCEPTED**，包含两个已复现的生产缺陷与四项页面就绪性问题。两个缺陷均已修复，四项页面问题均已处理。验收报告本身保持原样，因此在其被重新运行之前，其结论依然有效。

| 发现 | 修复 | 证据 |
| --- | --- | --- |
| **NAV-ACC-01**（P1）——在资料库 A 生成根面包屑，却让资料库 B 离开了 `Docs/Notes` | 点击现在以 `AncestorRequest(target, libraryId, prefix)` 携带渲染时的状态，`openAncestor` 要求该资料库**与**该前缀仍是当前值才会执行 | `LibraryAncestorNavigationTest` 新增 `aRootTargetFromAReplacedLibraryIsIgnored`、`aTargetFromTheSamePathInAnotherLibraryIsIgnored`、`aTargetRenderedAtAnOlderPrefixIsIgnored`；验收自带的探针现已通过 |
| **NAV-ACC-02**（P2）——`cancelRefresh()` 在没有扫描可结束时发布了 `CANCELLING` | 仅当刷新任务处于活动状态**且**进度为 `SCANNING`/`CANCELLING` 时才执行取消；扫描已终结后到达的 Cancel 保持终态 | `LibraryScanCancelTest`（3 个 JVM 方法）以及真实扫描器页面用例 `everyScanStateTransitionKeepsTheListGeometryStable`（`SUCCEEDED` 之后的延迟 Cancel） |
| 页面问题——全局查询歧义 | 所有类型提示断言都限定在某一行内（`hasContentDescription(…) and hasAnyAncestor(hasTestTag(row))`），不再对多行做无限定的全局查询 | `LibraryBrowsePageTest.rowsCarryRowScopedTypeCuesAndHandTheDocumentIdToTheReader` |
| 页面问题——门控一次性、`CANCELLING` 未测量 | 门控改为逐次扫描（`holdNextScan`），并在 `NonCancellable` 中等待，因此 `SCANNING` 与 `CANCELLING` 是分别测量的确定性相位 | `everyScanStateTransitionKeepsTheListGeometryStable` |
| 页面问题——夹具覆盖不足 | 夹具现在覆盖 320 dp 宽屏下的十层路径（并断言面包屑可横向滚动）、200% 字号下的长中文名、空目录，以及 `STANDARD`/`EINK` 两种运行模式 | `tenLevelPathKeepsEveryAncestorReachableOnANarrowScreen`、`longChineseNamesAtTwoHundredPercentFontScaleKeepTheRowUsable`、`scanTransitionsInAnEmptyFolderKeepTheStatusRegionStable`、`standardRuntimeModeKeepsTheBrowseControlsUsable`、`einkRuntimeModeKeepsTheBrowseControlsUsable` |
| 页面问题——前缀切换可能保留滚动位置 | 提升 `LazyListState`，并在资料库或前缀变化时重置 | `aDifferentPrefixStartsItsListAtTheTop` |

验收保留的探针与运行器已按新的请求协议调整（过期来源场景保持不变）并对修复后的代码重跑：既有类 `OK (21 tests)`、`NavigationAcceptanceProbe` `OK (2 tests)`，退出码 0（[recheck-after-fix.txt](review-evidence/2026-10-01-navigation-acceptance/recheck-after-fix.txt)）。修复前这两个探针用例为 2/2 失败。这是按其运行器做的自检，不是新的独立评审。

## 保持的契约

空目录仍可选择；`browseLevel` 的排序（目录先于文件、不区分大小写的名称顺序）未改动；切换到不同前缀时列表仍从顶部开始；只读 SAF 边界、扫描取消与上一次索引保留均不变；两个发行版本共用同一实现；未做任何 schema、DAO、迁移或 DataStore 变更。

## 限制与遗漏

- 缺少设备与硬件证据：上述每个插桩用例均为 NOT_RUN，因此 AC-3/AC-4/AC-5 的页面测量只是写好了而未执行，也不作任何 E-Ink 或 TalkBack 结论。
- 独立验收仍标注 `NOT_ACCEPTED`：只有其作者（或一次新的独立验收）才能撤回该结论。上述整改由本切片的回归与其自带运行器的自检支持，而后者不是新的独立评审。
- UX-01/UX-02/UX-04 仍未交付。因此本切片工作在当前（无安全区处理、未统一）的屏幕上；当 UX-04 重构 `LibraryScreens.kt` 时，浏览相关改动可能需要变基。
- `values-zh/strings.xml` 只覆盖本切片的标签，因此三个默认字符串文件以显式注释抑制了 lint 的 `MissingTranslation`；完整语言对齐与该抑制的移除由 UX-10 负责。
- `ktlintCheck` 在本修订版为全绿。首轮交付时它只在一个并行运行的审查修复任务所拥有的文件上失败；该任务随后已使其合规，现在所有模块均通过。
- 并行任务还在 `LibraryViewModel.openLibrary`（CR-20261001-06）中加入了选择代次 (selection generation) 守卫，与 `openAncestor` 同处一个文件；两处改动并存，本交付既未包含也未修改该修复。
- 未提交、未宣称发布，本切片也未启动下一个 UX 工作包。

## 临时数据

本切片未创建任何临时夹具、日志、缓存、模拟器副本或截图；全部工作都留在仓库工作区与 Gradle 构建目录内。无需清理。
