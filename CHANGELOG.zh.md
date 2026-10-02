# Leaflet 更新日志

[English](CHANGELOG.md) | 中文

此处记录所有面向用户的重要变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)。
## [未发布]

### 修复

- 阅读器提供可见菜单与上一屏/下一屏控制，文档首尾禁用相应翻页按钮。阅读操作在限高菜单内换行和滚动，操作说明可重新打开；离开阅读区或在教程背后触摸不再翻页或切换底层菜单。

- 电子书式翻页支持正文左右滑动松手翻页及包含页边距的两侧点击；拖动中正文保持静止。两种发行版本未配置阅读模式时均默认逐屏翻页；明确选择的连续滚动仍可用且被保留。链接/按钮和表格/代码横滚保持优先，翻页采用最新菜单遮挡尺寸。

- 面包屑点击保留条目渲染时的资料库与文件夹前缀。延迟点击不再读取较新的屏幕状态，从而错误移动其他资料库或后来打开的文件夹。

- 延迟阅读位置写入在多次切文档后仍保留排序状态；只淘汰空闲记录。
- 阅读位置保留块内字符偏移（in-block character offset）。长文本通过当前行布局恢复，无移动补存保留已存偏移。
- 切换或退出阅读器会取消过时的相对链接导航；已移交片段仅属于目标文档。

- Markdown 表格各行现在共用列宽，提供清晰的网格分隔线和更明显的表头分隔线。
- 本地小图片保持已解码的像素尺寸；大图和长图按视口宽度及 320 dp 高度等比例缩小，不改变宽高比。
- 代码复制反馈在最近一次点击两秒后恢复，直接显示在现有操作按钮内，复制不再改变文档布局。

- 指向本文档内章节的链接（`#section`）现在会滚动到该章节；此前页面会停在原处。
- 在没有应用可以处理的情况下打开外部链接时，现在会显示可恢复的「没有可打开该链接的应用」提示，而不是毫无反应。
- 改动翻页重叠比例后，翻页立即按新比例生效；此前要等到旋转屏幕或切换阅读模式后才生效。
- 翻页模式下现在总能到达文档最后一行：只有正文末尾完全离开进度页脚和已打开的阅读菜单后翻页才会停止，而不像此前那样在最后几行仍被遮挡时就停下。
- 首个块中的搜索命中，以及从目录中选择的章节，不再被搜索栏或菜单标题遮住。搜索命中会步进到搜索栏下方，选择章节会关闭阅读菜单。
- 即使应用因 Android 的包可见性（package visibility）规则看不到处理应用，外部链接现在也会在浏览器（或其他处理应用）中打开；只有当启动确实没有处理应用时，才会显示「没有可打开该链接的应用」提示。
- 阅读位置若在另一个文档刚被打开时保存，可能被写到错误的块上（被离开文档的第 0 块，或新文档的行）。现在每次保存都携带请求时捕获的文档、块和加载代次（generation），延迟保存不再能更新当前文档的缓存或保存节流（throttle）。
- 在文档最末尾关闭阅读菜单不再让文字跳动：尾部滚动空间不再随菜单缩小，因此位置得以保持。
- 在资料库、浏览、设置与阅读器之间的导航现在在两种显示模式下都是即时的。应用此前使用导航库默认的 700 ms 淡入淡出，这在 E-Ink 上绝不应出现。
- 含极长单行单元格的 Markdown 表格不再使阅读器崩溃。此类表格会作为可横向滚动的文本单独渲染，因此不会被裁剪或丢失任何内容。
- 两次保存重叠时，无论各次写入以何种顺序开始或完成，最新的阅读位置都不再丢失——包括较慢的旧保存之后紧接的补偿写入（compensating write）。每次保存都在被请求时记录其位置，而写入存储区的最后一次写入始终是该文档最新请求的地址；过期的保存不再更新保存节流（throttle）或缓存的位置。
- 提供方行缺少显示名称、缺少 id 或缺少 MIME 类型的文件夹不再被误判为文件（文件扫描路径会把该分类从提供方行一路保留到扫描器）。无法完全分类某行的扫描现在会保留上一次的索引，而不是删除该文件夹已索引的文档；扫描仍会完成并报告受影响的行。
- 当较早、较慢的加载稍后才完成时，打开资料库不再显示错误的资料库。当前选择、其文档行、其文件夹路径与扫描进度都会保留；切换资料库后才完成的扫描刷新也无法覆盖它们。
- 最近阅读列表会在解析资料库名称与「授权失效」/「文件缺失」检查完成后立即显示结果，而不再一直保持未知状态、直到列表因其他原因发生变化。
- 文件夹扫描不再移动文件列表。进度、Cancel、Retry 以及可恢复的失败/取消文案都位于同一个保留状态区域，其尺寸不随扫描状态变化，因此开始、取消或扫描失败都不会让列表移位。
- 在没有文件夹扫描运行时按下 Cancel 不再让状态卡在「正在取消」：取消现在只作用于确实进行中的扫描。
- 当面包屑所对应的资料库或文件夹已不再是当前所在位置时，该点击会被忽略，因此过期点击不再能把刚打开的资料库跳回其根目录。

### 新增

- 阅读器首次打开时会显示一次性的触控区提示：一个不透明浮层，用随模式变化的文案标注上一页、阅读菜单与下一页三个三分之一区域。点击任意位置或「知道了」即可关闭，关闭状态会被记住，因此不再出现。
- 浏览资料库时，不含任何 Markdown 或 TXT 文件的文件夹现在也会出现。扫描只保存文件夹的相对路径；仍然不读取文件正文、不复制原文。打开该文件夹时显示现有的空占位文案。
- 浏览资料库时现在会显示面包屑 (breadcrumbs)，包含资料库根目录与每一级祖先文件夹；点击其一即可直接跳到该文件夹，而当前所在文件夹显示为当前分段，而不是又一个按钮。
- 文件与文件夹行现在会显示文件夹或文档图标，以及可读的 MD / MARKDOWN / TXT 标签，因此类型不再依赖结尾斜杠或颜色。

### 变更

- 首页现在将继续阅读、文件夹、阅读历史与导航放入同一个惰性滚动页面。只在资料库加载完成后显示首次使用引导；本地索引加载失败可重试，清空历史须确认。
- 阅读设置显示实际排版数值和即时预览，选中控件样式统一。共享壳层预留系统栏/刘海空间，根据显示模式匹配系统栏图标对比度，并提供完整灰度 E-Ink 调色板。

- 阅读器正文现在占满整个视口。文档标题、各类提示、搜索栏、阅读菜单与阅读进度行都浮在正文之上，而不再压缩正文，因此打开或关闭它们都不会再让页面重排（墨水屏闪烁的来源），标题也不再永久占用页头。进度行是低调的贴底页脚，翻页仍按可见高度减去重叠量推进，且现在按屏幕上实际存在的外层控件来测量。各类提示与搜索栏会在正文上方预留各自的空间，因此也不会遮住正文。
- 全新的 E-Ink 安装现在以翻页模式启动，而不是连续滚动；Standard 安装仍以连续滚动启动。用户显式选择的阅读模式会被保留，切换显示模式后也是如此。
- 资料库浏览一次只显示一个文件夹层级。子文件夹排在文件前面，各组按名称排序。返回向上一层。
- 资料库浏览的行现在至少 48 dp 高，长名称会换行而不是被截断，因此每个文件与文件夹都更容易点中。文件列表现在也是受高度限制的可滚动区域，这让大文件夹下 Back 控件始终留在屏幕上。
- 打开不同文件夹（或切换资料库）时，现在会从顶部显示该文件夹的列表，而不再保留上一次的滚动位置。
- 长按一条资料库并确认后，会移除它的本地索引、已索引文档、已索引文件夹、阅读位置和最近阅读记录。原始文件仍留在设备上。
- 已有安装会就地升级本地索引到数据库版本 2。升级是纯增量的：不会丢弃任何资料库、文档、阅读位置或最近阅读记录；文件夹行会在下一次成功扫描时写入。

- 资料库扫描不再在 500 篇文档处停止。文件夹里发现的受支持 Markdown/TXT 文件都会被索引。目录树条目超过 5000 时，扫描仍会明确失败并保留上一次的完整索引。

### 文档

- 完成项目文档国际化：英文主文档配套简体中文译文；`AGENTS.md` 保持全英文。

### 修复（2026-09-20 代码审查 — 阅读状态一致性)
- **切换文档时不再写坏新文档的阅读位置**（DEF-012）：切换 A→B 且 B 仍在加载时退到后台，退出补存会用 B 的 ID 配上 A 的位置/锚点/指纹写进 B 的进度行。现在描述符与其所属文档绑定，切换文档即清空上一文档快照，补存只在「内容已就绪且属于当前文档」时执行，延迟保存也不会污染当前文档的描述符。
- **同文档重查不再把阅读位置拉回打开处**（DEF-013）：原先重查会先显示 Loading，导致阅读页卸载重建并重新滚动到「打开时的位置」。现在重查期间保持当前内容挂载（不写 Loading），并把最新已保存位置同步进恢复地址，使旋转/配置变化后的重挂载落在最新位置而不是打开时位置。
- 回归：新增 `ReaderStateConsistencyTest` 两例（修复前分别复现「B 被写成 A 的块 5」与「阅读页被重挂载」），修复后通过；受影响的既有用例（显示/恢复/存储边界/重建）单独运行全绿。
### 修复（R3-01～04 执行轮 — 任务列表空条目 + 验收补测)
- **任务列表条目不再渲染为空**（DEF-009，R3-03 复验发现）：列表重建曾用 `filterIsInstance<ListItem>()` 取列表项，而 Markwon 的任务项是 `TaskListItem extends CustomBlock`，被整体过滤掉，导致该块源码切片为空、生产页面出现空条目（JVM 路径不受影响，故此前只在设备暴露）。现遍历任意列表项子节点，并按类名识别任务项、反射读取勾选状态补回 `[ ]`/`[x]` 前缀（读不到则不猜）。回归：JVM 2 例 + 设备 1 例，修复前均失败。
- 验收补测（R3-01～04，见 `docs/plans/r3-01-04-execution-report.md`）：新增生命周期稳定性 2 例（返回目录 dispose 落盘、宿主 Activity `recreate()` 后文档实例与恢复位置不变）；资料库矩阵 4 例（21 文档/5 目录/3 空目录/嵌套、双资料库隔离、500 文档上限边界与 5000 条目常量、只索引元数据不读正文）；生产视图语法页 5 例；持久化 3 例（真实磁盘 DataStore + Room，另以 `adb install -r` + `am force-stop` 两阶段证明跨进程持久化；外部编辑→刷新→位置回退与变化提示）。
- 门禁：`test lint ktlintCheck` 绿；app full/eink 各 41 例（40 通过 + 1 设计内跳过）、`core:markdown` 5/5、`core:storage` 16/16；JVM 588 variant / 276 unique。E-Ink 实机、TalkBack、真机手动授权撤销仍未验证。
### 修复（R2 第二轮缺陷修复 — 独立复验后重开项 DEF-003…008)
- **Compose 状态主线程纪律（DEF-008，崩溃根因）**：`ReaderViewModel` 原先在 `Dispatchers.IO` 上写屏幕观察的 `ui`/`search` StateFlow；StateFlow 写入即 snapshot 写入，Compose 的 apply-notification 会在写入线程上驱动重组合（复现为 `Choreographer: The current thread must have a looper!`，并在 Activity 销毁时并发修改 SlotTable，导致复验报告的 `ArrayIndexOutOfBoundsException` → `Unable to destroy activity`）。现在打开 / 新鲜度检查 / 搜索 / 库内链接解析都在 Main 上写状态，阻塞与 CPU 工作经 `withContext(IO)` 下沉；新增 `ReaderThreadingTest` 用 snapshot apply 观察器做常驻门禁。
- **代码围栏与复制完整性（DEF-004）**：代码正文作为数据保存（`RenderBlock.codeBody`），复制不再靠扫描围栏行；重建围栏长度按正文中最长反引号串自适应，因此四反引号 / `~~~` 围栏包住字面 ``` 行时不再被自身内容提前闭合（显示与复制都不丢内容）。
- **搜索高亮保留匹配身份（DEF-003）**：重复词过去一律映射到首次出现（高亮合并、current 指错词）。现在同一子串的第 k 次匹配映射到渲染串的第 k 次出现；渲染串中出现次数不足时丢弃该匹配而不猜测，current 无法定位时仍走整块加粗回退。
- **同文档重开校验真实可达性（DEF-005）**：指纹只来自索引行，授权撤销或文件消失时不会变化，因此旧内容曾遮蔽错误态。新增 `ReaderStore.probeAccess`（生产实现真正打开 SAF 流后立即关闭），指纹非空且相等**并且**可读才复用旧内容；行在而不可达 → 重新授权 / 缺失提示；指纹未知 → 一律重新加载。
- **测试替身与真实存储边界（DEF-006）**：`FakePageStore` 区分 denyAll / 授权撤销 / 文件消失 / 未知指纹四种模式；新增 `ReaderStorageBoundaryTest` 用真实 Room + 真实 `DocumentSourceImpl`/`StorageReaderGateway`（仅 SAF provider 用 `ContentAccess` 替身）覆盖「索引行不变但 provider 失权/条目消失」；页面用例由双 flavor 各 21 增至各 29。
- **验收结论重新汇总（DEF-007）**：`round-2-acceptance-verdict.md` 增补「第二轮修订附录」，受影响 AC 依据更新为生产路径用例；DEF-008 因原始崩溃未再复现按偶发处理（FIXED_PENDING_VERIFICATION），不声明全部关闭。
验证：`test lint ktlintCheck` 全绿（JVM 552 variant / 264 unique）；`:app:connectedFullDebugAndroidTest` 29/29、`:app:connectedEinkDebugAndroidTest` 29/29、`:core:markdown:connectedDebugAndroidTest` 5/5、`:core:storage:connectedDebugAndroidTest` 16/16（emulator-5554）；双 flavor debug/release 四包全绿。新回归均验证「修复前失败 / 修复后通过」。E-Ink 实机残影刷新、真机 SAF 手动撤销授权、TalkBack 仍未验证。
### 修复（R2 缺陷关闭 — DEF-001…007)
- **TXT / 纯文本降级不再被二次解释为 Markdown**（DEF-001）：`RenderBlock.literal` / `ParsedDocument.literalText` 标记 TXT 分块与 `PLAIN_FALLBACK` 降级块，条目直接按字面文本渲染；`#`、`**`、`[label](target)` 保持可见字符，纯文本降级不再被富文本解析。同内容 `.md` 仍正确富文本显示（页测同时锁定两侧）。
- **排版设置同步到已存在的 TextView**（DEF-002）：新增 `applyTypography`，AndroidView 的 `factory` 与 `update` 共用同一套字号 / 字体 / 行距（行距按 px 经 `TypedValue` 换算）；改字号、字体、行距即时作用于已有条目，不再出现同文档条目排版不一致。
- **搜索高亮落在真正显示的文本上**（DEF-003）：高亮按块以入口实际渲染的字符串（Markwon 渲染文本 / Compose 去标记文本）为映射基准，匹配按“首现出现”定位，无法定位才整块加粗回退；标题、加粗词与重复词的 span 现在恰好覆盖匹配词（页测逐 span 断言）。顺带修掉渲染文本转字符串导致 Markwon span（加粗/标题）丢失的问题。
- **AST 重建的 Markdown 语义保真**（DEF-004）：文本按行首/行内规则重新转义，`~~删除线~~` 恢复其节点，引用块按行 `>` 输出并保留段落，列表续行正确缩进保持嵌套，表格按真实 `TableBlock→TableHead/TableBody→TableRow` 层级切片；`visibleTextOf` 改为 AST 文本遍历（旧 Markwon 文本渲染器对引用块输出 `«…»` 占位符、丢弃删除线内容）。
- **重开同一文档不再绕过刷新与失权检查**（DEF-005）：同文档再次 `open()` 先重读索引行比较指纹——未变化保留同一解析结果（旋转不重解析），变化则重新加载，文件缺失进入缺失提示，授权失效进入重新授权提示；菜单新增“刷新”入口（`reader_refresh`）用于 mtime:size 未变的覆盖写。
- **页面测试改为断言生产显示行为**（DEF-006）：新增 `PageViews`（遍历真实 view tree：TextView 文本、span 类型/范围、`textSize`/`lineSpacingExtra`/`typeface`、宿主 Activity），页测由双 flavor 各 12 增至各 21，覆盖真实旋转、排版改已有视图、真实输入框搜索与高亮 span、TOC 点击落位、TXT 字面显示、外部修改/删除/失权后重开；`LargeFileInstrumentedTest` 改用生产 Markwon 解析器。
- **验收结论证据范围收窄**（DEF-007）：`round-2-acceptance-verdict.md` 逐条重核受影响 AC，确证缺陷的项收窄为 PARTIAL 并补上生产路径证据，新增“DEF-007 修订附录”（含模拟器证据边界与仍未关闭的证据缺口）。
关闭记录（修复文件、失败复现、修复后命令与结果、双 flavor/设备覆盖、未验证范围）见 `docs/plans/round-2-open-defects.md` 各 DEF 条目。验证：`./gradlew test lint ktlintCheck` 全绿（JVM 534 variant / 255 unique）；`:app:connectedFullDebugAndroidTest` 21/21、`:app:connectedEinkDebugAndroidTest` 21/21、`:core:markdown:connectedDebugAndroidTest` 5/5、`:core:storage:connectedDebugAndroidTest` 16/16（emulator-5554，Android 15 / API 35）；双 flavor debug/release 四包全绿。E-Ink 实机残影/刷新、跨进程排版持久化、TalkBack 仍未验证。
### 新增（R2-08 — 状态、许可与 Alpha 候选报告)
- 状态诚实化：README“规划阶段/尚无工程/均为计划能力”改为 M1 Alpha 候选实述 + 4 张真实截图；AGENTS.md 阶段与任务入口同步；`app/build.gradle.kts` NO-SOURCE 注释改为页测实述；`testing.md` JVM 基线 494 variant / 240 unique；`apk-size.md` R2-08 重建（full 22566877 / eink 22566617，MD5，dex 96.9%，零测试入包，零用户权限）；`THIRD_PARTY_LICENSES` 补 ui-test-junit4/ui-test-manifest 1.7.5（instrumented-only）。
- F09 修正：`release.md` “debug keystore 签名”改为无 `signingConfig` 的 UNSIGNED 实述（`apksigner DOES NOT VERIFY` 双包实证）；ADR-0007 经核查无项目许可断言（“仓库 Apache-2.0”指 Onyx 参考仓 + M5 前提措辞，无需改史）；矩阵“PASS + walkthrough pending”行已由 R2-07 真机走查替换为实证；REQ 索引与六份 IN_PROGRESS 文件头对齐。
- 验收 verdict：新增 `docs/plans/round-2-acceptance-verdict.md`（每条 AC 的 PASS / PARTIAL / NOT_RUN + 用例名/命令/记录；无测试不记 PASS）；走查截图 4 张入库 `docs/development/r207-screenshots/`；`testdata/markdown` 30 文件走查前后 hash 一致。
- 候选状态：双 release 未签名候选（MD5 见 apk-size）；仍阻塞发布：E-Ink 实机、许可证/正式 ID/签名/渠道、尺测类 NOT_RUN（翻页像素、1000+ 标题、大图峰值、10MB 搜索手感、TalkBack）。本任务零产品代码改动、零签名、零提交、零发布。
### 新增（R2-07 — 真实页面回归与 E-Ink 验收)
- 页面回归套件（`app/src/androidTest`，双 flavor 各 12 用例全绿）：`ReaderEntryTest`（MD 标题/正文/表格/代码 + 搜索 3 命中及步进 + TOC 双锚点）、`ReaderRecoveryTest`（失权/消失提示、跳转落位持久化、重进恢复块、TXT 字面性）、`ReaderDisplayTest`（EINK 同文档、点击仲裁表、旋转无重解析）、`ReaderImageTest`（本地绘制 + 远程零调用 + 损坏占位）；`createComposeRule` 内容直组（TEST 进程 Activity 在本机镜像跨进程失败，无宿主 Activity）。
- 缺陷修复（R2-07 走查发现）：搜索栏计数 `Text(status)` 只算未组（REQ-0008 FR-7 在页面上恒空白），现已补上；页测断言 “1 / 3” → 步进 → “2 / 3” 锁定。作用域内单行，无他改。
- 真机走查（emulator-5554，生产包 + 真实 SAF 授权）：加目录 → 扫描完成 → 浏览 8 文档 → MD/TOC 跳转 20/56 → 搜索 “1 / 1” 跳转高亮 → 退出重进 26/56 恢复 → 旋转保持 → 删文件重扫后冷启 “Can’t open…” + 移除条目 → 1MB/12149 块 ≈8s 无 OOM（PSS 122MB）→ 10MB Loading 6s 可 Back 取消（中值 PSS 260MB，无 ANR）→ 设置/模式切换 EINK 落盘 → eink  flavor 零配置 EINK + TXT/宽表。截图 14 张存于 /tmp/r207-*.png（走查证据，未入库）。
- 未验证：E-Ink 实机残影/刷新（无设备；矩阵 1–4、12 行维持 NOT_RUN）、TalkBack 播报；同 VM 内删文件重进显示缓存内容（旋转 no-op 权的已知取舍，冷启即正确提示，不 crash）。
### 新增（R2-06 — 可取消且与真实渲染一致的文内搜索)
- 有界搜索：`:core:markdown` 新增 `SearchLimits.MAX_SEARCH_MATCHES=5000` + 共享 `collectBlockMatches` 扫描；`BlockPositionMap.locateQuery` 与 `:core:reader` `DocumentSearch`（sync `search` + 协作取消 `searchCapped`，每块 `ensureActive` + 每 512 块 `yield`，与解析循环同节律）同源同序；10MB 重复字符查询返回前 5000 命中，永不 OOM。
- 永不静默截断：`SearchResult.capped` → `SearchUiState.capped` → 搜索栏显示“n / 5000+ · 结果过多，请细化关键词”（`reader_search_capped`）；跳转/计数/高亮均作用于同一返回窗口。
- 取消闭环：快速改查询/切文档沿用 debounce + generation 守卫 + job 取消；`searchCapped` 块级协作取消使 200k 块扫描可杀（单测锁定：取消后无部分结果、无旧高亮覆盖）。
- 表格高亮对齐真实渲染（REQ-0008 AC-3）：`:app` `EntryBindings.cellSearchHighlight`（JVM 单测直测的纯映射）把块级可见坐标重定位到 stripped cell 内，单元格内针点亮 + 保留内联样式；歧义 current 沿用整块加粗回退。段落/代码路径不变。
单测 7 项新增 + 1 项扩展（reader 4：稠密截断/稀疏完整/取消无部分结果/默认非截断；markdown 1 + 1 扩展：稠密同 cap 同序新用例 + 空白不搜扩展；app 2：单元格映射直测 + 样式与搜索 span 合成）；JVM 全绿，`test lint ktlintCheck` 全绿。
未验证：真机 10MB 搜索手感与 E-Ink 跳转残影（无设备；走 R2-07 真机矩阵，标 NOT_RUN）。
### 新增（T13 — 质量门禁与 E-Ink Alpha 候选）
- 门禁：full/eink × debug/release-unsigned 四个 APK 均构建通过；JVM 406/406；lint 零错误；ktlintCheck 通过；`:core:storage` 设备测试 15/15（Room/SAF/Scanner + 原文件 SHA-256）；`:core:markdown` 设备测试 5/5（10MB 多块解析约 599 秒模拟器 CPU；巨段/巨代码降级；2MB 协作取消；过期结果守卫）。
- 解析协作：`topLevelBlocks`、`BlockSlicer.slice` 和解析循环每 512 块 `yield`；10MB 可读性移到普通 `@Test`；在 `Dispatchers.Default` 上将取消样本调整为 2MB（`runTest` 的 60 秒虚拟预算无法容纳 50 秒以上 CPU 解析）。
- 文档：`docs/performance/apk-size.md`（22,517,277 / 22,517,017 B，差值 260）、模拟器基线（冷启动/内存/10MB 清单）、E-Ink 矩阵模拟器辅助部分；实机行仍为 NOT_RUN。
- 状态：仅未签名候选包；许可证/ID 为 TBD；没有 E-Ink 实机结论。T13 未将任何 REQ 标为 DONE（各项仍缺硬件残影、实测阈值或完整 instrumented 绿色门禁至少一项）。
### 新增（T12 — 显示策略与设置集成）
- 策略接线：`:core:eink` 新增 `DisplayModeId`、`profileFor/profileForName`、`MotionPolicy.pick/indicationOn`、`controllerForMode`、`capabilitiesForFlavor`。`LeafletTheme` 单点解析策略；EINK 下 ripple 与 overscroll glow 关闭，STANDARD 保留平台默认；导航即时切换，无 crossfade/过渡动画。
- Settings/About：双 flavor 支持热切模式并持久化；eink 默认 EINK、full 默认 STANDARD。Reader 通过 `DisposableEffect` 配对 `onReaderEnter/onReaderExit`，翻页调用 `onScrollStart → scroll → onScrollEnd → onPageTurn`，全部即时滚动。About 显示版本/flavor/临时 ID/许可证 TBD/第三方许可入口/系统重绘语义；不显示 UpdateOption、GC 间隔、Fast Mode 等硬件档位。
- 新增 6 项 `DisplayProfileTest`。E-Ink 实机残影/刷新/翻页手感及 TalkBack 仍未验证。
### 新增（T11 — 当前文档内搜索）
对当前已加载文档做字面量搜索，默认区分大小写；空/空白查询不报错、零结果。显示 `n/m`，支持上/下一个与当前匹配静态高亮。Markdown 按可见文本搜索，不匹配 `#`、`**`、`](` 等语法标记；链接目标文本按当前 TextContentRenderer 语义测试。通过位置映射即时跳转并保存；查询变化 200ms debounce、generation 守卫和取消旧任务。TXT 与大块 `PLAIN_FALLBACK` 同样可搜可跳，不重解析全文。
Reader 菜单新增搜索入口，搜索栏显示计数/无匹配提示、上/下和关闭。关闭/切文档清理旧查询、计数、高亮和任务。
未验证：真机 10MB 搜索体验、E-Ink 跳转残影与 TalkBack 播报（无设备，标 NOT_RUN）。
### 新增（T10 — 相对链接与本地图片）
链接：同文档 `#fragment` 即时滚动，未找到时静态提示且位置不变；库内文档经 `DocumentSource` 解析，仅允许树内 `..`；越界/缺失显示不可达提示；非 MD/TXT 目标只显示媒体提示；http/https/mailto/tel 仅点击后交 OS；未知 scheme 不执行，空目标无操作。
Fragment 规则为 raw → percent-decode → TOC slugify，重复标题 best-effort 首个匹配。
图片：本地按视口采样解码，`ImageSample` 初始上限 12MiB（待 REQ-0011 基线验证），可取消且 dispose 后释放；缺失/损坏/不支持显示静态 alt 占位；远程/协议相对地址仅显示“不自动加载”占位，网络调用为零；SVG 不支持。不新增网络/图片库或 WebView。
UI 为灰框 + alt + 尺寸说明，无动画；提示静态且可关闭。新增 11 项 Markdown 链接/图片分类和 7 项 fragment/采样 JVM 测试；缺失目标提示已在设备验证。
### 新增（T09 — 排版、代码块与表格）
`:core:markdown` 增加精确源切片/语言标签/逐字复制、`ReaderStyle`、NORMAL-only `TableRenderMode` 和 `renderSpanned`。未知语法降级纯文本；后台渲染，主线程绑定；JVM 可回退纯文本。
`:core:reader` 增加代码 soft-wrap 与表格模式策略。排版变化复用 blocks，通过 `keepBlock` + 即时滚动保存来保持上下文，不使用 smooth/animated。
`:app` Settings 增加字号/字体/行距/页边距 stepper 与 soft-wrap；Reader 显示语言标签、复制按钮、静态“已复制”、代码横滚和宽表横滚。无 WRAP/CARD 控件或字体包；E-Ink 未设置 soft-wrap 默认 ON。
新增 16 项 JVM 测试。冻结字号 5 档默认 2、行距 3 档默认 1、页边距 3 档默认 1（8/16/24dp）；Standard soft-wrap 使用存值。
未验证：深色/灰度人工对比、E-Ink 实机翻页/横滚、TalkBack 代码复制/表格导航及 Markwon 等宽列真实观感。
### 新增（T08 — TOC、翻页与手势仲裁）
Reader 接入分层 TOC、无标题占位、即时点击落块并保存位置；菜单切换 CONTINUOUS/PAGE_TURN；三区点击、首尾 no-op、静态进度。PAGE_TURN 禁止用户拖动滚动，生产路径无 smoothScroll；E-Ink 翻页通知 Generic controller。
新增 20 项 JVM 测试，覆盖距离/overlap/clamp/遮挡/旋转/手势仲裁/重复标题 anchor/模式保位置；默认 overlap 5%，范围 3–8%。
未验证：E-Ink 实机翻页/残影、左右区三等分误触率及无障碍翻页。
### 新增（T07 — Reader 会话、位置与最近阅读）
Reader 已接入只读 `StorageReaderGateway` 与 `ReaderViewModel`；支持加载/降级/失败/重试、即时恢复和退出补存。位置按指纹优先块/偏移，否则 anchor→progress→顶部回退；2 秒节流。最近阅读 MRU 上限 30，死项保留并提示，不自动清除。双 flavor M0 闭环演示通过；进程杀死重建当时尚无 instrumented 覆盖。
### 新增（T06 — Markdown/TXT 基础 Reader）
`:core:markdown` 以 Markwon 4.6.2 单一 `MarkdownRenderer` 接缝解析；支持可取消解析、frontmatter/H1/文件名标题、分块、32KiB 超限降级、TOC anchor、可见文本位置映射、UTF-8/BOM/替换字符与编码提示。
`:core:reader` 提供 Loading/Content/Degraded/Failed、generation 快切保护、16MiB 读取上限及 RecyclerView 分块（代码/表格独立横向滚动），不将全文放入单 TextView。新增 JVM 用例与 10MB 多块/巨块设备测试。

### 新增（T05 — 资料库扫描与目录浏览）
`LibraryScanner` 递归遍历 SAF 元数据，发现大小写不敏感的 `.md/.markdown/.txt`，保留层级；mtime+size 可靠一致才跳过；仅完整扫描成功后清理消失项。支持取消、每 Library 串行刷新和 ≥500ms 静态进度。访问上限 5000、索引上限 500，超限显式失败并保留旧索引；性能基线仍未验证。
`ContentResolverTreeLister` 使用生产 `DocumentsContract` 子文档查询，所有游标均关闭且取消可传递。App 提供目录选择、资料库列表/浏览、手动刷新/取消/重试、重复添加提示及 T07 Reader 占位路由。
JVM scanner 20 项，设备 provider 扫描与原文完整性探针通过。
### 新增（T04 — SAF 与持久化基础）
增加 LibraryRepository、只读 `DocumentSource` 框架、共享授权检查、扫描状态、位置/最近阅读，以及类型化 `StorageException`。SettingsStore 保存排版、soft-wrap、overlap 和阅读模式。新增 JVM/DAO/SAF instrumented 测试及原文件完整性探针。
### 新增（T02 — 最小工程与双 flavor）
新建 Android 工程：`:app` 与 `:core:model/storage/markdown/reader/eink`。
增加有不同 applicationId/名称的 `full` / `eink` flavors。
增加资料库空态、显示模式切换、About。
增加最小 DisplayProfile/MotionPolicy/AppCapabilities/EInkController（NoOp + Generic）及 T01 存储/Markdown/Reader 契约形状（尚无业务逻辑）。
