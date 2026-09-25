# R2 验收未关闭缺陷

[English](round-2-open-defects.md) | 中文

记录日期：2026-09-18
状态：**2026-09-19 表格子项修复：DEF-003/006/007 CLOSED；DEF-008 仍 FIXED_PENDING_VERIFICATION；
R3-03 复验新发现 DEF-009（任务列表空条目）并已关闭；2026-09-20 独立验收新增 DEF-010/011（测试与验收覆盖缺口，OPEN）。**
上一修复轮的 CLOSED 记录保留为历史；当前状态以汇总表及最新日期的复验追加记录为准。
来源：第二轮独立验收及后续任务讨论。

本清单补充 [R2 验收结论](round-2-acceptance-verdict.md)，记录其中 PASS 声明尚未覆盖或与源码冲突的部分；不改写历史验证结果。修复沿用 [R2 任务计划](round-2-stabilization-plan.md) 和现有 REQ，不另建重复产品需求。

证据等级：原始缺陷描述以静态检查为主；后续修复与独立复验的运行证据分别记在对应追加记录中。记录文档本身不代表实施修复，不把建议复现步骤当成已执行测试。

## 汇总与执行顺序

2026-09-20 代码审查新增 **DEF-012/013（OPEN，P1）**：切文档补存身份混用、同文档重查位置回退。详见 [阅读状态代码审查](code-review-2026-09-20.md)。这是生产调用链的新发现，不与 DEF-010/011 的测试补证混为一项；设备复现待补。

2026-09-20 当前交接：DEF-010/011 已按要求补证并关闭（证据见
[r3-01-04-execution-report.md](r3-01-04-execution-report.md)「独立验收整改」）；DEF-008 保留待验证；
已通过的语法/表格工作不重复实现。详细复验结论见 [R3-01～04 独立验收](r3-01-04-independent-review.md)。
**注意**：整包双 flavor 设备套件在宿主双模拟器高负载下会出现活动启动失败（`No compose hierarchies found`），
定向用例全绿；环境恢复后需重跑全量。

P1：阻塞对应 MVP 功能验收；P2：阻塞完成声明可信度与交付闭环。状态使用 OPEN → IN_PROGRESS → FIXED_PENDING_VERIFICATION → CLOSED；复验失败可退回 OPEN（注明重新打开）。只有验收证据完整后才关闭。

| ID | 优先级 | 缺陷 | 关联任务 / REQ | 状态 |
| --- | --- | --- | --- | --- |
| DEF-001 | P1 | TXT 与纯文本降级内容进入 Markdown 渲染 | R2-03 / REQ-0002 | CLOSED（2026-09-18） |
| DEF-002 | P1 | 已创建的 TextView 不同步排版设置 | R2-03 / REQ-0007、0010 | CLOSED（2026-09-18） |
| DEF-003 | P1 | 表格重复单元格误标当前匹配（普通文本子项早前已修） | R2-06 / REQ-0008 | CLOSED（2026-09-19 表格子项修复） |
| DEF-004 | P1 | AST 重建仍可能破坏代码围栏及复制完整性 | R2-02 / REQ-0002、0007 | CLOSED（第二轮 2026-09-18） |
| DEF-005 | P1 | 同文档重开只查索引，未校验真实访问权限 | R2-05 / REQ-0001、0003 | CLOSED（第二轮 2026-09-18） |
| DEF-006 | P1 | 表格重复匹配的生产视图断言缺失 | R2-07 / 对应 MVP REQ | CLOSED（2026-09-19 补齐） |
| DEF-007 | P2 | 验收结论与证据不一致（含表格项与多处过期引用） | R2-08 / REQ-0011 | CLOSED（2026-09-19 重汇总） |
| DEF-008 | P1 | Full 设备测试销毁 Activity 时 Compose 越界崩溃 | R2-07 / REQ-0011 | FIXED_PENDING_VERIFICATION（已有线程/测试修复，因果归因尚未独立证明） |
| DEF-009 | P1 | 任务列表条目不产出源码切片，生产页面渲染为空条目 | R3-03 / REQ-0002、0007 | CLOSED（2026-09-19 R3-03 发现并修复） |
| DEF-010 | P1 | 生命周期测试不能证明退出补存与重建后页面恢复 | R3-01 / REQ-0003 | CLOSED（2026-09-20 补证；重建“新实例页面重挂载”由 DEF-011 跨进程证据承担） |
| DEF-011 | P1 | 磁盘测试未闭环生产页面跨进程恢复，跳过条件可能掩盖失败 | R3-04 / REQ-0003、0007、0010 | CLOSED（2026-09-20 补证；真实 MainActivity 路由/真实 SAF provider 仍未覆盖） |
| DEF-012 | P1 | 切文档加载期间补存混用新 ID 与旧内容 | 2026-09-20 代码审查 / REQ-0003 | CLOSED（2026-09-20 修复；设备回归修复前红） |
| DEF-013 | P1 | 同文档新鲜度检查把阅读位置拉回打开时地址 | 2026-09-20 代码审查 / REQ-0003 | CLOSED（2026-09-20 修复；设备回归修复前红） |

当前推荐顺序：已按「DEF-003 表格定位 → DEF-006 真实单元格断言 → DEF-007 结论重汇总」执行完毕（见文末最新一轮记录）；DEF-008 保留生命周期复验与残余风险，待独立复现/归因。DEF-001/002/004/005 不因其他缺陷未关闭而重复实施。共享渲染/定位模型的任务默认串行，不新增框架或升级无关依赖。

## DEF-010 — 退出补存与 Activity 重建断言不足

记录日期：2026-09-20；状态 OPEN；证据类型：源码/测试审计，不代表已确认产品功能损坏。

- `ReaderLifecycleTest` 的两个用例先调用 `jumpToBlock(2)`，该生产方法立即持久化位置；之后以 `savedPositions.contains(documentId)` 证明退出或销毁补存，旧写入即可满足断言。
- recreate 测试明确说明新 Activity 没有重新挂载阅读组合，最后只检查测试持有的 ViewModel，不能证明生产页面恢复或可见位置保留。
- 最小补证：等待并记录旧写入，然后制造节流窗口内尚未保存的新位置；触发离开/销毁，核对新增写入及最终位置。临时禁用退出补存时回归必须失败。
- 对真实 Activity 重建，断言新实例中的阅读内容和可见地址，不只比较测试变量中的旧模型。不要把辅助测试失败直接归因到产品，先定位接线与测试边界。
- 关闭条件：退出补存和重建恢复分别有有效生产路径断言；双 flavor 验证，日志绑定源码标识。

## DEF-011 — 跨进程存储证据尚未覆盖生产页面恢复

记录日期：2026-09-20；状态 OPEN；证据类型：验收范围缺口，非已复现的数据丢失。

- `ReaderPersistenceTest` 使用真实磁盘 `r3-persist.db` 和 `r3-persist.preferences_pb`，生产配置使用 `leaflet.db` 和 `settings`；两阶段只直接调用 Store/Repository 写入和读取，没有在重启后打开生产阅读页/设置页。
- 这些测试能证明存储序列化持久化，但不足以将“用户改设置/阅读→杀进程→生产页面恢复”标记全量 PASS。
- `assumeTrue(seeded == FONT_SIZE)` 使用被验证的字号作为前置检测；字号若意外丢失，第二阶段会 skip 而非 fail。应使用独立播种标识；强制两阶段验收中前置缺失必须失败。
- 当前 ReaderPersistenceTest SHA-256 为 `215dfa8786efa5fba40257b9549c24e4efe6c7819127d5e42ad4da8dbd7a38bc`，与执行报告中的源码哈希不一致；历史 adb 记录不能直接绑定当前文件。
- 本轮联合运行：Full 第二阶段用例未跳过，E-Ink 跳过；同一联合套件的通过不证明测试中间经历过真实进程终止。本轮未独立执行两阶段 adb 流程。
- 关闭条件：保留已有底层磁盘测试；补生产接线下的设置修改/阅读、杀进程、新进程 UI 恢复断言，记录进程和数据标识；两 flavor 的必要路径均有明确结果，skip 不算通过。

## DEF-001 — TXT / 降级内容被二次解释为 Markdown

- 已确认：`DocumentParser.parseTxt` 为普通 TXT 创建 PARAGRAPH；ReaderScreen 的普通条目无条件传入 `renderSpanned`。PLAIN_FALLBACK 同样进入该分支，没有字面显示分流。
- 源码：`core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt`；`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:493`、`:918`。
- 影响：TXT 中的标题、星号、链接等会被解释；纯文本降级可能再次富文本解析，显示与搜索语义不一致。
- 复现：打开包含 `# literal`、`**literal**`、`[literal](target)` 的 TXT；检查实际 TextView 文字和 spans。再对降级样本做同样检查。
- 修复边界：明确保留“字面文本 / Markdown”渲染语义，TXT 和降级块不能再次 Markdown parse；不依赖截图猜测文档类型。
- 验收：以上字符在实际视图完整原样显示，无意外标题/粗体/链接 span；同内容 MD 仍正确富文本显示；搜索与复制对应用户看到的文本。

## DEF-002 — 排版变化未应用到已有 TextView

- 已确认：SpannedTextView 在 AndroidView factory 中设置字号、字体和行距；update 仅执行 `view.text = withMarks`。
- 源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:974`、`:982`、`:985`。
- 影响：视图复用/重组时，新设置未同步到旧 TextView，可能出现同文档条目排版不一致。
- 复现：保持同一阅读视图，依次改变字号、字体、行距，比较已有与新进入视口条目的 TextView 属性；返回阅读页并检查恢复位置。
- 修复边界：样式更新与文本绑定同样覆盖 update 路径；核对行距单位及密度转换，不靠销毁整个 Reader 强制更新。
- 验收：各档设置即时反映在已有视图；新旧条目一致；重启持久化正确，阅读上下文保留。

## DEF-003 — 富文本搜索高亮错位

- 已确认：EntryBindings.displayedText 对普通块返回源码 `block.text`，据此映射匹配位置；SpannedTextView 将这些范围直接应用到已去除 Markdown 标记的文本。
- 源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt:25`、`:41`；`ReaderScreen.kt` 的 `applyStaticSpans`；`core/reader/src/main/kotlin/io/leaflet/reader/SearchHighlight.kt`。
- 复现：文档为 `# Alpha` 或 `**Alpha** Beta`，搜索 Alpha，检查实际 BackgroundColorSpan/ForegroundColorSpan 的起止和覆盖文字；增加链接、重复词、代码和表格。
- 影响：匹配数可能正确，但高亮位置偏移、被截断或完全不可见。
- 修复边界：映射到最终实际渲染文本，不能用 clamp 掩盖坐标错误；复用唯一位置契约，不再增加平行映射体系。
- 验收：上述样本的高亮范围准确覆盖匹配词；前后步进对应不同匹配；修改查询和切文档后无旧高亮残留。

## DEF-004 — AST 重建导致 Markdown 语义变化

- 已确认：BlockSlicer.inlineInto 直接输出 Text.literal，没有恢复必要转义；没有 Strikethrough 专门分支；重建结果又被 Markwon 解析。列表/引用的多行缩进保留也需专项验证。
- 源码：`core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt` 中 `BlockSlicer.emit/listItems/inlineInto`，特别是 `:450`。
- 复现输入：转义星号 `\*literal\*`、`~~deleted~~`、含反引号的行内代码、嵌套列表、多行引用、围栏内含三反引号的代码；比较原文语义与实际 spans/层级/复制文本。
- 影响：原本字面内容变成样式，删除线丢失，嵌套结构可能扁平化；“源码切片”实际已被重建，不能再宣称逐字精确保留。
- 修复边界：优先复用既有 AST 与原生渲染能力；如必须重新序列化，覆盖转义与结构规则，不自建完整 Markdown parser。
- 验收：实际渲染保持输入语义、文本完整且不重复；代码复制符合原需求；现有完整性与锚点回归仍通过。

## DEF-005 — 重开同文档绕过刷新和失权处理

- 已确认：ReaderViewModel.open 在 currentId 相同且已有 Content 时直接返回，未重读元数据或检查文件可达性。
- 源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:173`。
- 复现：打开 A → 返回目录 → 外部修改 A 并手动刷新 → 重开 A；另测删除 A、撤销授权后重开。保持同一 App 进程和 ViewModel 生命周期。
- 影响：持续显示旧内容，缺失/失权提示被缓存遮蔽。历史 verdict 已记录该行为，但不是用户已批准的需求豁免。
- 修复边界：区分配置变化导致的页面重建与用户明确重开；缓存以可靠新鲜度为依据，无须每次旋转都全文解析。
- 验收：刷新后显示新内容并合理恢复位置；删除进入缺失状态；失权进入重新授权；旋转仍保留位置且不无故重解析。

## DEF-006 — 页测断言不足以发现生产显示回归

- 已确认：`ReaderRecoveryTest.txtOpensAsLiteralParagraphs` 只检查模型 blockTexts；`ReaderDisplayTest.rotationKeepsPositionWithoutReparse` 只再次调用 open，没有实际旋转；手势测试只调用仲裁函数；ReaderEntryTest 正文样式断言仅检查模型。
- 源码：`app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderRecoveryTest.kt`、`ReaderDisplayTest.kt`、`ReaderEntryTest.kt`。
- 已确认：LargeFileInstrumentedTest 仍使用 `createForJvmTests()`，不能独立证明生产 Markwon/UI 大文件行为。
- 修复边界：Fake storage 可继续用于隔离页面测试，但不能据此声称真实 SAF/Room/进程恢复已覆盖。补独立集成场景，无需把所有单测改成端到端测试。
- 验收：实际原生 TextView 文本/spans/排版断言；真实旋转；设置交互；TOC 点击落位；搜索输入与高亮；横滑和翻页触摸；生产读取/解析/渲染大文件与取消。两个 flavor 均有非零有效用例。
- 关闭条件：DEF-001～005 的回归必须在修复前失败、修复后通过；不能仅更改用例名称或增加模型断言。

## DEF-007 — AC 判定超出证据

- 文档：`docs/plans/round-2-acceptance-verdict.md`。
- 问题：TXT、完整语法、生命周期和大文件等部分 PASS 使用模型测试或未覆盖全部行为的走查作为充分证明；“全部 MVP 功能路径可走通”强于现有证据。
- 处理：逐条重新核对受影响 AC；当前存在已确认功能缺陷的项使用 FAIL，部分覆盖用 PARTIAL，未执行用 NOT_RUN。原测试命令成功记录保留，但收窄其证明范围。
- 验收：每个 PASS 对应匹配生产路径的测试/人工记录；所有引用证据注明源码标识、环境、样本和实际断言；模拟器不能证明 E-Ink 残影/刷新。

## 第二轮修复记录 — 2026-09-18（独立复验后）

按本轮推荐顺序执行：DEF-008 → DEF-004 → DEF-003 → DEF-005 → 补齐 DEF-006 → 复核 DEF-007；
DEF-001/002 无新反证，未重复实施（其历史 CLOSED 记录保留）。

第二轮门禁结果（同一次调用，全部通过）：`test lint ktlintCheck` +
`assembleFullDebug/EinkDebug/FullRelease/EinkRelease` +
`:app:connectedFullDebugAndroidTest` 29/29 + `:app:connectedEinkDebugAndroidTest` 29/29 +
`:core:markdown:connectedDebugAndroidTest` 5/5 + `:core:storage:connectedDebugAndroidTest` 16/16；
JVM 552 variant / 264 unique，0 failure/error。（`assembleEinkRelease` 首跑出现 D8 `OutOfMemoryError`，
重启 Gradle daemon 后两个 release 构建通过——属 daemon 堆环境问题，非代码问题。）

环境：emulator-5554（AVD dev_arm64_api35，Android 15 / API 35，**模拟器，非 E-Ink 实机**）；
JDK 17 / AGP 8.13.2 / Gradle 8.13 / Kotlin 2.0.21。

### DEF-008 — Activity 销毁时 Compose 越界崩溃（根因已定位）

- 根因：`ReaderViewModel` 在 `Dispatchers.IO` 上写 Compose 观察的 `ui` / `search` StateFlow。
  StateFlow 写入是 **snapshot 写入**：Compose 的 apply-notification 机制会在**写入线程**上驱动重组合
  （本轮复现：`AndroidPrefetchScheduler.<init>` → `Choreographer.getInstance()` →
  `IllegalStateException: The current thread must have a looper!`，完整栈见
  `app/build/outputs/androidTest-results/.../flavors/eink/TEST-*.xml`）；
  同一机制在主线程 destroy 期间并发修改 SlotTable，即原始报告的
  `SlotWriter.removeGroup` → `ArrayIndexOutOfBoundsException`（index=-246）→
  `RuntimeException: Unable to destroy activity`。
- 修复：
  - `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt`：open / 新鲜度检查 / 搜索 /
    库内链接解析全部改为在 `viewModelScope`（Main）中运行并写入状态，阻塞与 CPU 工作通过
    `withContext(ioDispatcher)` 下沉；`recordRecent` 同样下沉。位置节流/持久化协程不触碰
    Compose 观察状态，保持在 IO。
  - `app/src/androidTest/.../PageViews.kt`：新增 `onMain { }` 纪律——View 树读取与
    `Activity.setRequestedOrientation` 一律在主线程执行（原先从 instrumentation 线程直接调用
    Activity/View API）；`rotateTo()` 封装"主线程请求 + 线程外等待"。
- 失败复现证据（确定性）：新增 `ReaderThreadingTest.composeStateIsOnlyWrittenOnTheMainThread`
  注册 `Snapshot.registerApplyObserver`，记录每次 snapshot apply 的线程。修复前：
  `Compose state was applied off the main thread: [DefaultDispatcher-worker-6: 1 state(s), …]`
  （3 次非主线程 apply）；修复后通过。
- 修复后验证：`:app:connectedFullDebugAndroidTest` / `:app:connectedEinkDebugAndroidTest`
  各 29/29；联合调用（full+eink+markdown+storage 同一次 Gradle 调用，即复验报告的条件）连续 2 轮全绿；
  修复前同条件与单套件共 5 轮未复现，故按"偶发"处理并保留残余风险。
- 未验证范围 / 残余风险：原始 AIOOBE 崩溃未在本地再次触发（修复前后各若干轮均未复现），
  其与本次定位的 off-main snapshot 机制的一致性来自调用栈证据而非直接复现；
  Compose 运行时自身缺陷未被排除。**不标 CLOSED**，状态记为 FIXED_PENDING_VERIFICATION。

### DEF-004 — 代码围栏与复制完整性（已修复）

- 静态确认（复验）：`MarkdownRenderer` 重建 `FencedCodeBlock` 固定三反引号；
  `RenderBlock.codeForCopy` 以"首个三反引号行"为结束——四反引号或 `~~~` 围栏包住字面 ``` 行时，
  重建切片提前闭合（显示丢内容），复制只返回 `before`。
- 修复：
  - `core/markdown/.../MarkdownContracts.kt`：新增 `RenderBlock.codeBody`（代码正文作为**数据**保存），
    `codeForCopy()` 优先返回它；旧扫描仅作为无数据行（历史测试构造）的回退。
  - `core/markdown/.../DocumentParser.kt`：解析时把 fenced/indented 代码的 `literal` 存入 `codeBody`。
  - `core/markdown/.../MarkdownRenderer.kt`：重建围栏长度 = `max(3, 正文中最长反引号串 + 1)`，
    正文末无换行时补一个，避免切片被自身内容提前闭合。
- 失败复现证据：临时还原"固定三反引号 + 无 codeBody"后
  `ReaderEntryTest.codeFenceWithInnerFenceShowsWholeBody` 失败（`after` 不再出现在页面上）；
  JVM 侧四个新用例同时变红。
- 修复后验证：`:core:markdown:test` 65 tests / 0 failed（新增 4 项：四反引号+内部围栏、
  波浪线围栏、缩进代码去缩进、普通围栏逐字复制）；页测断言真实页面上 `before` / ``` 行 / `after`
  全部呈现且 `codeForCopy()` 恰为 `before\n```\nafter`。
- 未验证范围：`source` 切片仍为"重建后的规范形式"（非逐字节），该取舍此前已声明；
  复制按钮的剪贴板内容未在设备上读回（断言的是同一 `codeForCopy()` 值）。

### DEF-003 — 重复匹配的映射身份（已修复）

- 静态+运行确认（复验）：`SearchHighlight` 用 `displayed.indexOf(sub)` 取**首次出现**，
  重复匹配被合并到同一区间；复验探针 `displayed="x x!", visible="x x", ranges=[0..0,2..2], current=2..2`
  返回 `current=0..0, others=[]`。
- 修复：`core/reader/.../SearchHighlight.kt` 改为**保序映射**——同一子串在 visible 中位于第 k 次出现，
  就映射到 displayed 的第 k 次出现；displayed 中出现次数不足时**丢弃该匹配**（不猜测）；
  current 无法定位时保留 `blockFallback`。相同字符串仍走直通快路径。
- 修复后验证：JVM `SearchHighlightTest` 5 项新用例（复验探针原样、前后步进、标题位移+重复、
  列表子弹位移+重复、无法定位不猜测）修复前 5/5 红、修复后全绿；`:core:reader:test` 105 tests / 0 failed。
  页级：完整套件下真实 span 逐段断言（`searchFindsNeedleAndSteps`：背景/前景 span 逐段 `substring` 恰等于
  匹配词，步进后 current 落到第二个匹配）。
- 未验证范围：表格内重复单元格的 cell 级高亮仍按"单元格内重定位"规则工作（相同内容的两个单元格都会点亮），
  这是 R2-06 既有约定，本轮未改；链接内重复词未单独走查。

### DEF-005 — 同文档重开：索引行之外的真实可达性（已修复）

- 静态确认（复验）：`ReaderWiring.StorageReaderGateway.describe` 只读 Room 行；授权撤销或文件消失时
  该行（及其指纹）不变，因此指纹比较无法发现；`null == null` 也会被当成"未变"。
- 修复：
  - `core/reader/.../ReaderSession.kt`：新增公开 `readerFailureReasonOf(Throwable)`（复用名称映射，
    不引入 `reader → storage` 依赖）。
  - `app/.../ReaderViewModel.kt`：`ReaderStore` 新增 `probeAccess(documentId)`；新鲜度检查要求
    指纹**非空**且相等，并且 `probeAccess` 确认可读才复用旧内容；行仍在但不可达 → 直接进入
    `AUTHORIZATION_LOST` / `FILE_MISSING` 错误态；指纹未知 → 一律重新加载。
  - `app/.../ReaderWiring.kt`：生产 `probeAccess` 真正打开 SAF 流（只读，立即关闭）并按名称分类失败。
- 失败复现证据（修复前）：
  - page 替身：`reopenAfterGrantRevocationWithUnchangedIndexShowsReauthorize`、
    `reopenAfterFileVanishesWithUnchangedIndexShowsGonePrompt`、
    `unknownFingerprintNeverCountsAsFresh` 三例超时（缓存内容遮蔽错误态 / 未知指纹被当作新鲜）。
  - **真实存储边界**（新增 `ReaderStorageBoundaryTest`：真实 Room `LeafletDatabase` +
    `LibraryRepositoryImpl` + `DocumentSourceImpl` + `StorageReaderGateway`，仅 SAF provider 用
    `ContentAccess` 替身）：`revokedGrantWithUnchangedRowSurfacesReauthorize`、
    `vanishedFileWithUnchangedRowSurfacesGonePrompt` 两例超时。
- 修复后验证：同上 5 例 + `reachableUnchangedDocumentIsReusedWithoutReReading`（可达且指纹未变时
  只多一次可达性探测、不重新加载解析）全部通过；`:app:connectedFullDebugAndroidTest` 29/29。
- 未验证范围：真实设备上的 SAF 授权撤销仍以 `ContentAccess` 边界替身模拟（未在真机手动撤销授权）；
  旋转带来的额外一次 describe + 一次 open 的耗时未做性能测量。

### DEF-006 — 测试替身与生产存储行为不一致（已补齐）

- 替身边界：`FakePageStore` 现在区分 `denyAll`（describe 即失败）、`grantRevoked`（行与指纹不变、
  仅访问失败）、`fileGone`（行在、文件消失）、`unknownFingerprint`（指纹 null），并记录 `openCount`；
  provider 层差异由 `ReaderStorageBoundaryTest` 用真实 Room + 真实 reader 链路单独覆盖（见 DEF-005）。
- 边界锁定：DEF-003 的保序映射与 DEF-004 的围栏/复制各有 JVM + 页级回归，且均验证"修复前失败"。
- 设备套件：本轮新增 8 个用例（DEF-008 1 + DEF-004 1 + DEF-005 3 + 真实存储边界 3），
  full / eink 各 29 用例；`ReaderThreadingTest` 作为线程纪律的常驻门禁。
- 未验证范围：E-Ink 实机残影/刷新/灰度（模拟器不可替代）；横滑与长按的真机手势记录；TalkBack。

### DEF-007 — 复核（重新汇总）

- 依修复后证据重新核对 `round-2-acceptance-verdict.md`：新增"第二轮修订"条目，
  把 DEF-003/004/005/006/008 相关的 AC 依据更新为**本轮生产路径用例**（真实 Room + SAF 边界的
  重开错误态、保序高亮 span、代码围栏显示与复制、线程纪律门禁），仍缺项者保持 PARTIAL/NOT_RUN。
- 关闭条件：本轮失败记录（DEF-008 崩溃报告、复验探针输出）原样保留在本文档与设备报告中，
  不用历史绿灯覆盖；DEF-008 因"偶发未再触发"不标 CLOSED。

## 统一关闭记录

每个缺陷关闭时追加以下信息，不删除原始问题描述：

修复轮环境（2026-09-18）：emulator-5554（AVD dev_arm64_api35，Android 15 / API 35，
**模拟器，非 E-Ink 实机**）；JDK 17 / AGP 8.13.2 / Gradle 8.13 / Kotlin 2.0.21。
修复后门禁结果：`./gradlew test lint ktlintCheck` 全绿（JVM 534 variant / 255 unique，0 failed）；
`:app:connectedFullDebugAndroidTest` 21/21、`:app:connectedEinkDebugAndroidTest` 21/21、
`:core:markdown:connectedDebugAndroidTest` 5/5（改用生产 Markwon 解析器）、
`:core:storage:connectedDebugAndroidTest` 16/16；`assembleFullDebug/EinkDebug/FullRelease/EinkRelease` 四包全绿。

### DEF-001 — TXT / 降级内容被二次解释为 Markdown

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownContracts.kt（RenderBlock.literal / ParsedDocument.literalText）
  core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt（TXT 与 PLAIN_FALLBACK 分块标记 literal）
  core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt（renderSpanned 对 literal 直返原文）
  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt（ParagraphEntry：literal 块走纯文本分支）
  app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt（displayedText 对 literal 不 strip）
失败复现证据：
  临时还原（rendered 计算与 ParagraphEntry 分支忽略 block.literal，即修复前的“无条件 renderSpanned”）
  → :app:connectedFullDebugAndroidTest ReaderRecoveryTest.txtOpensAsLiteralParagraphs 失败：
  ComposeTimeoutException: Condition still not satisfied after 15000 ms（页面上再也找不到字面 `# Not a heading`）。
修复后命令、用例与结果：
  ./gradlew :core:markdown:test → 62 tests，0 failed
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿（含 txtOpensAsLiteralParagraphs：
    页面上 `# Not a heading` 与 `[not-a-link](nowhere) **literal**` 原样出现，且 TXT 文档不创建任何 Markwon AndroidView）
  ./gradlew :app:connectedEinkDebugAndroidTest → 21/21 绿
双 flavor / 设备覆盖：full 与 eink 均在 emulator-5554（AVD dev_arm64_api35，Android 15 / API 35）执行。
未验证范围：E-Ink 实机残影/刷新（无设备）；降级块（PLAIN_FALLBACK）的页级断言沿用同一代码路径，未单独造样本。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-002 — 排版变化未应用到已有 TextView

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt
  （新增 applyTypography：factory 与 update 共用；行距按 px 经 TypedValue 换算，字号仍为 sp）
失败复现证据：
  临时还原（update 只 setText）→ ReaderDisplayTest.typographyChangesReachExistingTextViews 与
  fontFamilyChangeReachesExistingTextViews 双双失败：ComposeTimeoutException（已有 TextView 的
  textSize / typeface 永不变化）。
修复后命令、用例与结果：
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿；两用例断言真实 TextView：
    改字号 → textSize 变化且全部正文条目字号一致；改行距 → lineSpacingExtra 增大；改字体 → typeface == SERIF
双 flavor / 设备覆盖：full + eink（同一套件）在 emulator-5554 执行。
未验证范围：逐档（字号 0..4 / 行距 0..2 / 边距 0..2）全组合走查未执行；跨进程持久化仍由 JVM/走查承担（REQ-0007 AC-1 维持 PARTIAL）。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-003 — 富文本搜索高亮错位

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  core/reader/src/main/kotlin/io/leaflet/reader/SearchHighlight.kt（按“首现匹配”映射；不可定位才整块回退）
  app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt（highlightFor 改为接收入口真正渲染的字符串）
  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt（按块计算 rendered，交给映射与 Spanned 渲染同源；
    保留 Markwon Spanned（不再 toString），高亮在副本上叠加）
失败复现证据：
  临时还原（displayed = block.text）→ ReaderEntryTest.searchFindsNeedleAndSteps 失败：
  heading highlight covers exactly the searched word expected:<[Leaflet]> but was:<[aflet P]>
  （偏移量恰为 `# ` 标记长度，与缺陷描述一致）。
  JVM 侧：EntryBindingsTest.headingHighlightLandsOnTheRenderedWord 固定 0..4（渲染坐标）与 2..6（源码坐标）差异。
修复后命令、用例与结果：
  ./gradlew :core:reader:test → 97 tests，0 failed（SearchHighlightTest：首现映射 / 空串早退 / 无法定位返回 null）
  ./gradlew :app:testFullDebugUnitTest → 绿（EntryBindingsTest 新增 4 项映射用例）
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿；页测逐 span 断言
    substring 恰好等于 page-search-needle / Leaflet（含步进后 current span 落到正确那一个）
双 flavor / 设备覆盖：full + eink 在 emulator-5554 执行。
未验证范围：链接 / 重复词 / 表格内针点的真机观感走查（映射规则已有 JVM 用例，未逐样例走查）。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-004 — AST 重建导致 Markdown 语义变化

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt
  （文本转义 escapeText/escapeLines/needsEscapeAtLineStart；Strikethrough 分支；行内代码加宽反引号定界；
    引用块按行 `>` 输出并保留段落分隔；列表续行缩进保持嵌套；表格按 TableBlock→TableHead/TableBody→TableRow 的真实层级切片；
    visibleTextOf 改为 AST 文本 walker（原 Markwon 文本渲染器对 BlockQuote 输出占位符、对 Strikethrough 丢内容））
  core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt（沿用切片，literal 标记）
失败复现证据：
  实现期间新用例即红：`strikethroughKeepsItsNode`（visible text 由 `~~deleted~~` 变 `«deleted»`）、
  `multiParagraphQuoteKeepsBothParagraphs`（`«first linesecond line»`）、`escapedMarkersStayLiteralText`
  （可见文本多出尾随 `\`）、`fullDocumentStructureRoundTrips`（表格可见文本丢列分隔）。
修复后命令、用例与结果：
  ./gradlew :core:markdown:test → 62 tests，0 failed（新增 MarkdownRoundTripTest 7 项：转义字面、
  删除线节点、行内代码逐字符、嵌套列表保持父级、多段引用保两段、行首字面、全文档结构往返；
  既有 R2-02 完整性与锚点用例继续全绿）
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿（markdownRendersMarkersAsSpansNotCharacters：
  真机 real TextView 无字面标记且 StrongEmphasisSpan 生效）
双 flavor / 设备覆盖：JVM（core:markdown）+ full/eink 页测（emulator-5554）。
未验证范围：全部 GFM 语法的页级「所见即所写」只覆盖标题/加粗；转义后 source 切片与原始字节不再逐字相同
  （原文档已声明“normalized”取舍，新增转义后仍为非逐字契约）；页级未逐语法走查。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-005 — 重开同文档绕过刷新和失权处理

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt
  （open() 同文档时先做新鲜度检查：重读 describe 比较指纹；未变→保留同一 ParsedDocument；
    变→走完整 open；行缺失→FILE_MISSING；失权→AUTHORIZATION_LOST；新增 refresh() 显式重读；
    awaitLoaded 增加 generation 下界，避免旧的缓存结果被当成本次重载结果）
  core/reader/src/main/kotlin/io/leaflet/reader/ReaderSession.kt
  （openDocument 记录结果 generation；新增 currentGeneration()/lastLoadedGeneration()；状态携带 generation）
  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt（菜单新增“刷新”入口 + reader_refresh 字符串）
失败复现证据：
  临时还原（open() 同文档直接 return）→ ReaderRecoveryTest 三个用例失败：
  reopenAfterExternalEditShowsNewContent / reopenAfterDeletionShowsGonePromptInsteadOfCachedContent /
  reopenAfterGrantLossShowsReauthorizeInsteadOfCachedContent（均 ComposeTimeoutException，缓存内容遮蔽新状态）。
修复后命令、用例与结果：
  ./gradlew :core:reader:test → 97 tests，0 failed（新增 3 项 generation 语义用例）
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿（含 4 项 DEF-005 用例：
    外部修改后重开显示新正文且旧正文消失；删除 → 缺失提示；失权 → 重新授权提示；
    未变化重开保持同一 document 实例与 restoreBlock；refresh() 在指纹不变时仍重读）
双 flavor / 设备覆盖：full + eink 在 emulator-5554 执行。
未验证范围：真实 SAF 授权撤销 / 进程重建路径仍靠走查（页测用 FakePageStore）；旋转时新增一次 describe 读取
  的耗时未计入基线（未做性能测量）。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-006 — 页测断言不足以发现生产显示回归

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：
  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt（新：真实 view tree 读取——TextViews、span 类型/范围、
    textSize/lineSpacingExtra/typeface、宿主 Activity；内存 DataStore + 生产 SettingsViewModel）
  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderScreenShim.kt（可选 settingsViewModel 注入）
  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderDisplayTest.kt（真实旋转、排版落到已有 TextView、字体、翻页点按）
  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderRecoveryTest.kt（TXT 字面页断言、MD 富文本 span、DEF-005 四例）
  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderEntryTest.kt（真实输入框搜索 + 真机 span 范围断言 + TOC 点击落位）
  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt（FakePageStore 支持外部修改/删除/失权/同指纹）
  core/markdown/src/androidTest/kotlin/io/leaflet/markdown/LargeFileInstrumentedTest.kt（改用生产 Markwon 解析器）
失败复现证据（关闭条件：DEF-001～005 的回归必须修复前失败、修复后通过）：
  DEF-001 → txtOpensAsLiteralParagraphs 失败（字面文本不在页面上）
  DEF-002 → typographyChangesReachExistingTextViews / fontFamilyChangeReachesExistingTextViews 失败
  DEF-003 → searchFindsNeedleAndSteps 失败（expected:<Leaflet> but was:<aflet P>）
  DEF-004 → MarkdownRoundTripTest 多项失败（实现期间即红）
  DEF-005 → 三个重开用例失败
  以上还原均在临时 revert 下实测，还原后恢复修复并全部转绿。
修复后命令、用例与结果：
  ./gradlew :app:connectedFullDebugAndroidTest → 21/21 绿（原 12）
  ./gradlew :app:connectedEinkDebugAndroidTest → 21/21 绿（原 12）
  ./gradlew :core:markdown:connectedDebugAndroidTest → 5/5 绿（10MB 多小块 / 巨块降级 / 取消 / stale 守卫，且用生产解析器）
双 flavor / 设备覆盖：full 与 eink 各 21 用例；emulator-5554（Android 15 / API 35）。
未验证范围：E-Ink 实机残影/刷新/灰度（模拟器不可替代）；横滑手势的仲裁仍以纯表 + 点按区用例覆盖，
  未做真机 500ms 长按/横滑记录；TalkBack 未覆盖。
验收人 / 日期：执行 agent / 2026-09-18
```

### DEF-007 — AC 判定超出证据

```text
状态：CLOSED（2026-09-18）
修复文件 / commit（若已有）：docs/plans/round-2-acceptance-verdict.md（新增“DEF-007 修订附录”）
失败复现证据：原文件 REQ-0002 AC-1/AC-4/AC-6、REQ-0003 AC-7、REQ-0007 AC-2、REQ-0008 AC-1/AC-3
  的 PASS 依据为模型级断言或未覆盖全部行为的走查（与源码冲突，见各 DEF 的确认项）。
修复后命令、用例与结果：
  REQ-0002 AC-1 → PARTIAL（往返语义 + 页级仅标题/加粗）
  REQ-0004 AC-4/AC-6、REQ-0003 AC-7、REQ-0007 AC-2、REQ-0008 AC-1/AC-3 → 依据更新为
  DEF-006 后的生产路径用例（真实 TextView span/排版、真实旋转、真实输入与高亮）；仍缺项的保持 PARTIAL/NOT_RUN
  “全部 MVP 功能路径可走通”收窄为“本轮 21+21 页测覆盖的路径”，并新增模拟器证据边界说明与证据缺口清单。
双 flavor / 设备覆盖：文档修订；引用的用例覆盖 full + eink（emulator-5554）。
未验证范围：E-Ink 实机残影/刷新/对比、1000+ 标题性能轮、翻页像素尺测、大图峰值、10MB 真机搜索手感、
  跨进程排版持久化、TalkBack。
验收人 / 日期：执行 agent / 2026-09-18
```

```text
状态：
修复文件 / commit（若已有）：
失败复现证据：
修复后命令、用例与结果：
双 flavor / 设备覆盖：
未验证范围：
验收人 / 日期：
```

本清单不授权自动提交、发布、签名、许可证选择或实现 M2 功能。E-Ink 实机、正式 ID、许可证与生产签名仍是独立发布前置条件，不与软件缺陷混为同一关闭项。

## 独立复验追加记录 — 2026-09-18

本节记录上一轮关闭之后的独立复验，优先于上文历史 CLOSED。未修改产品代码；使用当前编译产物作 JVM 探针，并执行现有门禁及设备测试。

### 验证结果

- 单独执行双 flavor debug 构建、`test lint ktlintCheck` 成功；JVM XML 为 534 个 variant 记录、255 个去重用例、零 failure/error。增量结果不等于全部重新执行。
- 联合设备验收在 `:app:connectedFullDebugAndroidTest` 失败；Full XML 记录 13 项、1 项失败，套件未完整结束。
- E-Ink 历史 21/21 报告不能替代本轮结果；后续设备任务未全部完成，不声称本轮设备门禁通过。
- 环境：emulator-5554 / Android 15 API 35 ARM64；项目尚无可绑定的提交，结论针对当时工作区。报告在 build 目录中，后续执行可能覆盖。

### DEF-003 — 重复匹配被映射到首次出现（重新打开）

- 证据：`core/reader/src/main/kotlin/io/leaflet/reader/SearchHighlight.kt:93` 在 displayed 与 visible 不同时使用 `displayed.indexOf(sub)`，丢失匹配序号；LinkedHashSet 随后合并相同范围。
- 已执行探针：直接调用当前编译产物的 `highlightForBlock`，输入 displayed=`x x!`、visible=`x x`、ranges=`[0..0, 2..2]`、current=`2..2`。
- 实际输出：`BlockHighlight(others=[], current=0..0, blockFallback=false)`。
- 预期：当前匹配落在第二个 x（`2..2`），其余匹配保留第一个 x（`0..0`）。这是映射函数的运行复现，不等同于已完成对应真实页面走查。
- 修复验收：保持每次匹配的身份/顺序；覆盖容器换行、重复词、相同单元格内容与前后步进。不能把“总选首次出现”作为正确性测试预期。

### DEF-004 — 三反引号正文被误当结束围栏（重新打开）

- 证据：`MarkdownRenderer.kt:488` 重建 FencedCodeBlock 固定使用三反引号；`MarkdownContracts.kt:50` 的 codeForCopy 将正文中首个以三反引号开头的行当作结束行。
- 可触发原文：使用四反引号或波浪线围栏，正文依次包含 `before`、三个反引号、`after`。这是合法的代码正文内容。
- 已执行探针：构造当前重建逻辑会产生的 RenderBlock（外层三反引号、正文含三反引号），调用当前编译产物 codeForCopy，只返回 `before`，未返回正文中的反引号和 `after`。本轮未补完整 parser → UI 自动回归。
- 修复验收：代码正文作为独立数据完整保存，或正确选择/识别围栏长度与类型；显示与复制均不丢内容。覆盖四反引号、波浪线围栏、内部围栏行及缩进代码。

### DEF-005 — Fake 失权行为与生产 describe 不一致（重新打开）

- 静态确认：`ReaderWiring.kt:33` 的 StorageReaderGateway.describe 只执行 `DocumentDao.findById`，从 Room 行构造指纹，不触碰真实 SAF 文件或授权。
- `ReaderViewModel.kt:213` 比较相同指纹后恢复旧 Content。真实文件授权撤销而索引未改变时，这条路径不会发现失权；null 指纹相等也可能被当作“未变”。
- 页测替身 FakePageStore.describe 在 denyAll 时抛 SecurityException，remove 时直接删除其记录；其行为没有覆盖“真实 provider 已变化、Room 索引仍在”的场景。
- 证据边界：生产调用链缺口已确认；本轮没有操作真实 SAF 授权撤销。
- 修复验收：真实 Room 行保持存在且指纹不变，provider 失权/删除后重开仍进入正确错误态；未知指纹不得证明新鲜。外部修改并刷新后重开显示新内容，配置重建仍合理复用解析结果。

### DEF-006 / DEF-007 — 测试与关闭结论重新打开

- DEF-006：新增 TextView、输入和旋转断言是有效进展，但 DEF-003/004 的边界尚未锁定，DEF-005 的替身掩盖生产路径差异，且设备套件出现 DEF-008。
- DEF-007：不能维持“全部 CLOSED”。本文件已更正当前状态；`round-2-acceptance-verdict.md` 仍需依据修复后证据重新汇总，不直接覆盖历史运行日志。
- 关闭条件：上述反例转为有效回归；真实存储边界与 UI 检查各自报告；本轮失败原因明确且相关完整套件通过。保留失败记录，不能用历史或单次重试绿灯掩盖未解释的崩溃。

### DEF-008 — Full 页面套件在 Activity 销毁时崩溃（新增）

- 状态：OPEN；优先级 P1；对应 R2-07 / REQ-0011。
- 已确认运行失败：Full XML 13 tests / 1 failure，报告挂在 `ReaderRecoveryTest.revokedGrantShowsReauthorizePrompt`；测试进程未完整结束。
- 异常：`RuntimeException: Unable to destroy activity`，cause 为 `ArrayIndexOutOfBoundsException: length=640; index=-246`。
- 关键栈：`androidx.compose.runtime.SlotTableKt.dataAnchor` → `SlotWriter.removeSlots/removeGroup` → `CompositionImpl.dispose` → Activity 销毁生命周期。
- 本地证据：`app/build/reports/androidTests/connected/debug/flavors/full/index.html`；`app/build/outputs/androidTest-results/connected/debug/flavors/full/` 内 XML 和 `logcat-io.leaflet.reader.debug-crash-report.txt`。
- 根因：未确定。可能涉及应用状态更新、测试生命周期或 Compose 运行时；当前证据不足以归因，也不能直接标为环境偶发问题。
- 建议诊断：先保存失败日志与源码标识；分别复跑对应单例、前序生命周期/旋转用例组合、完整 Full 套件，检查 Main 线程状态与组合销毁时机。定位后再决定最小修复，不直接升级整个 Compose 工具链。
- 关闭条件：有可解释的根因及针对性验证，相关生命周期场景和完整双 flavor 页面套件通过；若仅偶发复现，记录复现频率与残余风险，不无依据关闭。

## 独立复验追加记录 — 2026-09-19

### 本轮结论与验证

结论：针对性修复有实质进展，现有自动门禁通过，但 DEF-003 表格重复匹配仍有确定性错误，不能完成 MVP 全量验收。DEF-006/007 随对应覆盖与证据缺口重新打开。

执行命令（exit 0）：

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck \
  :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest --console=plain -q
```

- 环境：emulator-5554 / Android 15 API 35 ARM64，非 E-Ink 实机；Git 仍无已提交源码基线，针对检查时工作区。
- 双 debug 构建、单测、lint、格式门禁成功；JVM 当前 XML 为 552 个 variant 记录、264 个去重用例，零 failure/error，包含有效增量复用。
- Full 29/29、E-Ink 29/29，failure/error 均 0。本轮 XML 时间分别为 2026-09-18T23:26:10 / 23:26:57 UTC，即本地 2026-09-19。
- 本轮未重跑 core:storage/core:markdown 独立设备套件，未重建 release，未验证 E-Ink 硬件。
- 门禁通过不覆盖本节已复现的表格反例；未修改产品代码或测试，只更新缺陷记录。

### 已确认的修复进展

- DEF-003 普通文本：重复运行上一轮探针（displayed=`x x!`、visible=`x x`、ranges=`[0..0,2..2]`、current=`2..2`），当前编译产物返回 `others=[0..0], current=2..2`，原反例已修复。
- DEF-004：FencedCodeBlock 重建围栏按正文反引号长度扩展；生产解析将 AST 原始 codeBody 传给 RenderBlock，复制优先使用该字段。新增代码围栏页面用例所在双 flavor 套件通过；本轮不重新打开该项。
- DEF-005：真实 ReaderWiring 已实现 probeAccess，实际打开并关闭 SAF 流；同指纹重开调用探针，未知指纹不再当作可靠新鲜度。新增 ReaderStorageBoundaryTest 使用真实 Room/DocumentSourceImpl/Gateway，provider 访问以 ContentAccess 替身注入错误；所在套件通过。真实设备系统授权撤销仍非本轮验证范围。
- DEF-008：本轮双 flavor 完整套件未复现原崩溃。保留 FIXED_PENDING_VERIFICATION；线程/测试访问纪律改动与当前通过可确认，但“StateFlow 写入直接等同 snapshot 写入并导致原始 SlotTable 崩溃”的因果解释未由本轮证明，不将推断升级为已确认根因。

### DEF-003 — 表格不同单元格被同时标为当前匹配

- 状态：OPEN，P1；属于原缺陷“重复匹配保持身份”的未完成子项，不新增重复编号。
- 源码：`app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt:84` 的 cellSearchHighlight，尤其 `:115`。
- 机制：函数仅收到单元格文本、整块匹配范围和整块文本，没有单元格在整块中的位置。它提取当前匹配字符串后，在每个单元格独立 indexOf；两个单元格内容相同，就都成为当前匹配。
- 已执行运行探针：调用当前编译产物；blockVisible=`x\tx`，highlight=`others=[0..0], current=2..2`，对第一个和第二个内容均为 `x` 的单元格分别调用函数。
- 两次实际输出均为 `BlockHighlight(others=[], current=0..0, blockFallback=false)`。
- 预期：第一格为普通匹配 `others=[0..0], current=null`；第二格才为当前匹配 `current=0..0`。前后步进只改变对应单元格的当前样式。
- 证据边界：这是实际生产映射函数的 JVM 运行反例；本轮未新增表格视图自动用例，不声称已完成该反例的屏幕截图验证。
- 最小修复方向：携带单元格范围/身份，将全块匹配坐标与本单元格范围相交后再转换为局部坐标；不要仅凭相同字符串猜测归属。
- 关闭条件：同一行、跨行、跨列重复文本及单格多次匹配都能逐个定位；真实表格页证明同一时刻只有选中项具有当前匹配样式，其他项保留普通高亮；中日文/emoji 范围正确。

### DEF-006 / DEF-007 — 剩余覆盖与判定缺口

- DEF-006：已有普通文本 span、存储边界和围栏回归是有效进展；仍需增加以上重复单元格用例，且修复前必须失败。只验证“某格找到该词”不能证明全局匹配身份正确。
- DEF-007：`round-2-acceptance-verdict.md` 的 REQ-0008 AC-3 仍记 PASS，同时承认表格重复单元格高亮为“已知限”；该限制直接违反当前匹配定位要求，不是经用户批准的范围豁免。该 AC 应记 PARTIAL，并关联 DEF-003。
- 清单原来的总状态、表格状态与历史记录互相冲突；本轮已统一当前汇总，历史记录保留。只有完成剩余修复及证据重核，才可重新关闭 DEF-006/007。

## 再次复验 — 2026-09-19（表格子项未修复）

- 本轮再次读取生产实现并调用当前编译产物，DEF-003 原表格反例仍成立，不新增重复缺陷编号。
- `EntryBindings.cellSearchHighlight` 仍没有单元格范围/身份参数。blockVisible=`x\tx`、当前匹配=`2..2`，分别对两个 `x` 单元格调用，均返回 `others=[], current=0..0`；第一格错误地获得当前匹配样式。
- 相关文件 SHA-256：`app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt` = `66663cff0d2f29bcddd05b5dd86563bb45f7a9c97a2fbe789d8b648a794de961`，供后续确认验收对应源码。
- 测试检查：现有 EntryBindingsTest 涉及单格 Ada、不同文字单元格及单格重复 Ada，没有锁定“两个相同单元格只有一个当前匹配”的反例；本轮没有添加测试或修改产品代码。
- 状态不变：DEF-003、006、007 保持 OPEN；DEF-008 保持 FIXED_PENDING_VERIFICATION。本轮未发现需另建编号的新缺陷；该表述不等于全仓无其他缺陷。
- 本次联合命令 `./gradlew :app:assembleFullDebug :app:assembleEinkDebug test lint ktlintCheck :app:connectedFullDebugAndroidTest :app:connectedEinkDebugAndroidTest --console=plain -q` 返回 0。JVM 552 variant / 264 unique，零 failure/error；Full 29/29、E-Ink 29/29，XML 时间为 2026-09-19T03:03:12 / 03:04:01 UTC。现有测试通过仍未覆盖上述已复现反例。
- 本轮未复现 DEF-008，但重复通过不能单独证明原崩溃因果已闭环。core 独立设备套件、release 和 E-Ink 实机未在本轮验证。

## 表格子项修复轮 — 2026-09-19（DEF-003 → DEF-006 → DEF-007）

承接上一轮复验结论（表格重复单元格误标当前匹配、该子项无生产视图断言、verdict 仍记通过），本轮完成修复与证据重核。

### 根因（两处，叠加）

1. `EntryBindings.cellSearchHighlight` 只有「单元格文本 + 整块匹配范围 + 整块文本」，**没有单元格在整块中的位置**；它对每个单元格独立 `indexOf(当前匹配子串)`，两个内容相同的单元格因此都成为当前匹配（复验探针与本次独立复现一致：`blockVisible=x\tx`、`current=2..2` 时两格均返回 `others=[], current=0..0`）。
2. 坐标基不唯一：块级高亮以**管道切片**（`block.text`）为基准，而单元格布局与搜索坐标在**可见文本**（`blockTexts`，`\t` 分列、`\n` 分行）里。实测 `| x | x |` 两行两列时，visible 第 2 个匹配被映射到**另一行**的格子（`current=30..30`），再叠加逐格字符串猜测。

### 修复

- `app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt`
  - 新增 `tableCellSpans(blockVisible)`：从块自身可见串按 `\n` / `\t` 切出**每格的行主序区间**（空单元格保留）。
  - `cellSearchHighlight(...)` 增加 `cellSpan` 参数；有区间时走 `positionalCellHighlight`——**先把块级范围与格子区间求交**，命中才转局部坐标，其它格一律不得点亮；区间缺失（网格不匹配 / 旧调用）才回落到旧字符串搜索（`legacyCellHighlight`）。
- `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt`
  - 表格块级高亮改以**块可见文本**为基准（与单元格布局同一坐标系），消除跨行漂移。
  - `TableBlockEntry` 接收 `visibleText`，逐格把 `cellSpans[row][col]` 传入；仅当可见网格与渲染网格行列数一致时才启用位置化路径。

### 失败复现证据（修复前）

- JVM：把位置化分支替换为旧字符串搜索后，新增 5 个用例全部变红——
  `identicalCellsOnlyTheSelectedOneIsCurrent` / `threeIdenticalCellsOnlyTheSelectedOneIsCurrent` /
  `identicalCellsAcrossRowsKeepTheirOwnCell` / `cellWithInlineMarkersMapsToTheRenderedText` /
  `spanOutsideTheBlockDegradesWithoutSpanOrCrash`（`EntryBindingsTest`，19 tests / 5 failed）。
- 设备：同一还原下 `ReaderEntryTest.duplicateTableCellOnlyTheSelectedOneIsCurrent` 失败：
  `exactly one cell carries the current-match style expected:<1> but was:<0>`。

### 修复后验证

- `EntryBindingsTest` 19 tests 全绿：两格/三格/跨行重复、单元格内联标记、越界区间、网格断言。
- 设备生产视图断言（`ReaderEntryTest.duplicateTableCellOnlyTheSelectedOneIsCurrent`）：4 个同文本单元格中
  **恰好 1 格**带当前匹配样式（黑底白字加粗）、其余 3 格为普通匹配样式（黄底）；步进后当前样式移动到另一格。
  断言读取的是单元格 `Text` 节点的 `SemanticsProperties.Text` span（表格单元格是 Compose `Text`，不经 TextView）。
- 全量门禁（同一次调用）：`test lint ktlintCheck` + 四个 assemble 全绿；
  `:app:connectedFullDebugAndroidTest` 30/30、`:app:connectedEinkDebugAndroidTest` 30/30、
  `:core:markdown:connectedDebugAndroidTest` 5/5、`:core:storage:connectedDebugAndroidTest` 16/16；
  JVM **576 variant / 270 unique**，0 failure/error。环境：emulator-5554（Android 15 / API 35，模拟器）。

### 本轮涉及文件 SHA-256（按上一轮惯例固定，供后续复验确认源码；每轮修复后需重新固定）

```text
df7b8d2d3030de809bea2ca8808f7cb39c4a37358751794a26ac4ab64975bf05  app/src/main/kotlin/io/leaflet/reader/ui/reader/EntryBindings.kt
7ab585c45e32d00c594b38543bae0927c0df438a8968c945982b38a88640a732  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt
367abb2e3308c94a3cba4672131294c7ba67ff60096bf47b70946717b59a52e7  app/src/test/kotlin/io/leaflet/reader/ui/reader/EntryBindingsTest.kt
be1d4d0606fcd49ab05eb6ad8d8132e5cbf7e6df51fcdea8498f2b40acc00af2  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderEntryTest.kt
cde7b3ec8b1c88a1d2553afa56ae75e89afd8b79525178c9fb242074ddf7853c  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt
```

（上一轮固定的 `EntryBindings.kt = 66663cff…de961` 锚定的是**修复前**版本，已被本轮替换；两轮记录均保留。）

### DEF-006 / DEF-007 关闭说明

- DEF-006：表格重复匹配的**回归已存在且在修复前失败**（JVM 5 项 + 设备 1 项），另有生产视图断言锁定「同一时刻只有选中格是当前匹配」；加上此前的真实存储边界、线程门禁、围栏/复制回归，关闭条件满足。
- DEF-007：`round-2-acceptance-verdict.md` 已按修复后证据重汇总（AC-3 恢复 PASS 并写明本轮证据），同时清理了失引用例名 `tocResolvesBothSections`、孤立代码围栏、把已修的 DEF-005 仍列在「阻塞发布」的问题、以及仍写「首现匹配」的过期契约描述。

### 未验证范围（本轮）

E-Ink 实机残影/刷新/灰度；真机手动撤销 SAF 授权；TalkBack；表格单元格内含制表符等极端形状（网格不匹配时回落到旧字符串搜索，未单独造样本）；DEF-008 的原始崩溃仍未复现（保持 FIXED_PENDING_VERIFICATION）。

## DEF-009 — 任务列表条目生产页面渲染为空（R3-03 复验发现）

- 发现方式：R3-03「Markdown/TXT 全语法显示验收」在生产视图上逐语法核对时发现——同一份文档里
  标题、正文、列表、引用、代码、分隔线、表格都有内容，**唯独任务列表条目是一个空 TextView**。
- 复现（设备，生产解析器）：
  - 文档 `- [ ] open task` / `- [x] done task`；
  - 生产解析结果：`kind=LIST text=[] src=[] visible=[open task\ndone task]`——**源码切片为空**，页面显示空条目；
  - AST 形状（反射 dump，Markwon 4.6.2）：`Document → BulletList → TaskListItem(done=false/true) → Paragraph → Text`，
    其中 `io.noties.markwon.ext.tasklist.TaskListItem extends org.commonmark.node.CustomBlock`。
- 根因：`BlockSlicer.listItems` 用 `node.children().filterIsInstance<ListItem>()` 取列表项。任务列表的项是
  **CustomBlock 而非 commonmark `ListItem`**，过滤后列表项集合为空 → 该块源码切片为 `""` → 条目渲染为空。
  JVM 路径不受影响（本地 shim 把标记作为 `ListItem` 的子节点），所以此前只有设备路径暴露该缺陷。
- 最小修复（`core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt`）：
  - `listItems` 改为遍历**任意**子节点作为列表项，不再按 `ListItem` 过滤；
  - 每项按**类名**识别 Markwon 任务项（避免 JVM 路径加载 Android 类），反射读取 `isDone()` 并加
    `[ ] `/`[x] ` 前缀（读取失败则不加，不猜测）；本地 shim 的 `TaskMarker` 子节点分支保持不变，两者不叠加。
- 失败复现证据（修复前）：
  - JVM `MarkdownRoundTripTest.listWithNonListItemChildrenStillRebuilds`（非 `ListItem` 子节点 → 切片为空）
    与 `taskMarkerItemKeepsItsCheckboxMarker` 均红（`67 tests / 2 failed`）；
  - 设备 `ReaderSyntaxPageTest.taskListItemsKeepMarkerAndTextOnDevice` 红（切片 `"- open task\n- done task"`，
    无复选框标记）。
- 修复后验证：
  - JVM `:core:markdown:test` 全绿；设备 `ReaderSyntaxPageTest` 5/5 绿（断言切片非空且含 `[ ]`/`[x]`、
    可见文本为两项、页面上两个条目文本都真实渲染、无空白条目）；
  - `:app:connectedFullDebugAndroidTest` / `:app:connectedEinkDebugAndroidTest` 全绿（见 R3-01～04 执行报告）。
- 未验证范围：任务项的复选框**绘制**（drawable/leading margin）只在设备截图层面对应，本清单不做截图判定；
  嵌套任务列表、任务项内含多段落未单独造样本。
- 验收人 / 日期：执行 agent / 2026-09-19（R3-03）

## DEF-012 — 切文档加载期间补存混用新 ID 与旧内容（2026-09-20 代码审查）

- 结论：**已确认并在设备上复现**（审查为静态确认；本轮回归实测到误写）。
- 机制：`startOpen(B)` 只更新 `currentId = B`，`currentDoc`/`currentDescriptor`/`lastVisibleBlock` 仍是 A；
  `flushPosition()` 无 Loading/Failed 与 doc↔currentId 一致性检查，`persist()` 又无条件读取全局
  `currentDescriptor` 取指纹 → A→B 加载未完成时退到后台，会把 **B 的 ID + A 的位置/锚点/指纹** 写进 B 的进度行。
  已存的两条时序都成立：加载中 flush；以及延迟保存任务执行时 `currentDescriptor` 已换成 B。
- 修复（`app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt`）：
  1. 新增 `currentDescriptorId`：描述符与它所属文档绑定；
  2. `startOpen` 在**切换文档**（`currentId != documentId`）时清空上一文档快照（doc/descriptor/id/stored/
     lastVisibleBlock），同文档重载仍保留（会话要用它判断缓存可复用）；
  3. `flushPosition()` 只允许在 `Content` 且 `content.document.documentId == currentId` 时写；
  4. `persist()` 仅在 `currentDescriptorId == documentId` 时使用缓存描述符，且只有该文档仍是当前文档时才回写缓存，
     避免延迟保存污染当前文档的描述符。
- 失败复现（修复前，设备）：`ReaderStateConsistencyTest.switchingDocumentsDuringLoadNeverWritesTheOldAddressUnderTheNewId`
  红 —— `B must not be written while it loads: [5]`（B 的行被写成 A 的块 5）。修复后绿。
- 修复后验证：该用例与 DEF-013 用例定向 2/2 绿；受影响的既有类单独运行
  （`ReaderDisplayTest` 6/6、`ReaderRecoveryTest` 14/14、`ReaderStorageBoundaryTest` 3/3、
  `ZzActivityRecreationTest` 1/1）全绿。
- 未验证范围：真实 SAF provider + 真机后台切换的组合未走查；本轮为 FakePageStore（含慢加载/慢 describe 注入）。

## DEF-013 — 同文档新鲜度检查把阅读位置拉回打开时地址（2026-09-20 代码审查）

- 结论：**已确认并在设备上复现**（不同 TextView 实例 = 组合被卸载重建）。
- 机制：`checkFreshnessThenKeep` 先写 `Loading` 再恢复旧 `Content`；`Loading` 会让 `ReaderScreen` 卸载
  `ReaderContent`（`rememberLazyListState` 随之销毁）；重新挂载后 `LaunchedEffect(docId)` 用
  `state.restoreBlock` 定位，而该字段只在 `onLoaded` 写入、随滚动/TOC 保存**不更新** → 已读到第 20 块时
  一次较慢的同文档重查会把页面拉回打开时的位置。
- 修复（同一文件）：
  1. 同文档重查**不再写 Loading**：内容保持挂载，只在结论为失败或需要真正重载时才替换状态
     （保持不变时完全不写状态，因此也不触发重组合/重挂载）；
  2. 加固：`syncRestoreAddress()` 在**真实保存**（节流通过或 TOC 落位）时把最新地址同步进 `Content.restoreBlock`，
     使任何后续重挂载（旋转/配置变化）恢复到最新地址而不是打开时地址；按滚动 tick 不更新（避免高频 UI 变化）。
- 失败复现（修复前，设备）：`ReaderStateConsistencyTest.sameDocumentRecheckDoesNotRemountTheReader` 红 ——
  断言捕获的 `TextView` 实例在重查后**不再是同一个**（组合被卸载重建）。修复后绿。
- 连带修正（修复改变了可观察时序，属测试同步问题、非产品问题）：
  - `ReaderStorageBoundaryTest.reachableUnchangedDocumentIsReusedWithoutReReading` 原先等待 `Content` 的
    Loading→Content 转换；现在改为等待探针（`openCount`）发生，断言不变（只多一次可达性 open、不重解析）。
  - `ZzActivityRecreationTest.activityRecreationKeepsTheDocumentAndRestoreAddress` 原先在 `jumpToBlock` 之前
    记录地址；现在先推进地址再记录（因为 `restoreBlock` 现随保存同步）。
- 未验证范围：真实长文档 + 慢 provider 的观感走查；E-Ink 实机。
- 验收人 / 日期：执行 agent / 2026-09-20

## 本轮设备环境（影响全量运行，如实记录）

宿主机并行运行 2 个模拟器（emulator-5554 = 本项目 AVD `dev_arm64_api35`、emulator-5556），
负载均值 ~13.5–14.5；此状态下**大合并运行**会出现间歇性 `No compose hierarchies found`
（活动启动失败，测试体未执行）。同一批类**单独或小批量运行全绿**：
`ReaderDisplayTest` 6/6、`ReaderRecoveryTest` 14/14、`ReaderStorageBoundaryTest` 3/3、
`ZzActivityRecreationTest` 1/1、`ReaderStateConsistencyTest` 2/2、此前定向 14/14。
整包双 flavor 全量门禁仍需在环境恢复后重跑，不把定向绿灯写成全量通过。
