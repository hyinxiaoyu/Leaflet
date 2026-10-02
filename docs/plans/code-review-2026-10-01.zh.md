# 阅读器后续代码审查 — 2026-10-01

[English](code-review-2026-10-01.md) | 中文

## 结论与范围

新增三个问题：一个 P1 表格测量不安全路径，以及两个 P2 阅读器/导航回归。历史 DEF-012 的延迟保存子项也已通过失败的 JVM 回归独立复现；它是尚未解决的 P1 遗留问题，不新增重复缺陷。本次审查未实施产品修复。

> **跟进状态（2026-10-01）：** 四个问题均已修复；两个延迟保存用例已复现为失败，现已在 JVM 上通过；表格门禁与解析器组合已有 JVM 回归；两个界面问题已有设备回归，回归代码已编写但为 NOT_RUN（无设备）。见本报告末尾的[解决结果](#resolution-2026-10-01-follow-up)。

HEAD 仍为 `5a754292c1ea02f33af0cb917f5cf24c29dbb763`。审查范围为未提交的 UX-07 实现与上次审查修复的快照（snapshot），以及相关已提交的导航/阅读状态代码。下文行号对应该快照。最终源码检查时，相关工作区源码仍与快照一致。本次是定向审查，不是全项目验收。

| 编号 | 优先级 | 问题 | 证据 | 状态 |
| --- | --- | --- | --- | --- |
| DEF-012，延迟保存子项 | P1 | 排队保存读取后来文档的可变块位置 | 重新编译生产 ViewModel；确定性 JVM 回归失败：预期 5，实际 0 | FIXED — JVM 回归通过（改动前两个用例均失败） |
| CR-20261001-01 | P1 | 宽表格单元格可能超过 Compose 固定约束的可表示范围 | 生产解析器接受样本；固定版本框架拒绝过大约束；完整界面场景 NOT_RUN | FIXED — JVM 门禁/解析器回归通过；设备回归已编写，NOT_RUN |
| CR-20261001-02 | P2 | E-Ink 模式导航仍使用默认 700 ms 淡入淡出 | 已检查应用调用与固定版本依赖字节码；设备过渡录制 NOT_RUN | FIXED — 已接入显式即时过渡；设备采集 NOT_RUN |
| CR-20261001-03 | P2 | 在文档末尾关闭菜单可能移动正文 | 静态滚动边界反例；设备回归 NOT_RUN | FIXED — 设备回归已编写，NOT_RUN |

## DEF-012 — 延迟保存仍读取可变位置

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:394–397` 与 `419–421`。
- **触发与影响：** 阅读 A，请求 `jumpToBlock(5)`，让 IO 保存任务保持排队，再调用 `open(B)`。切文档将 `lastVisibleBlock` 重置为 0。A 的保存开始执行时，将 A 写为第 0 块，而非请求的第 5 块，覆盖 A 的辅助阅读位置。原始文档不受影响。
- **原因：** 协程（coroutine）捕获了 A 的 ID/文档，但执行时才读取 `lastVisibleBlock`。`onVisibleBlock()` 存在同样的可变读取，包括完成时的节流状态更新。显式补存路径捕获 `blockIndex` 并不能修复其他保存路径。
- **证据：** [ReaderSaveReviewTest.kt](review-evidence/2026-10-01/ReaderSaveReviewTest.kt) 使用独立 Main/IO 测试调度器（test scheduler）及内存 ReaderStore，运行重新编译的实际 ViewModel。保存排队前 A 已完整加载；释放 IO 时 B 仍处于 Loading。JUnit 输出 `Tests run: 1, Failures: 1`，断言为 `expected:<5> but was:<0>`。
- **最小修复方向与关闭条件：** 派发前捕获不可变保存快照，完成/节流更新绑定相应文档与代次（generation）。保留的回归必须通过，并覆盖延迟滚动保存与切文档后的完成回调。生产验收仍需真实 Room/SAF/设备验证。

[2026-09-20 独立审查](code-review-2026-09-20.md) 已指出该子项，其记录的 ViewModel SHA-256 与当前源码相同。历史台账中较宽泛的 CLOSED 标签不能证明该执行顺序安全；本报告保留原缺陷身份并补充可执行证据。

## CR-20261001-01 — TableGrid 使用无上界固定约束

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/TableGrid.kt:58–74`，调用点为 `ReaderScreen.kt:1019–1033`；解析器保护位于 `core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt:165`。
- **触发与影响：** 合法表格含有很长的不换行单元格，其固有物理像素宽度超过 Compose 约束（constraint）的可表示范围。新网格直接将该宽度传给 `Constraints.fixed`，布局时抛异常，而非进入承诺的可读/降级状态。更高屏幕密度或更大字体会增加触发机会。
- **原因：** 网格宽度取 `maxIntrinsicWidth(Constraints.Infinity)` 的最大值，只有下界。Compose 约束不支持任意整数尺寸。既有 32 Ki 字符源码上限不能限制单元格或整个网格的物理像素尺寸。
- **证据：** [WideTableProbe.kt](review-evidence/2026-10-01/WideTableProbe.kt) 生成 10,039 字符的表格，单个单元格包含 10,000 个 CJK 字符。生产 JVM 解析器输出 `degraded=false, kinds=[TABLE], blockSourceChars=[10039]`。[ConstraintsProbe.java](review-evidence/2026-10-01/ConstraintsProbe.java) 调用固定版本 `ui-unit-android:1.7.5` 工厂方法：`(100000, 80)` 被接受；`(262143, 80)` 与 `(300000, 80)` 抛出 `IllegalArgumentException: Can't represent a width ... in Constraints`。本次未测量实际 Android 文字固有宽度，也未执行完整界面崩溃复现。
- **最小修复方向与关闭条件：** 固定测量前校验单元格及网格总尺寸，对极端内容复用有说明、有边界的降级路径。不能只钳制宽度而让不换行文字被裁剪。通过真实阅读器，在高密度/大字体下验证样本无崩溃且内容完整可达，同时保留普通表格列对齐与搜索映射。

## CR-20261001-02 — NavHost 仍使用默认淡入淡出

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/LeafletNav.kt:74–77`；`gradle/libs.versions.toml` 中固定版本为 `navigationCompose = "2.7.7"`。
- **触发与影响：** 在 E-Ink 模式下切换 Library、Settings、Browse 与 Reader。NavHost 默认进入/退出动画仍启用，绕过 REQ-0004 FR-2/FR-4 的禁止交叉淡化（crossfade）规则。返回导航也继承过渡效果（transition）。
- **原因：** NavHost 没有显式传入过渡策略。注释所称 2.7.7 默认即时切换路由不成立；主题中关闭水波纹（ripple）/越界滚动效果（overscroll）不会配置 NavHost 过渡。
- **证据：** 提取本地缓存的 2.7.7 依赖，使用 JDK 17 `javap -c` 检查。`NavHostKt$NavHost$3.invoke` 调用 `fadeIn(tween(700))`；`$4.invoke` 调用 `fadeOut(tween(700))`。既有应用字节码以默认参数掩码 `508` 调用支持过渡的 NavHost 重载，包含进入/退出/返回默认值。这是依赖与调用链证据，不是设备动画录制。
- **最小修复方向与关闭条件：** 通过集中动效策略，为进入、退出、返回进入与返回退出显式提供 `EnterTransition.None` / `ExitTransition.None`；M0/M1 Standard 需求也规定无页面过渡。两个分发变体（flavor）均需验证真实路由切换与返回导航，包括显示模式热切换。

## CR-20261001-03 — 菜单相关尾部间距在文末移动正文

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:466–470`，第 491 行绑定为 `LazyColumn.contentPadding`；第 713/733 行因菜单变化而改变底部控件高度。
- **触发与影响：** 打开阅读菜单，翻到实际文档末尾，再通过中心点击关闭菜单。底部间距从 `bottomChromePx + 8 dp` 缩小为较小的页脚预留值。当前滚动偏移可能超过新的最大值，列表必须向后钳制并移动正文。因此在这个边界上，菜单切换不能保留正文项位置。
- **原因：** 对足够长的文档，正文总高度为 `C`、视口高度为 `H`、尾部间距为 `P` 时，可滚动末尾为 `C + P - H`。已位于菜单展开的末尾 `P_open` 时，将间距减为 `P_closed`，当前偏移会超过新末尾 `P_open - P_closed`。列表外部矩形固定，不能阻止这种内容移动，也不能阻止内容间距变化带来的测量过程。
- **证据边界：** 这是静态几何反例，尚未在设备上测量像素位移。新增末尾测试覆盖关闭 → 打开 → 继续翻页，但不再关闭已到末尾的菜单。浮层几何测试只检查列表矩形，不检查正文位置或布局过程计数。
- **最小修复方向与关闭条件：** 保留足够且不随菜单可见性变化的尾部空间，或采用在尾部遮挡变化时仍保持实际正文位置的策略。补充打开 → 文末 → 关闭/重开的原生文字边界与滚动偏移检查。不能仅凭列表边界不变就验收 UX-06 的正文无跳动标准。

## 上轮审查跟进

[2026-09-30 报告](code-review-2026-09-30.md) 记录了工作区修复。本次检查了相应源码变更：

- CR-20260930-01/02/03 已有新的末尾边界与跳转揭示/顶部预留逻辑。本次未重跑完整设备关闭验证；CR-20261001-03 指出尾部间距方案中的新边界回归。
- CR-20260930-04 的强制 `resolveActivity()` 预检查已移除。本次未验证对包查询不可见的处理应用场景。
- CR-20260930-05 的三个 `SettingsStoreReadingModeTest` 用例基于快照源码重新编译，并直接用 JUnit 运行：`OK (3 tests)`。这证明配置的首次安装默认值与显式选择行为，不证明所有 MainActivity/设备启动帧。

没有将历史执行者测试数量或 UX-07 截图当作本次新执行的审查检查。

## 验证、源码身份与限制

环境：macOS ARM64、JDK 17.0.9、Kotlin 编译器 2.0.21、Coroutines/Test 1.10.2、JUnit 4.13.2；依赖来自既有本地缓存。快照与复制的缓存位于 `/tmp/leaflet-review2.V7aBMq`。未修改产品源码。

| 检查项 | 结果 |
| --- | --- |
| 定向 Gradle JVM 命令 | 未能启动：当前沙箱下 `FileLockContentionHandler` 报 `java.net.SocketException: Operation not permitted`。不宣称 Gradle 测试通过。 |
| 延迟保存回归 | 快照 `ReaderViewModel.kt` 与保留的复现代码重新编译成功；直接 JUnit 运行按预期失败，证明保存块位置错误。其他 core/model 类使用既有本地构建 jar。 |
| 阅读模式回归 | 快照 `SettingsStore.kt`、`ReaderSettings.kt` 与既有测试重新编译成功；直接 JUnit 运行通过 3 个测试。 |
| 宽表格解析器探针 | 使用既有生产解析器构建 jar，将生成输入接受为非降级 TABLE。 |
| 固定约束探针 | 固定版本框架工厂方法复现可表示范围异常。 |
| 导航依赖检查 | 确认默认 700 ms 淡入淡出与既有应用默认参数调用。 |
| 界面/设备回归、APK 构建、lint、ktlint、硬件 | 本次审查为 NOT_RUN；当前套接字限制也阻止正常的本地模拟器/ADB 验证。 |

在快照中尝试的 Gradle 命令：

```bash
./gradlew --offline --no-daemon --no-build-cache \
  :app:testFullDebugUnitTest :app:testEinkDebugUnitTest \
  :core:reader:testDebugUnitTest :core:storage:testDebugUnitTest
```

选取的 SHA-256 身份：

| 快照文件 / 提取的依赖 | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `b933b812728dc5a943433b81b667b89ee9595e954d002652dd681663bd801e70` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/TableGrid.kt` | `8a5c6a9aa10da33eb9826c2375aac036a0318cf0fd363e724861a71293525d72` |
| `app/src/main/kotlin/io/leaflet/reader/ui/LeafletNav.kt` | `5f467b386496298ec32ef79a756efb8afbf9d0ad5efe683402445bfcd1cfdedb` |
| `ui-unit-android:1.7.5` 提取的 `classes.jar` | `b27d9309a3f18a8f9a3b0b948d1efcf6221e722271842c78cee0869fef3383d6` |
| `navigation-compose:2.7.7` 提取的 `classes.jar` | `1a8e0d9001f090191054361d6d555a1f2b74ed9deaddaf0b2960a9b90c3af68e` |

## 产物与清理

- 检查后已删除 `/tmp/leaflet-review2.V7aBMq` 下的快照、复制缓存、提取依赖、编译产物与日志。
- 三份有价值的复现源码刻意保留于 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01/`：`ReaderSaveReviewTest.kt`、`ConstraintsProbe.java` 与 `WideTableProbe.kt`。它们是报告证据，未接入产品构建，不增加依赖。后续修复时应先将复现代码接入适用测试套件，再关闭问题。
- 英文报告与完整中文配对文件为文档交付。保留并行源码/文档改动与既有工作区构建产物；本次审查未创建工作区构建输出。

<a id="resolution-2026-10-01-follow-up"></a>

## 解决（2026-10-01，跟进）

四个问题均已在被审查快照之上修复。审查保留的 `ReaderSaveReviewTest.kt` 已并入产品测试套件，成为 `app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderViewModelSaveSnapshotTest.kt`（改写为同一测试夹具（harness），并新增一个针对节流滚动路径的用例和一个不切换文档的基线用例）。在修复前运行它会精确复现报告中的失败——`expected:<5> but was:<0>` 与 `expected:<4> but was:<0>`——修复后通过。其余界面关闭项是设备回归，已编写并可编译，但为 NOT_RUN（未连接设备或模拟器）。

**DEF-012，延迟保存子项。** `ReaderViewModel` 现在在请求保存的瞬间捕获不可变快照 `SaveSnapshot`（document id、解析后的文档、块、加载代次），并交给 `queueSave`/`persist`。IO 协程只读取该快照，因此排队保存不再能把下一个文档的第 0 块写到前一个文档的位置之上。完成记账（bookkeeping）绑定同一快照：`PositionThrottler.markSaved` 与 `currentDescriptor`/`currentStored` 缓存仅在快照仍描述当前打开的文档时才更新（`isCurrent` = 相同 id 且相同会话代次），这也堵住了延迟保存可能用来污染新文档缓存的那次无条件 `currentStored` 赋值。`jumpToBlock`、`onVisibleBlock` 与 `flushPosition` 都走这条路径。
回归（JVM，已执行）：`ReaderViewModelSaveSnapshotTest` —— 3 个用例，全部通过；两个切换用例在旧实现上失败。

**CR-20261001-01 — 表格约束。** 上界现在显式，并在测量前完成校验：

- `TableGrid` 将列宽与行高都钳制到 `MAX_CONSTRAINT_DIMENSION_PX`（260 000），因此任何输入都不会把不可表示的尺寸交给 Compose。
- `TableBlockEntry` 先校验块：单元格以 `softWrap = false` 渲染，因此每个字形推进都被限制在约 2 em 内，`characters × 2 em + cell padding` 是未换行单元格最宽行的保守上界（`isTableCellWidthRepresentable`）。超过该上界时，块改走既有的有界回退方案（fallback）——在普通横向滚动容器中渲染其自身源文本——因此不会丢弃或裁剪任何字符，搜索高亮仍然生效。普通表格保持其测量对齐；普通列宽远低于该上界。
- 上界本身是测量得出而非假设：针对固定版本 `ui-unit` 1.7.5 的 JVM 探针显示 `Constraints.fixed(262142, …)` 被接受、`262143` 抛异常，测试将其断言为余量（margin）（`TableGridBoundsTest.theGateKeepsAMarginBelowTheMeasuredFrameworkLimit`）。
回归（JVM，已执行）：`TableGridBoundsTest` —— 5 个用例，包括把审查样本经生产解析器（PRODUCTION parser）解析（`degraded = false`、一个 `TABLE` 块、最宽单元格 10 000 字符）并被门禁拒绝。设备回归 `ReaderReviewFollowUpTest.pathologicalWideTableFallsBackToScrollableTextWithoutCrashing` 断言回退文本已组合且后一段落仍会显示；NOT_RUN。

**CR-20261001-02 — 导航过渡。** `LeafletTheme` 现在将解析后的 `MotionPolicy` 发布为 `LocalMotionPolicy`，`NavHost` 从该策略显式接收 `enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition`（`MotionPolicy.pick`）。REQ-0004 FR-2 要求每个过渡决策都经该策略，§Standard 又规定 M0/M1 无页面过渡，因此两个分支都是即时的；此前声称 navigation-compose 2.7.7 默认即时切换的注释有误，已删除。设备过渡采集仍为 NOT_RUN —— 需要在硬件上做帧级观察。

**CR-20261001-03 — 尾部间距。** 尾部预留不再取决于当前菜单可见性：它从已经覆盖页脚加一行菜单的下限（floor）开始（96 dp；页脚约 29 dp，Material 3 `TextButton` 行 ≥ 40 dp），并只在实测外层控件在大号系统字体下超过该下限时，通过按组合（composition）记录的高水位（high-water mark）单向增长。缩小才是缺陷（预留变小会降低最大滚动偏移，列表向后钳制、正文随之移动）；增长不会移动任何东西，因为该内边距位于最后一项之后，只扩展滚动范围。最后一行仍能避开页脚和已打开的菜单，因此 CR-20260930-01 保持关闭。设备回归 `ReaderReviewFollowUpTest.togglingTheMenuAtTheTailDoesNotMoveTheBody` 在菜单打开时翻到末尾，断言菜单关闭再打开时组合出的正文行保持精确位置；NOT_RUN。

本次跟进的验证（2026-10-01，四项修复全部完成后）：`./gradlew test --rerun-tasks` 执行 726 个 JVM 用例（315 个去重方法），0 失败、0 错误、0 跳过——其中包含新增的 `ReaderViewModelSaveSnapshotTest`（3 个用例）与 `TableGridBoundsTest`（5 个用例），且在 app 的全部变体中运行；`lint` 与 `ktlintCheck --rerun-tasks` 通过；`:app:assembleFullDebug`、`:app:assembleEinkDebug` 以及两个变体的 androidTest 编译均成功（`app-full-debug.apk` 29,215,245 B，`app-eink-debug.apk` 28,493,461 B）。两项 UI 回归（`ReaderReviewFollowUpTest`）以及本报告中的全部设备场景均为 NOT_RUN：未连接设备或模拟器（`adb devices` 为空，且未安装 AVD）。
