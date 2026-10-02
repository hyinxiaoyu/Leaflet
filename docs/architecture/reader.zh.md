# 阅读器架构

[English](reader.md) | 中文

## 概览

本文规定 ADR-0004/0005/0006 与 REQ-0002/0003/0005/0006/0007/0008 的 Reader 边界。管线为可取消后台文本读取 → Markwon/CommonMark 解析 → 项目拥有的顶层分块 → 基于块的 RecyclerView 渲染。普通块使用 TextView/Spanned；围栏代码与表格有独立横向滚动条目。重排复用解析块。TXT 保持字面文本，标题回退为 frontmatter title → 首个 H1 → 文件名。不把任意大文档放进一个 TextView，不实现完整分页引擎。

Reader 暴露 Loading、Content、Degraded 与 Failed。加载反馈静态，降级说明巨块或不支持语法的回退，失败提供恢复动作。取消或过时工作释放流/中间 AST；旧文档加载不得覆盖新文档。100 KB 至 10 MB 样本目标为有界可取消读取，无 OOM/ANR/崩溃；巨块可以有说明地回退为纯文本，不能静默截断。

`ParsedDocument` 包含块、TOC 与 PositionMap。搜索使用可见文本而非 Markdown 源偏移，按字面匹配、可取消，并由代次保护。`ReadingPosition` 保存版本化块索引/偏移、进度及可选锚点。版本匹配时优先按块恢复，再于内容/布局变化后验证并回退至锚点/进度。同文档复用前必须验证元数据与实际读取权限。

相对链接/图片在 Markdown 模块分类，仅通过 `DocumentSource` 在授权树内解析。拒绝未知协议及越界；远程图片保持静态占位。本地图片使用有界内存的采样平台解码并关闭流。链接/按钮/代码复制/表格横向手势优先于触控区翻页。

翻页采用可见视口减重叠（默认 5%，限制 3–8%）、即时滚动及边界钳制。未保存有效选择时 STANDARD 与 E-Ink 均默认翻页；保留明确选择的连续滚动。位置、TOC、搜索高亮、文本重排与分发策略复用块级契约。Reader 不直接访问 Room、SAF、ContentResolver 或 Onyx。

## 1. 管线：解析 → 分块 → RecyclerView（T09 Spanned/排版落定）

```text
DocumentSource.openStream(documentId)
    ↓ (cancellable background coroutine, IO dispatcher)
Read text (UTF-8, strip BOM; unknown encoding gets explicit fallback)
    ↓
MarkdownRenderer.parse (Markwon → commonmark-java 0.13.0 AST, background)
    ↓
Project block splitter (top-level Heading/Paragraph/List/Quote/Code/Table/Image/Divider)
    ↓
ParsedDocument(blocks + TOC + positionMap), each block has exact source slice + fence language
    ↓ (main thread binds only; reflow reuses blocks)
RecyclerView / Compose block rendering:
    ordinary block → TextView / Markwon Spanned (`renderSpanned`; JVM fallback plain text)
    FencedCodeBlock → horizontal-scroll item + language label + copy button
    TableBlock → horizontal-scroll item (NORMAL; WRAP/CARD are reserved)
```

排版（T09/REQ-0007 FR-1–FR-4/FR-7/FR-10）：`ReaderStyle`（`core/markdown/.../MarkdownContracts.kt`）
承载字号 step（5 档 0–4，默认 2，scale 0.85+0.15×step）、系统字体白名单（`system/sans-serif/serif`，
固定候选——family-query 各 OEM 面差异大，未验证，见 REQ-0007 OQ；monospace 只给代码块）、行距 step
（3 档 0–2，multiplier 1.00/1.25/1.50）、页边距 step（3 档 0–2 → 8/16/24dp）、soft-wrap 开关。
全部经 DataStore 持久化（`SettingsStore` 复用 `font_size_step/font_family/line_spacing_step/page_margin_step/code_soft_wrap`
键；Reader 经参数接收样式，不直读存储）。soft-wrap 未设置时按模式默认：E-Ink ON（FR-7），Standard 取存值。
随显示模式变化的布局（REQ-0007 FR-13–FR-15，2026-10-02）：上述档位含义不变，但每个档位解析为多少由 `DisplayProfile.readingLayout` 经 `ReadingLayoutResolver`（`core/reader/.../ReadingLayout.kt`）决定，Reader 与设置页预览共用这一条路径；对照表见 `eink.md` §6。Standard 保持 16 sp / 8–16–24 dp / 叠加式行距 / 右侧参差；E-Ink 使用 18 sp 基准、精确的 1.45–1.80 行高、16–28–44 dp 页边距、流式正文 36 em 行宽，以及 Markdown 段落的词间两端对齐加断字。Markwon `TextView` 为 `ReaderTextView`，它记住平台自身的换行与链接颜色默认值，因此切换模式时是恢复而不是假设这些值。字面 TXT / 降级块（Compose `Text`）从不两端对齐，按原样显示。正文颜色取自主题，而不是 Activity 主题中半透明的文字颜色。断字遵循设备语言环境的规则，因此与设备语言不同的文本可能断字不佳或不断字。

排版变化保当前块上下文：记录首可见块，重组后 `keepBlock` + instant `scrollToItem` 回到该块并经 `jumpToBlock`
即时存位（复用 T08 接口；无 smooth/animated，无像素级精确承诺）。

代码块（FR-5–FR-7）：等宽 + 语言标签（fence info 首词小写，无标注/缩进代码为空标签，未知名原文展示不虚报）
+ 复制按钮（`codeForCopy` 逐字符 fence 内原文；静态“已复制”提示，无动画）+ soft-wrap 开关（容器常驻；
ON 原地换行，OFF 横向滚动到行尾，AC-4）。无语法高亮引擎（Non-goal；灰度可辨靠默认主题色）。

表格（FR-8/FR-9）：仅 NORMAL——整体置横向滚动容器，窄屏全部列经横滑可达（AC-5），内横滑被 scroller
消费不触发翻页（T08 仲裁）。`TablePlugin` 与 `TableEntryPlugin` 二选一不混用（沿用 T06 选择：前者挂载；
后者只做 `TableEntry` 演进路径，见 `TableModePolicy` 注释；WRAP/CARD 只在 `TableRenderMode` 枚举与文档记录为
M2+ 扩展，不加 UI 开关）。

Markwon Spanned 升级（T06 留白闭环）：文本条目从 source text 升级为 `renderSpanned`（block source slice
重 parse → Spanned；含表格/任务列表；后台线程调用，主线程只 set；未知语法降级纯文本，仍按块分条目）。
深色/灰度可辨靠 Markwon 默认主题色（取 TextView textColor），无强制着色。

- 禁止把整篇文档无条件塞进单个超大 TextView（ADR-0004）。
- TXT 路径复用同一管线：整文视为 Paragraph 块序列，标题规则仍适用（文件名回退）。
- 标题规则：frontmatter title（限定子集，异常元数据不得使正文失败）→ 首个 H1 → 文件名（无扩展名）。

## 2. 四态：加载 / 内容 / 降级 / 错误

`ReaderState`（契约 4）为 Reader 对外唯一状态表达：

| 状态 | 触发 | UI 要求 |
| --- | --- | --- |
| Loading | 流读取/解析/分块进行中 | 静态文字＋百分比/细进度条；禁止旋转动画、无限 loading、shimmer（E-Ink 约束） |
| Content | 正常完成 | 块列表＋TOC＋搜索可用 |
| Degraded | 超大单块纯文本降级；未知语法降级；远程图片占位 | 有说明横幅（讲清降级原因与范围）；其余块正常可用 |
| Failed | 授权丢失/文件消失/流失败/解析失败 | 明确原因＋可恢复动作（重选目录/刷新/返回）；不得 crash 或空白卡死 |

约束：后台任务可取消；快速切换文档时旧结果不得覆盖新文档（任务 token / DisposableEffect 语义）；取消后释放资源（关闭流、丢弃中间 AST）。

## 3. 大文件策略（10 MB 可取消有界）

- 100 KB / 1 MB / 5 MB / 10 MB 样本行为一致：可取消、无 OOM/ANR/crash；10 MB 不承诺完整富文本体验，但必须给出有说明的纯文本降级（ADR-0006）。
- 单个巨大块（巨大段落/代码块/表格）拆分为纯文本条目 + 降级横幅；禁止静默截断、假装读取完成、拒绝所有大文件。
- 禁止明显 O(N²) 的文档处理；frontmatter 解析为单次线性 pass，不在主线程做二次正则（research 教训）。

## 4. 位置映射契约（PositionMap）

```kotlin
interface PositionMap {
    fun blockCount(): Int
    fun visibleTextOf(blockIndex: Int): String
    fun locateAnchor(anchor: String): BlockLocation?
    fun locateQuery(query: String): List<MatchLocation>
}
data class BlockLocation(val blockIndex: Int, val blockOffset: Int)
data class MatchLocation(val blockIndex: Int, val startInBlock: Int, val endInBlock: Int)
```
搜索按可见文本，不把语法标记当正文；搜索可取消，过期结果不覆盖；切文档清除旧状态；不在每次输入时重新解析全文。
位置恢复（配合契约 5 ReadingPosition）：同版本文档优先块/偏移；内容、字体、宽度变化后校验并回退至 anchor/progress；缓存按文档变化失效；节流保存 + 退出补存。
同文档重开的新鲜度判定（R2 DEF-005 第二轮）：重新读取索引行比较指纹，**指纹未知（null）不算证据**；指纹相同时仍要经 `ReaderStore.probeAccess` 打开一次真实文档（只读、立即关闭）确认可达——索引行在授权撤销或文件消失时不会变化，只比较指纹会让旧内容遮蔽重新授权 / 缺失提示。确认未变才复用已解析文档（配置重建不重解析）；变化或不可达则走完整打开并按 `AUTHORIZATION_LOST` / `FILE_MISSING` 呈现。

## 4b. 文内搜索实现（T11/REQ-0008 落定）

```text
input → ViewModel.onSearchQuery (generation++, cancel old job, 200 ms debounce)
    ↓ (background IO; reuse ParsedDocument, no reparse)
DocumentSearch.searchCapped (case-sensitive literal; empty/blank → zero; shared block scan)
    ↓ generation guard: discard stale result
SearchUiState (matches/currentIndex/jumpToken/capped) → instant scrollToItem + static highlight
```

状态机：`:core:reader` `DocumentSearch`（字面量/大小写敏感/空查询/跨块不匹配/wrap 步进/版本守卫）+ `SearchUiState`（query/matches/currentIndex/searching/generation/jumpToken/capped）；ViewModel 持有 `_search` StateFlow，切文档与关闭时清零（FR-5），查询为空不启动 job（FR-7）。
高亮映射（R2 DEF-003 修订）：`:core:reader` `SearchHighlight` 把可见文本坐标映射到**该条目实际渲染的字符串**——Markdown 块传 Markwon 渲染文本（标记已被消费），纯文本/降级块与表格传去标记文本；映射按“匹配子串在该串中的首现位置”定位，无法定位时才整块加粗回退（`blockFallback`，复用 TOC 到达高亮约定）。旧实现以 source 切片为基准，标题等带标记的块会整体位移（`# Alpha` 偏 2 字符）。其余匹配黄色底、当前匹配黑底白字加粗——全静态，无动画（E-Ink）。表格单元格另经 `:app` `EntryBindings.cellSearchHighlight`（JVM 单测直测的纯映射，`ReaderScreen` 仅薄委托）把块级坐标重定位到 stripped cell 内（AC-3 可见文本逐格点亮，歧义时整块加粗回退承载当前位置）。

## 4c. 相对链接与本地图片（T10/REQ-0009 落定）

```text
CommonMark Link/Image nodes → ResourceLinks classification (pure JVM)
    ↓ extractBlockLinks/extractBlockImages; code blocks carry no payload; classify before resolving
RenderBlock.links/images → App tap/placeholder binding → DocumentSource.resolveRelative (inside grant)
```

- 分类（`:core:markdown` `ResourceLinks`，base-agnostic）：EMPTY/SAME_DOC_ANCHOR/LIBRARY_DOC/UNSUPPORTED_MEDIA/EXTERNAL/UNSUPPORTED_SCHEME/UNREACHABLE；图片 EMPTY/LOCAL/REMOTE（含 `//`）/UNSUPPORTED_FORMAT（SVG）/UNSUPPORTED_SCHEME/UNREACHABLE。`..` 形状不预判越界——越界由寻址层按引用目录拒绝（与 `RelativePaths` 同规则镜像，单测互锁）。
- 寻址（`:core:storage` `DocumentSource` 唯一出口）：引用文档目录为基准 + 库内相对路径归一化（树内 `..` 允许、越树拒绝、content URI 不拼接、编码规范化一致）；未知 scheme 不执行；远程图片永不进入加载器（零网络调用）。
- Fragment（`FragmentMatch`，`:core:reader`）：raw → percent-decode → TOC-rule slugify（与 REQ-0006 同源）；重复标题首个匹配 best-effort；未知锚点返回 null（静态提示、位置不变，永不假装跳首块）。
- 图片解码（`:app` `LibraryImageLoader` + `:core:reader` `ImageSample`）：SAF 树 Exact-name 步进 → provider-issued document URI → `BitmapFactory` bounds → 按视口宽 `inSampleSize`（2 的幂）→ 12MiB 上限（`MAX_DECODE_BYTES`，UNVERIFIED 待 REQ-0011 基线）再降采样；双 pass 各用各流 `use {}` 关闭；可取消（composition 离开取消、在途 pass 间 `ensureActive`）；无共享缓存（关闭即丢引用）；缺失/损坏/不支持占位（含 alt）。
- UI（`:app`，E-Ink 安全）：灰框 + alt + 尺寸文字占位，无动画；链接为块内可点击行（T08 LINK_TAP 优先于翻页区）；外链 tap 直交 OS（无确认；无应用→静态提示）；缺失/越界/unsupported 一律静态一次性提示 + dismiss，不 crash 不空白。

## 5. 导航 / 交互构件

| 构件 | 位置 | 要点 |
| --- | --- | --- |
| `PageTurn` | `:core:reader` | `turnDistance = viewport − overlap`（默认 5%，`coerceOverlap` 钳 3–8%，与 `PageTurnBehavior` 同源、`SettingsStore.pageOverlap` 同带）；`visibleHeightPx` 扣 Insets/菜单遮挡；`next/prevOffset` 首尾 clamp，快速连翻不越界 |
| `TapZones` / `GestureArbitration` | `:core:reader` | 左/中/右三等分（上一页/菜单/下一页，边界归右侧区）；链接/按钮/代码复制/横滑容器优先，长按永不翻页；只有未被消费的 plain tap 才进区决策。`shouldShowHint(seen, dismissed)` 是纯首次运行引导判断（仅在已存值为 `false` 且尚未被关闭时显示） |
| `TocNavigation` | `:core:reader` | `anchor → blockIndex` 按 TOC 顺序 occurrence-aware（第 k 个同名条目 → 第 k 个同名 heading 块），复用 `ParsedDocument.toc`（同源，无二次解析）；未知 anchor 回退 `positionOps`，仍未知返回 null；到达用静态加粗（无动画，E-Ink 安全） |
| `ReadingMode` / `keepBlock` | `:core:reader` | `CONTINUOUS_SCROLL`/`PAGE_TURN`；切换不重建 list state，首可见块不变（`keepBlock` 钳界）；`keepBlock` 同时是 T09 排版变化（字号/行距/边距）重排后滚回的接口 |
| ReaderScreen 接线 | `:app` | 恢复/TOC 跳转 `scrollToItem`（instant），翻页 `scroll { scrollBy(±distance) }`（instant）；全路径无平滑/动画 API；`PAGE_TURN` 下 `userScrollEnabled=false`。UX-06：列表是根 `Column` 中唯一参与测量的子项，所有外层控件——标题、各类提示、搜索栏、阅读菜单、进度页脚——都是外层 `Box` 内不透明的浮层。正文带有实测内边距（body inset）：即预留各类提示/搜索栏的头部预留空间（headroom），使其绝不会遮住内容；以及尾部预留（tail reserve）：从覆盖页脚加一行菜单的下限（floor）96 dp 起步，并只向实测的底部外层控件单向增长，因此最后一行总能滚动到页脚和已打开菜单之外（CR-20260930-01/03），同时在文末关闭菜单不会缩小滚动范围、移动正文（CR-20261001-03）。钳制改为向列表询问其真实滚动边界（`canScrollForward`/`canScrollBackward`），翻页距离通过 `PageTurn.visibleHeightPx` 扣除实测外层控件高度，搜索/锚点/TOC 跳转会落在顶部控件之下（`scrollToItem(target, -chrome)`）；选择章节会关闭阅读菜单。静态 `x / n · p%` 进度；E-Ink 侧走 `GenericEInkController`、Standard 侧为 NoOp |
| 持久化 | `:app` `SettingsStore` + `:core:storage` `ReaderSettings` | 阅读模式 + overlap（3–8%，默认 5%）与一次性的 `tapZonesHintSeen` 引导标记存 DataStore；落位/恢复仍走 REQ-0003 位置管线（`jumpToBlock` 即时写，不经节流）。未保存阅读模式时按显示模式取默认值（`defaultReadingModeFor`：EINK → PAGE_TURN，STANDARD → PAGE_TURN，电子书交互跟进 2026-10-01）；已保存的选择始终优先，跨显示模式切换亦然 |

E-Ink 和 Standard 均默认 `PAGE_TURN`（阅读器内为 `ReadingMode.defaultForEink`，读取偏好处为 `ReaderSettings.defaultReadingModeFor`；已保存的值始终优先）。
三区默认三等分（左右区宽度实现记录：各 1/3，REQ-0005 Open Question 初值；误触率走查留 T12/T13）。
无分页引擎/zone 编辑器/完整手势系统（本节即 T08 停止条件）。

UX-06（2026-09-30）：阅读器外层控件已成为浮层，因此正文 `LazyColumn` 的几何尺寸不再取决于 `menuVisible`/`searchOpen`（无墨水屏重排），文档标题不再永久占用页头，进度百分比成为贴底的静态页脚，触控区引导是一次性、不透明、单击即可关闭的浮层。正文在各类提示/搜索栏之下保留实测的头部预留空间，并使用感知外层控件的尾部间距，翻页距离扣除实测外层控件高度，跳转会把目标显露在顶部控件之下。见 [ux-06-delivery.md](../plans/ux-06-delivery.md)。

阅读位置保存顺序（position-save ordering，CR-20261001-04）：`ReaderViewModel` 每次请求都捕获不可变快照（snapshot）（`SaveSnapshot`：文档、块、加载代次（generation）），在调用时（INVOCATION time）——在调用方线程上、在任何派发（dispatch）之前——分配单调递增的请求序号（sequence），因此序号顺序绝不是 IO 启动顺序，并保持存储写入并发。此后每一次写入完成都会合并/收敛（coalesce）到该文档最新完成的请求：写完自己的快照后，它会重新检查并写入更新的那一个，如此循环直到写出最新的那一个（补偿写入（compensating write）本身也是一次普通存储写入，可能在更新的请求完成期间被扣住，因此一次性的修复并不够）。最新请求按文档跟踪，因此切换文档不能重定向该修复，并且只有最新请求会发布保存节流（save throttle）与位置缓存。因此无论开始或完成顺序如何，已存储的位置都是该文档最新请求的地址。`flushPosition()` 返回其自身写入的任务。跨文档身份还由 `isCurrent`（相同文档与代次）绑定。见 [code-review-2026-10-01-round-2.md](../plans/code-review-2026-10-01-round-2.md)。

最近阅读行（Recent rows，CR-20261001-07）：`RecentViewModel` 将惰性的资料库名称与文件健康（file health）查询发布为不可变的可观察（observable）元数据，并与条目列表组合，因此完成的查询会更新已发布的行；条目列表会立即发布，查询运行在一个会被下一份列表取代的任务中，且缓存的元数据会被裁剪为已列出的条目。


## 5a. 表格与代码布局（UX-07）

`TableBlockEntry` 将现有解析网格和基于位置的搜索范围绑定到 `TableGrid`。Compose `Layout` 查询所有行中带样式内容的内在宽度 (intrinsic width)，确定每列最大宽度和每行最大高度，再用固定尺寸对每个单元格测量一次。缺少的单元格保留空位置。分隔线使用主题的 `onSurface` 颜色；横向滚动和块索引仍沿用现有阅读器链路。不引入第二套解析器、依赖、存储格式或模块契约。

单元格不换行，因此网格需要每个单元格完整的单行宽度——而 Compose 约束（constraint）并不能总是表示该宽度（`Constraints.fixed` 从 262 143 px 起抛异常；针对固定版本 `ui-unit` 1.7.5 实测）。因此 `TableBlockEntry` 在测量前先校验块：`characters × 2 em + cell padding` 是未换行单元格最宽行的保守上界，超过 `MAX_CONSTRAINT_DIMENSION_PX`（260 000）的块改走既有的有界回退方案（fallback）——在普通横向滚动容器中渲染其自身源文本——而不走网格，因此不会裁剪或丢失任何字符（CR-20261001-01）。`TableGrid` 还会对两个维度做纵深防御式钳制。普通表格保持其测量对齐；两条路径下搜索范围都保持其位置映射。

`ImageEntry` 保留占满宽度的加载容器以确定解码宽度，只有位图使用 `boundedImageDimensions`：以物理像素计算 `scale = min(1, viewportWidth / bitmapWidth, heightCap / bitmapHeight)`。高度上限为经 `LocalDensity` 转换的 320 dp；布局时再将宽高转换回 dp。像素取整可能产生一个像素的差异。加载器、取消机制和远程占位沿用既有行为。

代码复制反馈复用操作按钮，不插入反馈行。组合生命周期内的 `LaunchedEffect` 以代码块和复制计数为键，两秒后清除反馈；再次点击会重新计时，组合销毁会取消任务。见 [ux-07-delivery.md](../plans/ux-07-delivery.md)。

## 6. Reader 模块禁令

- 不直接访问 Room、SAF、ContentResolver、Onyx SDK（overview §5）。
- 不自研 Markdown parser；分块层可复用解析器 AST，但不发明覆盖全部 Markdown 的自研 AST。
- 不实现真分页排版引擎、可配置 tap-zone 编辑器、完整手势系统（T08 停止条件）。

## 7. 阅读状态与导航归属（第 3 轮修复）

version-1 位置结构不变。应用在加载、当前状态、不可变保存快照（immutable save snapshot）及持久化全过程携带块索引与显示文本字符偏移。`readingLocationFor` 在保存间隔内也返回当前逻辑地址。`ReaderContent` 等待文字/条目测量，再投影为即时滚动，并忽略初始恢复回声（restore echo）。Compose Text 与原生 TextView 行起点提供坐标；`BlockPositionGeometry` 计入标签/间距。没有单一文本布局的复合条目使用有界长度/高度近似。像素为瞬态，不持久化全局像素位置。

保存排序记录在派发前统计待处理任务，在完成时释放归属，包括启动前取消。只淘汰空闲记录；八条空闲记录上限不能丢弃活跃修复目标。

相对链接任务携带文档/加载/导航归属，被取代或路由退出时取消，并在发布导航/提示前重新校验。已移交片段记录目标，允许源路由先销毁、目标稍后加载；无关阅读器不能消费。片段落点与已存偏移恢复共用一个初始滚动效果（effect），避免竞争。

见[第 3 轮修复证据](../plans/code-review-2026-10-01-round-3.md)。原生/Compose 长文本设备测试可编译但仍为 NOT_RUN；复合/媒体条目的精确视觉恢复与 E-Ink 硬件仍需设备证据。

## 可见控制跟进（2026-10-01）

REQ-0014 增加静态页脚导航和可换行操作菜单，均委托现有 ReaderContent 列表/菜单/翻页回调。BoxWithConstraints 将菜单高度限制为固定列表视口的 40%；菜单独立滚动，不改变列表尺寸。实测浮层遮挡与不缩减的尾部预留空间仍决定翻页距离及末行可达性。帮助通过会话状态复用现有教程；教程显示和初始逻辑位置恢复期间，正文输入受保护。PageGesture 在指针离开 Reader 时永久取消，包括离开后重入。存储、解析、逻辑位置契约和集中动画策略均保持不变。见 [reader-controls-product-wave2.md](../plans/reader-controls-product-wave2.md)；Android 执行仍待验证。
