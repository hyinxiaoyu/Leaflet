# 阅读位置与链接审查 — 2026-10-01，第 3 轮

[English](code-review-2026-10-01-round-3.md) | 中文

**实现响应（2026-10-01）：用户已授权直接修复。三条保留失败现均通过；43 个定向 JVM 用例通过。两个分发变体的主源码及 androidTest Kotlin/Compose 源码通过本地编译，改动文件 ktlint 通过。Gradle/APK/设备验收仍待补。** 见[实现响应](#implementation-response-2026-10-01)。下文原始发现对应修复前快照。

## 结论与范围

独立复现两个新增 P2 缺陷：未变化文档的非零块内位置被无移动补存清零，以及相对链接延迟解析在切文档后仍发出导航。既有 P1 保存顺序问题 CR-20261001-04 也仍为 OPEN：淘汰会删除有未完成写入的文档排序状态。这是同一问题下的额外反例，不新增重复缺陷编号。

最新的修复合并与游标投影变更通过了此前保留的反例及产品回归。CR-20261001-05 现通过定向 JVM 证据；真实提供方/Room/设备关闭仍待补。本次仅审查既有阅读器/存储/导航行为。未审查或增加新需求与产品实现。

HEAD 仍为 `5a754292c1ea02f33af0cb917f5cf24c29dbb763`；测试使用 `/tmp/leaflet-review4.jkop4e/repo` 下的工作区快照（snapshot）。下文位置对应该快照，而非已提交版本。见[此前审查与验收记录](code-review-2026-10-01-round-2.md)。

| 编号 | 优先级 | 问题 | 证据 | 状态 |
| --- | --- | --- | --- | --- |
| CR-20261001-04，未完成写入淘汰 | P1 | 后续八个文档淘汰了未完成文档的修复目标 | A 的最新第 5 块被旧第 2 块覆盖 | OPEN |
| CR-20261001-08 | P2 | 无移动补存清除已存块内偏移 | 预期 300，实际 0 | OPEN |
| CR-20261001-09 | P2 | 旧文档延迟链接解析在切换后发出导航 | 预期无回调，实际 `[target]` | OPEN |

## CR-20261001-04 — 未完成写入必须经受排序记录淘汰

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:435–447`、`463–475`；`MAX_TRACKED_SAVE_DOCUMENTS` 为 8。
- **触发与证据：** 暂停 A 的第 2 块写入，让 A 的新第 5 块完成，再打开并保存其他八个文档，最后释放 A 的旧写入。[ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) 中的 `pendingOlderWritesMustNotLoseTheirRepairTargetAfterEightOtherDocuments` 确认释放前 A 仍为 5，之后失败为 `expected:<5> but was:<2>`。
- **原因：** 有界 `newestRequestByDocument` Map 未检查未完成保存，就移除了 A 及其 `newestCompletedByDocument` 条目。晚到写入仍可修改 A 的持久化行，但 `repairTargetFor()` 已没有较新快照可用于修复。因此合并循环写入旧地址后就结束。
- **影响：** 保存延迟期间快速切换多个文档，仍可能使辅助阅读进度回退。原始文档不受影响。
- **最小修复方向与关闭条件：** 限制空闲排序记录，而非淘汰活跃写入拥有的状态；或采用不依赖保留修复目标的持久化策略。相关完成全部处理前应保留排序状态。保留的淘汰回归必须与派发、重叠修复、跨文档快照及正常保存基线一起通过。新增场景仍归属 CR-20261001-04。

## CR-20261001-08 — 补存清除块内位置

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:852–862`、`918–925`；`ReaderScreen.kt:225–227`、`255–260`。
- **触发与证据：** 为未变化内容预存 version-1 位置：第 2 块、偏移 300；打开后不报告任何移动，立即补存。解析器复现含八个段落，每段超过 700 字符，因此预存字符偏移在块内。[ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) 中的 `aNoMovementFlushMustPreserveTheStoredInBlockOffset` 确认恢复第 2 块且补存前存储中仍为偏移 300，补存后变为 0。
- **原因：** `PositionRestore` 返回了已存偏移，节流器也以其初始化，但当前状态/保存快照仅保留块索引。`persist()` 始终写 `blockOffset = 0`。页面同样只按 `restoreBlock` 恢复，只观察 `firstVisibleItemIndex`，因此长块内部移动没有持久化块内地址。
- **影响：** 即使用户没有滚动，既有非零位置也会丢失。源码路径还阻止对超过一屏的块进行精确恢复。本次未测量设备上的像素级位移。
- **需求：** 这是既有 [REQ-0003](../requirements/REQ-0003-reading-position.md) FR-1/FR-3 与 AC-1 的块/偏移契约，并非新增分页或阅读器功能。
- **最小修复方向与关闭条件：** 在加载、界面观察、快照捕获及持久化全过程保留完整逻辑位置。使用既有位置映射解释偏移；不能用原始全局 `scrollY` 代替持久化契约。验证无移动补存、长块内部阅读/翻页、重开/进程重建及排版降级。保留的模型/存储回归必须转为通过；完整界面验收仍需补齐。

## CR-20261001-09 — 延迟链接覆盖后来打开的文档

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:646–669`。
- **触发与证据：** 在 A 中点击相对文档链接并暂停 `resolveRelative`，完整打开 B 后释放 A 的解析。[ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) 中的 `aLinkResolvedAfterADocumentSwitchMustNotNavigateFromTheOldDocument` 确认释放前当前文档已为 B，但之后仍收到 `onOpenDocument(target)`：`expected:<[]> but was:<[target]>`。
- **原因：** 链接任务启动于共享 ViewModel 作用域，没有保留任务，也没有在 IO 解析后重新验证文档/加载/导航归属。`openJob?.cancel()` 处理的是加载任务，并非此任务。即使用户已切文档，成功解析与片段移交仍使用旧回调（callback）。
- **影响：** 生产导航回调可能在用户后来选中的文档之上打开 A 的目标。测试证明过时回调，不是真实 NavController 过渡或未经授权访问。
- **需求：** 既有阅读器契约要求过时后台结果不能替换更新文档状态；[REQ-0009](../requirements/REQ-0009-local-resources-links.md) 定义了相对链接应从所属阅读器/文档导航。
- **最小修复方向与关闭条件：** 将链接解析、提示及待处理片段绑定于当前文档/代次（generation）及导航归属。取消被取代的链接工作并拒绝过时完成，包括路由退出。覆盖切文档、返回导航、快速连续链接及延迟失败提示。保留合法库内链接与片段导航，不新增全库链接检查器。

## 既有修复与验证

使用 JDK 17.0.9、Kotlin 2.0.21、lifecycle 2.8.7、Coroutines/Test 1.10.2 与 JUnit 4.13.2，重新编译成功。受影响的 ViewModel、游标解析器、扫描器、仓储、存储替身与定向测试均基于快照编译；未变化的本地项目/解析器 jar 提供依赖。合并直接 JUnit 运行返回 exit 1：`Tests run: 32, Failures: 3`。

| 检查组 | 结果 |
| --- | --- |
| 此前保留的复现与验收测试 | PASS 8/8：`ConcurrencyReviewTest` 3、`AcceptanceBoundariesTest` 2、`LibraryRefreshAcceptanceTest` 1、`RepairAndProjectionAcceptanceTest` 2 |
| 当前产品保存回归 | PASS 7/7：`ReaderViewModelSaveSnapshotTest` 3、`ReaderSaveOrderTest` 2、`ReaderSaveOrderDispatchTest` 1、`ReaderSaveRepairTest` 1 |
| 当前产品元数据/选择回归 | PASS 14/14：`RecentMetadataPublicationTest` 2、`LibraryBrowseSelectionTest` 2、`TreeRowClassificationTest` 7、`TreeEntryProjectionTest` 3 |
| 新增 `ReaderContinuationReviewTest` | FAIL 3/3：偏移清除、过时链接回调、活跃写入淘汰 |
| 合计 | 执行 32 个；29 通过；3 失败 |
| Gradle、更广门禁、Room/SAF/提供方/设备/界面/E-Ink 验证 | 独立 NOT_RUN；不宣称全仓或设备验收 |

此前游标代理投影测试现已通过实际重新编译的游标循环（其缺失 ID 分支避开 URI 构造）。仅缺 MIME 的分类器/扫描器场景也通过。这支持 CR-20261001-05 的 JVM 修复；有效 ID 的 Android URI 构造、提供方 IPC 与 SQLite 级联仍未验证。CR-20261001-06/07 保持 JVM 层面接受。CR-20261001-04 因未完成写入场景仍失败而继续拒绝关闭。

## 源码身份与产物

| 被审查源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `6fda75d018f431ac8073ebb80c9afcce6a7b2eb6bcf35a1f1d500c4c21cd6e32` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `ee0bed9b95a88856c278ba936cfa9cf4bc4f2e580a3f75afc5d2fe66ffcab1a6` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `ec53a13709241941139622ea9ca3f485448ab7eb6ec27d5600d3f099813b5b29` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |

- 临时快照、提取/复制依赖、编译产物与日志仅创建于 `/tmp/leaflet-review4.jkop4e`，验证后已删除。
- 有价值的复现源码刻意保留于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt`。它是审计证据，不接入产品测试/构建，不新增依赖。
- 英文报告、完整中文配对文件，以及此前报告配对文件的最新状态入口，为文档交付。本次未实施产品修复或新需求实现；保留并行工作与既有构建产物。

<a id="implementation-response-2026-10-01"></a>

## 实现响应 — 2026-10-01

用户的直接修复请求授权实现 CR-20261001-04 活跃写入淘汰子项、CR-20261001-08 与 CR-20261001-09。编辑前重跑原始失败套件：32 个测试，29 通过、3 失败，复现已记录的偏移 300 → 0、迟到 `target` 导航及第 5 块 → 第 2 块覆盖。定向修复保留既有位置版本、数据库格式、分发变体（flavor）、依赖与原文安全性。

### 变更

- **保存归属：** IO 派发前统计待处理请求。缓存裁剪跳过有未完成写入的文档，并在任务完成时重新执行，包括协程启动前取消。八文档上限用于空闲记录；活跃写入器在所有任务完成前保留最新快照/完成状态。既有请求时序号与合并修复维持不变。
- **逻辑位置：** ViewModel 加载/当前状态/保存快照/补存现携带块索引与字符偏移。同块旧式观察保留既有偏移；真实界面移动报告两个坐标。观察绑定所属文档。`readingLocationFor` 在节流间隔内暴露最新地址，用于重新挂载。显式块/锚点跳转将偏移重置为零。
- **界面投影：** `BlockPositionGeometry` 使用当前 Compose/原生文字行起点与相对文本原点，计入标签/间距。列表先测量目标块，再即时恢复字符地址，随后抑制恢复回声直到真实移动。列表按文档归属，阅读模式变化时保留状态。没有单一文本布局的复合条目使用有界字符长度/高度回退；复合/媒体精确视觉准确性仍是设备门禁。
- **导航归属：** 保留链接任务，被取代/打开/重载/退出时取消，并在成功或提示发布前检查文档/加载/导航归属。待处理片段记录目标；无关阅读器不能消费。允许源路由先销毁、目标稍后打开，而不丢弃已接受片段。片段落点在同一初始滚动效果（effect）中优先于已存偏移恢复。

### 变更文件与回归证明

| 文件 | 与任务相关的变更 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | 待处理保存归属、完整逻辑地址、链接取消/归属、目标绑定片段 |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | 实测位置捕获/恢复、初始回声保护、按文档归属的列表、退出时取消导航 |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometry.kt` | 纯行/字符映射与有界复合条目回退 |
| `app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderContinuationFixTest.kt` | 7 个回归：保留失败、完整地址更新、旧文档观察、退出取消、片段移交 |
| `app/src/test/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometryTest.kt` | 4 个映射回归：字体布局、文本原点、回退边界、已销毁条目测量 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLogicalPositionPageTest.kt` | 2 个设备场景：实际长段落可见行重开及 TXT 块内翻页；已编译，NOT_RUN |

保留既有无关工作区变更。CHANGELOG、阅读器架构与 REQ-0003/0009 跟进证据均同步更新中英文；需求状态不推进为 DONE。

### 验证

| 检查项 | 实际结果 |
| --- | --- |
| 修复前复现 | 按预期 FAIL：执行 32 个，3 条保留失败 |
| 最终直接 JVM 回归运行 | PASS：执行 43 个，0 失败；包括保留审计用例、既有产品回归与新增 11 个 JVM 测试 |
| 改动文件 ktlint 1.0.1 | PASS；函数命名沿用应用既有 Compose 例外 |
| Full/Eink 主源码 Kotlin/Compose 编译 | PASS；使用 Kotlin 2.0.21、固定版本 Compose 编译插件、既有解析后的 lint-model 依赖 jar/R jar 及每变体生成的 BuildConfig |
| Full/Eink androidTest Kotlin 编译 | PASS；不宣称场景已执行 |
| 标准 Gradle 门禁 | 未能启动：当前沙箱中 `FileLockContentionHandler` → `java.net.SocketException: Operation not permitted` |
| APK 构建、Gradle lint/全套、设备/界面/E-Ink 硬件 | NOT_RUN；不宣称 APK/设备通过或完整验收 |

首次改动文件格式化报告两处测试源码行长度违规；已修正，最终格式化/检查返回 exit 0。其他源码既有弃用/opt-in/图片加载器警告保留。未变化的 core/model/parser 支持 jar 来自此前本地构建；受影响的 ViewModel/界面/辅助源码重新编译。编译输出是临时验证产物，不是可分发 APK。

尝试的标准门禁为：

```bash
./gradlew --offline --no-daemon \
  --project-cache-dir /tmp/leaflet-fix.tAUbh0/gate-cache \
  :app:testFullDebugUnitTest :app:testEinkDebugUnitTest \
  :app:assembleFullDebug :app:assembleEinkDebug lint ktlintCheck
```

`JAVA_HOME` 指向 JDK 17，`GRADLE_USER_HOME` / Android 用户状态均位于隔离临时目录。环境允许后的设备跟进命令：

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLogicalPositionPageTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLogicalPositionPageTest
```

三项问题已有 JVM/编译证明的修复，设备验收待补。这是实现响应，不是另一个独立审查方的关闭结论，也不是产品发布。未提交。

最终源码身份：

| 已修复源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `fb97c8e1b24065b4af376cdbc80b0658f609ac4af668c1716534320531428631` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `a44d2ba7a42cef979b3188f491e990bc296b3ac6528a1ffa8f88b1ddd470748a` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometry.kt` | `cdc8de530b5a2184c190abd7c1ccc5aae1e943a8c0fa62e366b2043501272cb7` |

### 产物与清理

临时基线副本、编译/格式化脚本、复制/提取依赖、Gradle wrapper/用户目录、日志与编译类仅创建于 `/tmp/leaflet-fix.tAUbh0`，完成时已删除。新增辅助代码及三个产品测试文件是上述路径中的刻意保留交付。保留既有报告复现、截图、工作区变更及此前构建产物；未删除无关文件。
