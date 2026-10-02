# 并发与扫描安全审查 — 2026-10-01，第 2 轮

[English](code-review-2026-10-01-round-2.md) | 中文

**最新实现响应（2026-10-01）：第 3 轮活跃写入淘汰、块内位置及过时链接问题已有修复，43 个定向 JVM 用例通过，双变体源码编译通过；设备/完整 Gradle 验收待补。** 见[第 3 轮审查与修复证据](code-review-2026-10-01-round-3.md)。较早独立结果与执行方响应保留为历史记录。

## 结论与范围

独立复现四个新增缺陷：两个 P1 辅助数据问题，以及两个 P2 状态发布/导航问题。在被审查工作区快照（snapshot）中均为 OPEN。所识别路径不修改原始文档文件。本次审查不修改产品源码。

> **跟进状态（2026-10-01）：** 四个问题均已在被审查快照之上修复。保留的复现已作为 JVM 回归并入产品套件；保存顺序（save ordering）用例在改动前精确复现（`[5, 5, 2]`），改动后通过。见本报告末尾的[解决结果](#resolution-2026-10-01-round-2-follow-up)。
>
> **第二次跟进（2026-10-01，独立验收之后）：** 独立验收不接受 CR-20261001-04（序号（sequence）在工作线程中分配，而非在调用时（invocation time）分配）与 CR-20261001-05（仅缺少 MIME 类型的行仍被当成文件）。这两个不通过结论对于它们所固定的快照都是正确的。两个机制现在都已修复，它们各自的 `AcceptanceBoundariesTest` 反例已并入产品套件并通过，新的源码身份记录在[执行方对独立验收的响应](#executor-response-to-the-independent-acceptance-2026-10-01)。
>
> **第三次跟进（2026-10-01，调用顺序/类型验收之后）：** 该次验收再次拒绝了两项修复，且拒绝是正确的：补偿写入（compensating write）本身没有排序，而生产游标解析器重建条目时没有带上 `typeUnknown`，在扫描器看到它之前就丢掉了该标记。两者均已修复；三个验收反例全部并入产品套件并通过。见[执行方对第三次验收的响应](#executor-response-to-the-third-acceptance-2026-10-01)。

HEAD 仍为 `5a754292c1ea02f33af0cb917f5cf24c29dbb763`；快照包含针对[上一轮审查](code-review-2026-10-01.md)的未提交修复。其三个跨文档保存快照基线测试通过。新的同文档写入顺序问题机制不同，不重复编号旧的可变块位置捕获问题。本轮不对其他已报告修复作独立关闭验收。

范围：阅读位置并发写入、异步 Library/Recent 状态，以及影响扫描删除决策的生产游标（cursor）分类。行号与身份对应下文快照；并非全仓穷尽验收。

| 编号 | 优先级 | 问题 | 复现结果 | 状态 |
| --- | --- | --- | --- | --- |
| CR-20261001-04 | P1 | 同文档旧保存覆盖新跳转与退出补存 | 写入 `[5, 5, 2]`；最终块预期 5，实际 2 | FIXED — 调用时（invocation time）排序，且每次完成都会合并/收敛（coalesce）到该文档最新请求的地址；三个验收反例全部通过 |
| CR-20261001-05 | P1 | 目录元数据缺失可能授权删除旧子树索引 | 目录被分类为 `false`；删除数预期 0，实际 1 | FIXED — 类型未知（unknown type）显式化，并在解析器投影（projection）后保留下来；三个验收反例全部通过；真实提供方运行 NOT_RUN |
| CR-20261001-06 | P2 | 较早的慢速资料库打开覆盖当前资料库 | 选择预期 `b`，实际 `a` | FIXED — JVM 回归 PASS；刷新完成用例需要扫描器替身，NOT_RUN |
| CR-20261001-07 | P2 | 最近阅读元数据查询完成不更新可观察行 | 健康查询已完成；预期 `AUTH_LOST`，实际 `UNKNOWN` | FIXED — JVM 回归 PASS；视觉提示 NOT_RUN |

## CR-20261001-04 — 不可变保存仍会乱序完成

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:407–414`、`804–835`。
- **触发：** 在 A 中保存第 2 块，将该写入阻塞于 ReaderStore 内，跳到第 5 块并完成第 5 块的退出补存。随后释放旧写入，期间不切文档或加载代次（generation）。
- **影响与证据：** [ConcurrencyReviewTest.kt](review-evidence/2026-10-01-round-2/ConcurrencyReviewTest.kt) 的 `anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush` 确认释放旧写入前补存已经完成。写入顺序为 `[5, 5, 2]`，持久化地址最终是第 2 块。因此即使补存成功，最新位置仍可能丢失。
- **原因：** 每次 `queueSave()` 都启动独立 IO 协程（coroutine）。不可变快照保护身份，却既不按请求顺序串行写入，也不拦截过时完成。三个写入属于同一文档、同一会话代次，都满足 `isCurrent()`；旧完成还可能替换 `currentStored`。
- **最小修复方向与关闭条件：** 使用保持请求顺序的串行写入器，或带序号判断的原子持久化；补存应等待最新已接受地址。仅在乱序派发之后获取互斥锁（mutex），不足以建立调用顺序。覆盖重叠跳转、节流滚动保存、退出补存、写入失败与切文档。保留的失败必须转为通过，同时保持已修复的跨文档捕获问题不回归。这是 ReaderStore 层 JVM 证据，不是真实 Room/设备运行。

## CR-20261001-05 — 异常目录变成文件并允许清理

- **源码：** `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:89–97`；`LibraryScanner.kt:223–233`、`359–365`。
- **触发：** 某目录此前已经成功索引。后续提供方游标仍给出目录 MIME 类型与文档 ID，但显示名称为空。
- **影响与证据：** [ScanMetadataReviewTest.kt](review-evidence/2026-10-01-round-2/ScanMetadataReviewTest.kt) 先索引 `folder/keep.md`，再将相同目录身份、空名称、MIME 为 `vnd.android.document/directory` 的游标行交给实际游标解析器。结果为 `isDirectory=false`。下一轮扫描删除原有文档索引：预期 `deleted=0`，实际 `deleted=1`。扫描无法证明子树已消失。原始文件不会被删除；真实 Room 中文档到阅读位置的 CASCADE 意味着辅助位置丢失是额外的源码支持风险，并非另行执行过的断言。
- **原因：** 缺失 ID/名称分支在读取 MIME 前构造 `TreeEntry("", name, false, ...)`，丢弃已知目录/身份信息。扫描器仅对标为目录的异常条目关闭清理。生产分类因此绕过了直接构造 `TreeEntry(..., isDirectory=true)` 的测试所验证的保护。
- **最小修复方向与关闭条件：** 元数据不完整时保留已知类型/身份，将无法分类或无法枚举的子树视为不足以证明条目消失。继续有效索引，同时保留受影响的旧行；不能将部分枚举当成消失清理。通过真实提供方/游标路径覆盖名称、ID、MIME 缺失，并检查目录行与阅读位置。
- **证据限制：** 测试重新编译了生产游标解析器和扫描器。使用代理 Cursor 与仅测试使用的分配/反射调用异常行分支，不构造 Android ContentResolver；该分支不使用传入的 null 树 URI。仓储/扫描器替身为项目既有测试实现。未执行提供方进程间通信（IPC）、正常行 URI 构造及真实 SQLite 级联行为。

## CR-20261001-06 — 较早的资料库加载覆盖后来选择

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt:137–157`。
- **触发与证据：** 开始加载 A，延迟其文档快照，返回并打开 B，让 B 完成，再完成 A。`aSlowEarlierLibraryOpenMustNotReplaceTheCurrentLibrary` 确认释放 A 前 B 已被选中；之后 `browse.libraryId` 为 `a`，而非 `b`。
- **影响：** 当前路由可能显示较早资料库的文档行与路径状态。这是目标错误的导航/状态问题，不是未经授权的源文件访问。
- **原因：** `openLibrary()` 启动未跟踪的任务，既不取消较早打开，也不在 `_browse.update` 前检查选择代次。晚完成还会替换进度订阅。在过时状态已写入后取消进度收集器，不能取消旧元数据加载。
- **最小修复方向与关闭条件：** 跟踪/取消当前浏览加载，在发布元数据/进度前检查其仍属于当前选择。刷新完成也应应用相同的归属检查。覆盖慢 A → 快 B、返回导航、取消加载以及切换后刷新完成。测试使用延迟 LibraryListSource 驱动实际 ViewModel，不宣称 Compose/Room/设备运行。

## CR-20261001-07 — 最近阅读元数据完成不可观察

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt:71–90`、`134–158`；健康提示由 `RecentSection.kt` 渲染。
- **触发与证据：** 发出一条最近阅读记录，暂停资料库名称解析，直到 `rows` 已发布；再完成名称解析，并让健康查询返回 `AUTH_LOST`，期间不改变最近使用列表（MRU list）。`completedRecentMetadataMustPublishHealthAndLibraryName` 确认健康查询已执行，但行状态仍为 `UNKNOWN`；先前发布的资料库名称也仍为 null。
- **影响：** 元数据查询成功后，界面仍可能显示未知资料库，且遗漏授权失效或文件缺失的恢复提示。后来无关的记录列表变化可能通过从缓存重建行而掩盖缺陷。
- **原因：** `rows` 只响应 `_entries`。收集器先发布记录，再调用 `refreshMeta()` 更新普通可变 Map。缓存完成不发送 StateFlow 更新；相等的记录列表也不会强制产生新事件。
- **最小修复方向与关闭条件：** 发布不可变元数据状态并与记录组合，或在查询完成时重建可观察行。防止移除/列表变化之后的过时完成，并定义缓存健康状态何时失效。覆盖记录列表不变时的慢名称/健康查询、实际 `AUTH_LOST`/`GONE` 提示及已移除记录。当前为 ViewModel/JVM 证明，实际视觉提示仍未在设备上验证。

## 验证与身份

环境：macOS ARM64、JDK 17.0.9、Kotlin 编译器 2.0.21、Coroutines/Test 1.10.2、JUnit 4.13.2。源码复制至 `/tmp/leaflet-review3.kEd4Fm/repo`。受影响的 ViewModel、游标解析器、扫描器、仓储及既有存储替身均基于快照重新编译。既有本地 core/model/parser 构建 jar 提供未变化依赖；不宣称重新编译了解析器源码或执行了完整构建。

| 检查项 | 结果 |
| --- | --- |
| 首次直接编译 | 审查源码清单遗漏既有 `BrowseLevel.kt`，导致 `childPrefix` / `parentPrefix` 未解析而失败。补入该既有依赖源码后修正了审查环境；不属于产品缺陷，也未编辑产品代码。 |
| 修正后的直接编译 | PASS，exit 0。 |
| 新增受控回归 | 执行 4 个，4 个失败，复现上述问题。 |
| 既有 `ReaderViewModelSaveSnapshotTest` | 执行 3 个，3 个通过：上一轮跨文档捕获修复与顺序执行基线仍成立。 |
| 合并直接 JUnit 调用 | `Tests run: 7, Failures: 4`，exit 1。该运行证明缺陷，不是通过的门禁。 |
| 完整 Gradle、lint、ktlint、APK/设备/硬件检查 | NOT_RUN。上一轮已确认当前沙箱的 `FileLockContentionHandler` 套接字限制；未重试普通 Gradle。不作新的界面或设备验收声明。 |

编译后执行的 JUnit 入口：

```bash
java -cp "$REVIEW_CLASSES:$REVIEW_DEPENDENCY_CLASSPATH" org.junit.runner.JUnitCore \
  io.leaflet.reader.review.ConcurrencyReviewTest \
  io.leaflet.reader.review.ScanMetadataReviewTest \
  io.leaflet.reader.ui.reader.ReaderViewModelSaveSnapshotTest
```

`REVIEW_CLASSES` 指临时的新编译输出；`REVIEW_DEPENDENCY_CLASSPATH` 包含本地项目 jar、固定版本 JVM 测试依赖、lifecycle 2.8.7 及 Android 35 API jar。保留的证据未放入应用测试源码集。实施修复时应接入适用套件，再执行相应 Gradle 与真实提供方/设备回归。

| 快照源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `d72622a917c4b28f181cca276af9ce48b2f1542c4af3ffba33f3127ae08cf3f6` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `0aa94ae4c7c98ed4a8b40e4ab60b8312cb384797559b8ac7375087fecb239b61` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `301ee0c4f1759efc81f56d8729b043e7dfd5c2a49941f744b5b2a5e82efef743` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `eaf8e3fb65e038a97f83db19db30f3403acae2dc3ad331438cdf334a3d44a521` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `a198ef95b0daa82289dca380492f76fde8f92b18623ddcbef24b8b76a9ee08c9` |

## 产物与清理

- 临时快照、提取/复制依赖、编译产物及日志仅创建于 `/tmp/leaflet-review3.kEd4Fm`，验证后已删除。
- 有价值的复现源码刻意保留于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-round-2/`：`ConcurrencyReviewTest.kt` 与 `ScanMetadataReviewTest.kt`。它们支持本次审计，不增加产品依赖或构建接入。
- 英文报告及完整中文配对文件为文档交付。保留既有源码改动、历史报告与工作区构建产物。本次审查未实施产品修复，也未提交。

<a id="resolution-2026-10-01-round-2-follow-up"></a>

## 解决（2026-10-01，第 2 轮跟进）

四个问题均已在被审查快照之上修复。保留的复现已并入产品 JVM 套件（均为新文件，因此不与并发的 UX-07/REQ-0012 工作冲突）：`ReaderSaveOrderTest`、`TreeRowClassificationTest`、`LibraryBrowseSelectionTest` 与 `RecentMetadataPublicationTest`。保存顺序用例在修复前运行，精确复现了报告中的失败（`writes=[5, 5, 2]`、`expected:<5> but was:<2>`）；修复后全部通过，上一轮的回归也一并保持通过。真实 Room/SAF/提供方与设备验证仍为 NOT_RUN。

**CR-20261001-04 — 保存完成顺序。** 不可变快照修复了身份，却没有修复顺序，因此现在每次写入都从 `queueSave` 携带一个单调递增的请求序号（request sequence）。存储写入仍然是并发的——较新的地址绝不会被较慢的旧写入阻塞，这正是审查自身场景的要求——而顺序在完成时得到修复：

- 在存在更新的请求时完成的写入，不发布保存节流（save throttle），也不发布 descriptor/已存位置缓存（`isNewestRequest`）；
- 当该更新的写入已经落地时，旧写入的完成会重新持久化最新已接受的地址，因为它可能刚刚覆盖了该地址；
- `flushPosition()` 仍返回其自身写入的任务，因此等待它的调用方保留文档所述的「进程死亡前先写入」行为。
回归（JVM，已执行）：`ReaderSaveOrderTest.anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush`，加上上一轮的三个 `ReaderViewModelSaveSnapshotTest` 用例；后者必须持续通过，以免已修复的跨文档捕获缺陷重新打开。

**CR-20261001-05 — 异常目录元数据。** 两条规则，分属于拥有它们的那两层：

- `TreeLister` 依据确实存在的列对每一行分类：抽出的纯函数 `classifyTreeRow` 在「元数据缺失」判断之前先读取 MIME 类型，因此丢失显示名称（或 id）的文件夹仍保持为目录，而不会变成空的「文件」；
- `LibraryScanner` 现在把任何无法完全分类的行都视为遍历的缺失证据不足（`subtreeSkipped`），而不只是那些已经自称是目录的行。既不能证明是目录、也不能证明已消失的行——即缺失 MIME 的情形——再也不能把部分枚举变成消失清理；原有行得以保留，本次运行仍然成功，并把该行作为条目错误上报。
回归（JVM，已执行）：`TreeRowClassificationTest`（5 个用例：无名称目录、无身份目录、无法分类的行，以及两个断言 `deleted = 0` 且保留原文档行的扫描场景）与既有的 33 个 `LibraryScannerTest` 用例。审查中基于游标代理的解析器路径变体，以及 instrumented 的 `realListerMissingColumnsAreEntryErrorsNotAbort`，在此仍为 NOT_RUN。

**CR-20261001-06 — 资料库选择归属。** `openLibrary` 保留其加载任务，递增一个浏览代次（browse generation），并把上一次加载连同任何正在运行的扫描刷新一起取消；只有在它的代次仍然当前时，才发布其行、路径状态与进度订阅。`refresh` 在发布进度、元数据或取消/失败状态之前，会检查其资料库仍是被选中的那一个，因此较慢的旧加载或刷新无法替换用户打开的资料库。回归（JVM，已执行）：`LibraryBrowseSelectionTest`——慢 A → 快 B 会保持 B 被选中并保留 B 的行，重新打开同一资料库仍会发布。刷新完成场景需要一个应用模块没有的扫描器替身（存储替身位于 `:core:storage` 的测试源码集）；它由共享的归属检查覆盖，作为可运行测试仍为 NOT_RUN。

**CR-20261001-07 — 最近阅读元数据发布。** 资料库名称与文件健康（file health）现在是不可变的可观察状态（`RecentMeta`），并与条目列表组合，因此完成的查询会更新已发布的行。条目列表也会立即发布，元数据查询运行在一个会被下一份列表取代的任务中，因此较慢的提供方无法延迟新条目，而在其条目已消失之后才完成的查询也无法将其重新发布；缓存的元数据会被裁剪为当前列出的条目。回归（JVM，已执行）：`RecentMetadataPublicationTest`——一次完成的名称 + 健康查询会更新 `rows`（即审查中失败的用例），而在移除之后才完成的查询不会让该行复活。屏幕上的实际授权失效/文件缺失提示仍为 NOT_RUN。

<a id="independent-acceptance-2026-10-01"></a>

## 独立验收 — 2026-10-01

范围仅为 CR-20261001-04 至 CR-20261001-07。包含执行方修复的当前工作区复制至 `/tmp/leaflet-accept.8x8FT9/repo`。相关生产源码与定向测试使用审查时相同的本地 Kotlin/JVM 工具链重新编译。当前浏览辅助代码仅作为编译依赖纳入；未验收或实现新需求行为。

### 逐项结论

| 问题 | 独立结果 | 证据与剩余门禁 |
| --- | --- | --- |
| CR-20261001-04，P1 保存顺序 | REJECTED；保持 OPEN | 原始完成顺序回归通过，但逆序 IO 启动仍留下 `[5, 5, 2]`。未保持请求顺序。 |
| CR-20261001-05，P1 元数据不完整 | REJECTED；保持 OPEN | 名称/ID 缺失用例通过。仅缺 MIME、ID/名称完整的行仍删除旧子树索引（`expected 0, actual 1`）。 |
| CR-20261001-06，P2 资料库选择 | JVM 层面 PASS；设备验收待补 | 原始慢 A → 快 B 复现与两个产品测试通过。额外的真实扫描器/存储替身测试确认晚到的刷新元数据/进度不能覆盖 B。 |
| CR-20261001-07，P2 最近阅读发布 | JVM 层面 PASS；设备验收待补 | 原始复现与两个产品测试通过：元数据完成更新行；移除后晚到的查询不能重新发布记录。视觉恢复提示仍为 NOT_RUN。 |

该结果不能关闭全部四项。一个 IO 完成调度下复现变绿，不能证明其他调度下的保存顺序；同时删除名称与 MIME 的测试，也不能证明本报告已经要求的仅缺 MIME 规则。

### CR-20261001-04 不通过 — 序号在派发后分配

源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:438–443`。

`saveRequestSeq` 在已启动的 IO 协程内递增，因此序号记录的是 IO 启动顺序，而非 `jumpToBlock`/`flushPosition` 调用顺序。较早调用若较晚启动，就会被错误地提升为最新请求，且不会触发修复。

[AcceptanceBoundariesTest.kt](review-evidence/2026-10-01-acceptance/AcceptanceBoundariesTest.kt) 中的 `newestRequestedPositionMustWinWhenIOJobsStartInReverseOrder` 暂停 IO 派发，依次调用第 2 块 → 第 5 块 → 补存第 5 块，再按最新任务优先释放排队工作。补存完成后，最终存储块仍为 2。直接 JUnit 输出：

```text
invocation order must win, not IO-start order; writes=[5, 5, 2]
expected:<5> but was:<2>
```

这是同一保存顺序验收条件的另一种调度，并非新需求。原始的存储调用后完成顺序场景已修复，之前的跨文档快照测试也仍通过。关闭仍需在请求时建立顺序，并经受 IO 派发与完成乱序；仅在工作线程中分配顺序无法提供该保证。

### CR-20261001-05 不通过 — 未知 MIME 仍被当成文件

源码：`core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:64–79` 与 `LibraryScanner.kt:223–237`。

`classifyTreeRow("dir-id", "folder", null, ...)` 返回 ID/名称有效的非目录行。扫描器的不完整行保护只检查空 ID/名称，因此该行不会将遍历标为不完整。`folder` 没有受支持扩展名，被当成文件跳过，之前索引的子树则被按消失清理。

[AcceptanceBoundariesTest.kt](review-evidence/2026-10-01-acceptance/AcceptanceBoundariesTest.kt) 的第二个测试先索引 `folder/keep.md`，再仅删除父目录分类行的 MIME 值。失败输出：

```text
a row with ID/name but unknown MIME cannot prove a subtree vanished
expected:<0> but was:<1>
```

执行方的五个 `TreeRowClassificationTest` 用例通过，但其中未知 MIME 的扫描场景也删除了名称，因此进入空名称保护，漏掉此边界。即使名称与身份完整，分类可靠性也必须传递给扫描器。无法证明子树消失时保留旧行的原要求仍适用。该 JVM 测试证明索引删除；真实 SQLite 位置级联与提供方 IPC 仍为 NOT_RUN。

### 通过的检查与限制

| 检查项 | 本次实际执行结果 |
| --- | --- |
| 保留的 `ConcurrencyReviewTest` | PASS，3/3 原始 ViewModel 复现。 |
| `ReaderViewModelSaveSnapshotTest` | PASS，3/3 既有快照基线。 |
| `ReaderSaveOrderTest` | PASS，1/1 原始存储调用后完成调度。 |
| `TreeRowClassificationTest` | PASS，5/5 已提供的分类/扫描场景。 |
| `LibraryBrowseSelectionTest` | PASS，2/2 已提供的选择/重开场景。 |
| `RecentMetadataPublicationTest` | PASS，2/2 完成/移除场景。 |
| `AcceptanceBoundariesTest` | FAIL，2/2 上述验收反例。 |
| `LibraryRefreshAcceptanceTest` | PASS，1/1 晚到刷新检查。 |
| 合计 | 执行 19 个：17 通过，2 失败。18 测试合并运行返回 exit 1；独立刷新运行返回 exit 0。 |
| Gradle、lint、APK/分发变体构建、提供方/Room/设备/界面门禁 | 本次 NOT_RUN；此前记录的沙箱套接字限制仍存在。不宣称完整验收或新的构建通过。 |

[LibraryRefreshAcceptanceTest.kt](review-evidence/2026-10-01-acceptance/LibraryRefreshAcceptanceTest.kt) 使用实际 `LibraryScanner` 和仓储，以及既有存储替身。A 的刷新在不可取消查询工作的文档快照发布边界暂停；释放 A 前先选中 B。之后 B 的资料库 ID、标题、文档行与进度归属均保持正确。这补齐执行方响应中可运行刷新归属证据的缺口，没有新增扫描器抽象或编辑产品代码。

两次编译调用均通过。生产 ViewModel/扫描器/仓储源码重新编译；未变化的本地 core/model/parser 构建 jar 提供支持依赖。原始游标代理复现未重跑，因为新解析器的 URI 构造需要 Android 运行时；当前分类与扫描测试仅提供所述 JVM 层面证据。

### 被测快照与产物

HEAD 仍为 `5a754292c1ea02f33af0cb917f5cf24c29dbb763`。以下身份绑定本次测试工作区源码，而非上文较旧快照：

| 被测试源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `eb0c23e262029bbeae3259f7f3465b06b37bb0cfc2fdc7a962880711d025cefd` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `04028926ec2b0186354366debade9c41d72676dfac84f13e5758cbf5eab48338` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `de435a2fdfb98299e59b1feaa072a71a33491ad0959256afbff78970193db4ac` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `545d67878f37272c0cd2c78d7cc8a549bd4ba5208ea94c59ab203d18343f3ad1` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `31f7d1e7ddc476fbee5c8d1280f04e4ae124e313aad2aaf33e37a6130940b75b` |

验证后已删除 `/tmp/leaflet-accept.8x8FT9` 下的临时快照、提取/复制依赖、编译类与日志。两份有价值的复现保留于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-acceptance/`：`AcceptanceBoundariesTest.kt` 与 `LibraryRefreshAcceptanceTest.kt`。它们是审计证据，未接入产品构建，不增加依赖。本次验收只更新此报告配对文件并保留上述复现；保留并行实现文件与工作区构建产物。

<a id="independent-recheck-2026-10-01"></a>

## 独立复核 — 2026-10-01

用户要求继续验收这四项问题，并排除新需求。本次结论不变。`ReaderViewModel`、`RecentViewModel`、`TreeLister` 与 `LibraryScanner` 的源码身份与此前验收表完全一致。因此 CR-20261001-04/05 仍是相同的失败实现；复用既有反例，不计作本次新执行测试。CR-20261001-07 的 JVM 发布结果也仍适用于其未变化源码。

`LibraryViewModel.kt` 已变为 SHA-256 `f4021db0541ac5a23e583dfb676e5d02d1e4b3b078fa186b63a3ef44a8db7579`。其当前源码及支持的浏览/扫描器/仓储源码，在隔离的 `/tmp/leaflet-reaccept.N5qXle` 快照中重新编译。仅运行既有选择归属测试与保留的迟到刷新测试；新需求行为不在验收范围。

| 问题 / 检查 | 复核结果 |
| --- | --- |
| CR-20261001-04，保存顺序 | REJECTED / OPEN；相同源码仍在 IO 派发之后分配请求序号。此前逆序启动失败仍适用。 |
| CR-20261001-05，缺失 MIME | REJECTED / OPEN；相同分类器/扫描器仍允许仅缺 MIME 时清理。此前删除失败仍适用。 |
| CR-20261001-06，资料库选择 | JVM 层面 PASS；`LibraryBrowseSelectionTest` 2/2 与保留的 `LibraryRefreshAcceptanceTest` 1/1 在变更后的 ViewModel 上重新运行通过。 |
| CR-20261001-07，最近阅读发布 | 对身份相同的源码复用此前 JVM PASS；视觉/设备验收仍待补。 |
| 本次重新编译与直接 JUnit 运行 | 均 exit 0；`OK (3 tests)`。这是本次新增执行数量，不是又运行了 19 个测试。 |
| Gradle、构建、提供方/Room/设备/界面门禁 | NOT_RUN；没有新的环境或设备证据可支持关闭这些剩余门禁。 |

执行的 JUnit 类为 `io.leaflet.reader.ui.library.LibraryBrowseSelectionTest` 与 `io.leaflet.reader.review.LibraryRefreshAcceptanceTest`，使用 JDK 17.0.9、Kotlin 编译器 2.0.21、Coroutines/Test 1.10.2 及 JUnit 4.13.2。支持依赖来自既有本地构建/缓存 jar。本次复核未编辑产品源码、测试源码集或新需求。

验证后已删除 `/tmp/leaflet-reaccept.N5qXle` 下的临时源码副本、提取/复制依赖、编译类及日志。仅更新此报告配对文件；既有复现仍保留于之前记录的仓库路径。未新增保留的复现或工作区构建输出。

<a id="executor-response-to-the-independent-acceptance-2026-10-01"></a>

## 执行方对独立验收的响应 — 2026-10-01

两个不通过结论对于它们所固定的快照都是正确的（`ReaderViewModel.kt`
`eb0c23e2…25cefd`、`TreeLister.kt` `545d6787…f3ad1`、`LibraryScanner.kt` `31f7d1e7…40b75b`）。这两个
机制现在都已修复，验收反例本身也已并入产品测试套件并通过。

**CR-20261001-04 — 序号在调用时（invocation time）分配。** `queueSave` 现在通过
`nextSaveSequence`，在调用方线程上、`viewModelScope.launch` 之前同步分配请求序号。因此序号顺序就是调用顺序，
再也不由哪个 IO 工作线程恰好先启动来决定。记账（bookkeeping）正是出于这一原因从协程 `Mutex` 改为
`synchronized` 块：它必须在调用时确定顺序，而不是在协程内部。其余策略不变——写入保持并发，
当一个完成不再属于最新请求时，它既不发布保存节流（save throttle）也不发布位置缓存，而较旧的完成若在较新写入之后落地，
会重新持久化最新已接受的地址。

该验收反例现在是一个产品回归：
`app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderSaveOrderDispatchTest.kt` 暂停 IO 派发，
依次调用第 2 块 → 第 5 块 → 退出补存，再按最新任务优先释放排队工作。在被拒绝的修订版上它打印

```text
invocation order must win, not IO-start order; writes=[5, 5, 2]
expected:<5> but was:<2>
```

而现在它通过，与 `ReaderSaveOrderTest`（存储调用后完成顺序）以及三个跨文档
`ReaderViewModelSaveSnapshotTest` 基线一并通过。

修复目标按文档（PER DOCUMENT）跟踪（上限为最近请求的八个文档），因此两次重叠写入之间的文档切换，
不会把较旧完成的修复重定向到另一个文档的请求上。`ReaderSaveOrderTest.aDocumentSwitchMustNotRedirectTheRepairToTheOtherDocument`
覆盖的正是这一点：A 的一次慢速第 2 块写入、A 一次快速跳到第 5 块、切换到 B、一次 B 保存，
随后是 A 的迟到完成——A 最终必须是 5，而 B 必须保留它自己的地址。

**CR-20261001-05 — 类型未知（unknown type）是显式的，且永不作为文件。** `TreeEntry` 新增
`typeUnknown`（默认 `false`，因此既有构造不变），只要行未声明 MIME 类型，`classifyTreeRow` 就会设置它。
`LibraryScanner` 对 `typeUnknown` 的处理与身份缺失完全相同：该行作为条目错误（entry error）上报（带其自身原因），
永远不会被索引为文档，遍历的缺失证据（absence evidence）也变得不足——跳过清理，而本次运行仍然成功并保留原有行。
因此，一条保留 id 与显示名称、只丢失 MIME 的行再也不能代替一个已消失的文件夹。

该验收反例现在是一个产品回归：
`core/storage/src/test/kotlin/io/leaflet/storage/scan/TreeRowClassificationTest.kt` 新增了
`missingOnlyMimeMustNotDeleteThePreviouslyIndexedDirectory`（扫描用例，在被拒绝的修订版上打印
`expected:<0> but was:<1>`）与 `anUnknownMimeRowIsNotClassifiedAsAFile`
（分类器用例）。两者都通过，与先前五个用例及既有的 33 个
`LibraryScannerTest` 用例一并通过。

**当前源码身份**（取代上文被拒绝的表）：

| 已修复源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `45ad5735b077b2ad4376ede435bb94b7502f58357e2808c7563e618d9dec086f` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `9cc7d436def324bce5ebad33c6cfa65e3c5cf14491d73a8cb0a4c696e558d99a` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |

**在产品工作区运行的门禁**（2026-10-01，修复之后）：`./gradlew test` 执行了 852 个 JVM
用例（350 个唯一方法），在全部四个应用变体与核心模块中 0 失败、0 错误、0 跳过；`lint` 与
`ktlintCheck --rerun-tasks` 通过；`:app:assembleFullDebug`、
`:app:assembleEinkDebug` 以及两个 flavor 的 androidTest 编译均通过
（`app-full-debug.apk` 28,827,351 B，`app-eink-debug.apk` 28,386,595 B）。

**仍为 NOT_RUN，且本响应不宣称：** 独立重跑
`AcceptanceBoundariesTest` 与 `LibraryRefreshAcceptanceTest`、真实 Room/SAF/提供方行为
（包括位置级联与提供方 IPC）、设备/界面验收，以及 E-Ink 硬件
矩阵。CR-20261001-06 与 CR-20261001-07 已由独立验收在 JVM 层面接受，此处不变。

<a id="invocation-type-fix-acceptance-2026-10-01"></a>

## 调用顺序/类型修复的独立验收 — 2026-10-01

范围仍为 CR-20261001-04 至 CR-20261001-07。新的请求时序号与显式 `typeUnknown` 修复，已在 `/tmp/leaflet-finalaccept.f3Weli/repo` 下的新工作区快照中独立测试。此前精确失败的验收用例已通过，但完整修复/分类路径仍有两个检查失败。这是相同的最新位置与不完整分类条件；未新增需求或产品实现。

### 当前结论

| 问题 | 结果 | 证据 |
| --- | --- | --- |
| CR-20261001-04，P1 保存顺序 | REJECTED / OPEN | 逆序 IO 启动、原始完成调度及切文档后的修复归属均通过。延迟补偿写入仍覆盖更新的跳转/补存。 |
| CR-20261001-05，P1 扫描安全 | REJECTED / OPEN | 分类器/扫描器的仅缺 MIME 场景通过。生产游标投影丢失 `typeUnknown`，将其重置为 false。 |
| CR-20261001-06，资料库归属 | JVM 层面 PASS；设备待补 | 原始迟到加载、选择/重开以及迟到刷新元数据/进度测试本次均通过。 |
| CR-20261001-07，最近阅读发布 | JVM 层面 PASS；设备待补 | 原始发布与产品完成/移除测试本次均通过。 |

### CR-20261001-04 — 修复写入也可能变成过时写入

源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:493–497`。

序号分配现已在派发前完成，修复目标也限定于所属文档。但是一次性的 `persist(repair.second, ...)` 之后没有再次进行新鲜度/修复决策。该补偿写入仍在存储层执行时，更新的请求可能已经完成。

[RepairAndProjectionAcceptanceTest.kt](review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt) 的 `aCompensatingWriteMustNotOverwriteAnEvenNewerJumpAndFlush` 执行：

1. 暂停第 2 块原始写入；第 5 块写入完成。
2. 释放第 2 块，其写入落地并触发第 5 块修复；在存储层暂停该修复。
3. 跳到第 6 块并完成退出补存。存储地址为 6，补存任务已经完成。
4. 释放第 5 块修复。所有已释放工作完成后，地址变为 5。

```text
repair must obey the same latest-request rule; writes=[5, 2, 6, 6, 5]
expected:<6> but was:<5>
```

缓存序号保护只阻止旧内存状态发布，不能撤销旧存储写入。修复必须遵守与原始写入相同的顺序保证。关闭前该反例必须与现已通过的派发及跨文档测试一起通过。测试使用重新编译的生产 ViewModel 与受控 ReaderStore，不是真实 Room/设备执行。

### CR-20261001-05 — 游标投影丢弃未知类型标记

源码：`core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:148–156`。

`classifyTreeRow` 现已设置 `typeUnknown`，`LibraryScanner` 也检查它。但 `ContentResolverTreeLister.readEntries` 使用分类器字段重建 `TreeEntry` 时遗漏 `typeUnknown`。该字段取默认值 `false`，生产条目到达扫描器之前，标记已丢失。

[RepairAndProjectionAcceptanceTest.kt](review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt) 的第二个测试使用代理 Cursor，运行实际重新编译的私有游标解析器。分类器返回 true，解析器最终条目却返回 false：

```text
cursor projection must preserve the classifier's unknown-type flag
expected:<true> but was:<false>
```

复现刻意缺失 ID，以避开 Android URI 构造；该行仍受空 ID 检查保护，因此这次执行证明的是标记丢失，而非再次断言索引删除。有效 ID/名称的行也使用相同的无条件重建。对于该仅缺 MIME 情况，源码检查确认丢失标记会再次绕过未知类型保护，允许将未访问目录视为文件。URI 分支不会恢复标记。真实有效 ID 的 URI 构造、提供方 IPC 与 SQLite 位置级联仍为 NOT_RUN。

关闭要求分类可靠性保留到最终发出的条目，而非只保留在辅助函数中。仅测试使用的分配/反射避开 ContentResolver 构造；没有访问原始文档或提供方。

### 验证、身份与清理

受影响的 ViewModel、游标解析器、扫描器、仓储、存储替身及定向测试均使用 JDK 17.0.9 / Kotlin 2.0.21 重新编译；编译返回 exit 0。未变化的项目/解析器支持 jar，以及 lifecycle 2.8.7、Coroutines/Test 1.10.2、JUnit 4.13.2，来自既有本地构建/缓存。

| 执行的类 | 结果 |
| --- | --- |
| 保留的 `ConcurrencyReviewTest` | PASS 3/3 |
| `ReaderViewModelSaveSnapshotTest` | PASS 3/3 |
| `ReaderSaveOrderTest` | PASS 2/2 |
| `ReaderSaveOrderDispatchTest` | PASS 1/1 |
| `RecentMetadataPublicationTest` | PASS 2/2 |
| `LibraryBrowseSelectionTest` | PASS 2/2 |
| `TreeRowClassificationTest` | PASS 7/7 |
| 保留的 `AcceptanceBoundariesTest` | PASS 2/2；此前两个拒绝场景现已通过 |
| 保留的 `LibraryRefreshAcceptanceTest` | PASS 1/1 |
| `RepairAndProjectionAcceptanceTest` | FAIL 2/2 |
| 直接 JUnit 运行合计 | 执行 25 个，23 通过，2 失败；exit 1 |
| Gradle、更广门禁、真实 Room/SAF/提供方/设备/界面/E-Ink 检查 | 本次独立 NOT_RUN；不宣称完整关闭 |

定向失败足以拒绝两个 P1 关闭，因此之后未新增无关或更广的套件。上文执行方工作区门禁结果仍为执行方证据。

| 被测试源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `45ad5735b077b2ad4376ede435bb94b7502f58357e2808c7563e618d9dec086f` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `9cc7d436def324bce5ebad33c6cfa65e3c5cf14491d73a8cb0a4c696e558d99a` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `f4021db0541ac5a23e583dfb676e5d02d1e4b3b078fa186b63a3ef44a8db7579` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `04028926ec2b0186354366debade9c41d72676dfac84f13e5758cbf5eab48338` |

验证后已删除 `/tmp/leaflet-finalaccept.f3Weli` 下的临时快照、提取/复制依赖、编译产物与日志。有价值的复现保留于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt`，作为报告证据，不接入产品构建。本次验收只写入此报告配对文件与该复现。保留并行源码改动与既有产物。

<a id="executor-response-to-the-third-acceptance-2026-10-01"></a>

## 执行方对第三次验收的响应 — 2026-10-01

两个不通过结论对于它们所固定的快照（`ReaderViewModel.kt` `45ad5735…9dec086f`，`TreeLister.kt` `9cc7d436…e558d99a`）都是正确的。两个机制现在都已修复，三个验收反例全部并入产品套件并通过。

**CR-20261001-04 — 补偿写入（compensating write）遵守同一规则。** 修复写入也是一次普通存储写入：它可能在更新的请求完成期间被扣住，因此一次性的修复仍可能最后落地，把较旧的地址留在存储中。现在每次完成都会在一个循环内合并/收敛（coalesce）——先写入自己的快照，然后重新检查该文档最新完成的请求并写入它，直到写出最新的那一个——从而无论各写入如何交错，最后一次写入都是最新地址。按文档跟踪最新请求与调用时（invocation time）排序均未改变，且只有最新请求会发布保存节流（save throttle）与缓存。

该反例现在是一项产品回归：`app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderSaveRepairTest.kt` 扣住第 2 块的写入，让第 5 块落地并扣住第 5 块的修复，完成第 6 块及其退出补存，然后释放该修复。针对被拒绝的修订版，它打印出

```text
repair must obey the same latest-request rule; writes=[5, 2, 6, 6, 5]
expected:<6> but was:<5>
```

现在它通过，同时通过的还有 `ReaderSaveOrderTest`（存储调用后完成顺序加上跨文档修复用例）、`ReaderSaveOrderDispatchTest`（逆序 IO 启动顺序）以及三个跨文档 `ReaderViewModelSaveSnapshotTest` 基线。

**CR-20261001-05 — 投影（projection）保留分类器的判定。** `ContentResolverTreeLister` 逐字段重建所发出的 `TreeEntry`，因此 `typeUnknown` 在扫描器收到生产行之前就取了默认值 `false`。解析器现在使用单一共享投影 `readTreeEntry(documentId, displayName, mimeType, size, lastModified, documentUriFor)`，它从 `classifyTreeRow` 的结果派生所发出的条目（`copy(documentUri = …)`），而不是罗列字段，因此该标记——以及今后新增的任何字段——都不会在分类器与扫描器之间丢失。它是纯函数（URI 构造器由调用方提供），因此该投影无需 Android 运行时即可做 JVM 测试。

该反例现在是一项产品回归：`core/storage/src/test/kotlin/io/leaflet/storage/scan/TreeEntryProjectionTest.kt` 固定：仅缺 MIME 的行以 `typeUnknown = true` 发出；声明的文件/目录类型得以保留；无身份的行不会获得文档 URI。它通过，同时通过的还有七个 `TreeRowClassificationTest` 用例与既有的 33 个 `LibraryScannerTest` 用例。审查保留的 `RepairAndProjectionAcceptanceTest` 用代理 `Cursor` 与 `sun.misc.Unsafe` 自行驱动私有游标循环；该类不在本模块的单元测试编译类路径上，因此该复现仍是游标循环的独立证据，而产品测试固定它所调用的投影。它当时的测量输出为：

```text
cursor projection must preserve the classifier's unknown-type flag
expected:<true> but was:<false>
```


**当前源码身份**（取代此前的验收表）：

| 已修复源码 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `6fda75d018f431ac8073ebb80c9afcce6a7b2eb6bcf35a1f1d500c4c21cd6e32` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `ec53a13709241941139622ea9ca3f485448ab7eb6ec27d5600d3f099813b5b29` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15`（未变化） |

**在产品工作区运行的门禁**（2026-10-01，两项修复之后）：`./gradlew test` 在全部四个应用变体与 core 模块中执行了 866 个 JVM 用例（355 个唯一方法），0 失败、0 错误、0 跳过；`lint` 与 `ktlintCheck --rerun-tasks` 通过；`:app:assembleFullDebug`、`:app:assembleEinkDebug` 及两个 flavor 的 androidTest 编译均通过（`app-full-debug.apk` 28,827,351 B，`app-eink-debug.apk` 28,386,595 B）。

**仍未运行，且本次响应不宣称：** 独立重跑 `RepairAndProjectionAcceptanceTest`、真实 Room/SAF/提供方行为（有效 ID 的 URI 构造、提供方 IPC、SQLite 位置级联），以及设备/UI/E-Ink 验收。CR-20261001-06 与 CR-20261001-07 仍在 JVM 层面保持接受，且本次响应未改动它们。
