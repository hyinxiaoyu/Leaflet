# 阅读器架构

[English](reader.md) | 中文

## 1. 管线：解析 → 分块 → RecyclerView（T09 Spanned/排版落定）

```text
DocumentSource.openStream(documentId)
    ↓ （后台可取消协程，IO Dispatcher）
纯文本读取（UTF-8，BOM 剥离；未知编码 → 明确提示或降级，不虚报自动识别）
    ↓
MarkdownRenderer.parse（Markwon parse → commonmark-java 0.13.0 AST，后台线程）
    ↓
自有分块器（按顶层 block 切分：Heading / Paragraph / List / Quote / Code / Table / Image / Divider）
    ↓
ParsedDocument(blocks + toc + positionMap)，每块附 exact source slice + fence language
    ↓ （主线程只做绑定；排版变化复用 blocks，仅重绑定）
RecyclerView / Compose 逐块渲染（MarkwonAdapter 思想）：
    普通块 → TextView 条目（Markwon Spanned via renderSpanned；JVM 回退纯文本）
    FencedCodeBlock → 独立横向滚动条目＋语言标签＋复制按钮（悬浮层，非插件）
    TableBlock → 独立横向滚动条目（NORMAL 模式；WRAP/CARD 只做预留）
```

排版（T09/REQ-0007 FR-1–FR-4/FR-7/FR-10）：`ReaderStyle`（`core/markdown/.../MarkdownContracts.kt`）
承载字号 step（5 档 0–4，默认 2，scale 0.85+0.15×step）、系统字体白名单（`system/sans-serif/serif`，
固定候选——family-query 各 OEM 面差异大，未验证，见 REQ-0007 OQ；monospace 只给代码块）、行距 step
（3 档 0–2，multiplier 1.00/1.25/1.50）、页边距 step（3 档 0–2 → 8/16/24dp）、soft-wrap 开关。
全部经 DataStore 持久化（`SettingsStore` 复用 `font_size_step/font_family/line_spacing_step/page_margin_step/code_soft_wrap`
键；Reader 经参数接收样式，不直读存储）。soft-wrap 未设置时按模式默认：E-Ink ON（FR-7），Standard 取存值。
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

## 2. 三态：加载 / 错误 / 降级

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
    fun visibleTextOf(blockIndex: Int): String // 可见文本（不含 Markdown 语法标记）
    fun locateAnchor(anchor: String): BlockLocation? // TOC 跳转 / 位置恢复用
    fun locateQuery(query: String): List<MatchLocation> // 字面量搜索，按可见文本
}

data class BlockLocation(val blockIndex: Int, val blockOffset: Int)
data class MatchLocation(val blockIndex: Int, val startInBlock: Int, val endInBlock: Int)
```
- 搜索按可见文本，不把语法标记当正文；搜索可取消，过期结果不覆盖；切文档清除旧状态；不在每次输入时重新解析全文。
- 位置恢复（配合契约 5 ReadingPosition）：同版本文档优先块/偏移；内容、字体、宽度变化后校验并回退至 anchor/progress；缓存按文档变化失效；节流保存 + 退出补存。
- 同文档重开的新鲜度判定（R2 DEF-005 第二轮）：重新读取索引行比较指纹，**指纹未知（null）不算证据**；指纹相同时仍要经 `ReaderStore.probeAccess` 打开一次真实文档（只读、立即关闭）确认可达——索引行在授权撤销或文件消失时不会变化，只比较指纹会让旧内容遮蔽重新授权 / 缺失提示。确认未变才复用已解析文档（配置重建不重解析）；变化或不可达则走完整打开并按 `AUTHORIZATION_LOST` / `FILE_MISSING` 呈现。

## 4b. 文内搜索实现（T11/REQ-0008 落定）

```text
输入 → ViewModel.onSearchQuery（generation+1，取消旧 job，200ms debounce）
    ↓ （后台 IO dispatcher；复用已加载 ParsedDocument，不重解析）
DocumentSearch.searchCapped（字面量大小写敏感，空/空白零结果；共享 collectBlockMatches 扫描）
    ↓ （generation 守卫：过期结果丢弃，不覆盖）
SearchUiState（matches + currentIndex + jumpToken + capped）→ 屏幕 scrollToItem（instant）+ 静态高亮
```

- 状态机：`:core:reader` `DocumentSearch`（字面量/大小写敏感/空查询/跨块不匹配/wrap 步进/版本守卫）+ `SearchUiState`（query/matches/currentIndex/searching/generation/jumpToken/capped）；ViewModel 持有 `_search` StateFlow，切文档与关闭时清零（FR-5），查询为空不启动 job（FR-7）。
- 高亮映射（R2 DEF-003 修订）：`:core:reader` `SearchHighlight` 把可见文本坐标映射到**该条目实际渲染的字符串**——Markdown 块传 Markwon 渲染文本（标记已被消费），纯文本/降级块与表格传去标记文本；映射按“匹配子串在该串中的首现位置”定位，无法定位时才整块加粗回退（`blockFallback`，复用 TOC 到达高亮约定）。旧实现以 source 切片为基准，标题等带标记的块会整体位移（`# Alpha` 偏 2 字符）。其余匹配黄色底、当前匹配黑底白字加粗——全静态，无动画（E-Ink）。表格单元格另经 `:app` `EntryBindings.cellSearchHighlight`（JVM 单测直测的纯映射，`ReaderScreen` 仅薄委托）把块级坐标重定位到 stripped cell 内（AC-3 可见文本逐格点亮，歧义时整块加粗回退承载当前位置）。

## 4c. 相对链接与本地图片（T10/REQ-0009 落定）

```text
commonmark Link/Image 节点 → ResourceLinks 分类（DocLinkKind/ImageKind，纯 JVM）
    ↓ （块装配期 extractBlockLinks/extractBlockImages；代码块不带 payload；解析期只分类不寻址）
RenderBlock.links/images → :app 点击/占位绑定 → DocumentSource.resolveRelative（授权树内寻址）
```

- 分类（`:core:markdown` `ResourceLinks`，base-agnostic）：EMPTY/SAME_DOC_ANCHOR/LIBRARY_DOC/UNSUPPORTED_MEDIA/EXTERNAL/UNSUPPORTED_SCHEME/UNREACHABLE；图片 EMPTY/LOCAL/REMOTE（含 `//`）/UNSUPPORTED_FORMAT（SVG）/UNSUPPORTED_SCHEME/UNREACHABLE。`..` 形状不预判越界——越界由寻址层按引用目录拒绝（与 `RelativePaths` 同规则镜像，单测互锁）。
- 寻址（`:core:storage` `DocumentSource` 唯一出口）：引用文档目录为基准 + 库内相对路径归一化（树内 `..` 允许、越树拒绝、content URI 不拼接、编码规范化一致）；未知 scheme 不执行；远程图片永不进入加载器（零网络调用）。
- Fragment（`FragmentMatch`，`:core:reader`）：raw → percent-decode → TOC-rule slugify（与 REQ-0006 同源）；重复标题首个匹配 best-effort；未知锚点返回 null（静态提示、位置不变，永不假装跳首块）。
- 图片解码（`:app` `LibraryImageLoader` + `:core:reader` `ImageSample`）：SAF 树 Exact-name 步进 → provider-issued document URI → `BitmapFactory` bounds → 按视口宽 `inSampleSize`（2 的幂）→ 12MiB 上限（`MAX_DECODE_BYTES`，UNVERIFIED 待 REQ-0011 基线）再降采样；双 pass 各用各流 `use {}` 关闭；可取消（composition 离开取消、在途 pass 间 `ensureActive`）；无共享缓存（关闭即丢引用）；缺失/损坏/不支持占位（含 alt）。
- UI（`:app`，E-Ink 安全）：灰框 + alt + 尺寸文字占位，无动画；链接为块内可点击行（T08 LINK_TAP 优先于翻页区）；外链 tap 直交 OS（无确认；无应用→静态提示）；缺失/越界/unsupported 一律静态一次性提示 + dismiss，不 crash 不空白。

| 构件 | 位置 | 要点 |
| --- | --- | --- |
| `PageTurn` | `:core:reader` | `turnDistance = viewport − overlap`（默认 5%，`coerceOverlap` 钳 3–8%，与 `PageTurnBehavior` 同源、`SettingsStore.pageOverlap` 同带）；`visibleHeightPx` 扣 Insets/菜单遮挡；`next/prevOffset` 首尾 clamp，快速连翻不越界 |
| `TapZones` / `GestureArbitration` | `:core:reader` | 左/中/右三等分（上一页/菜单/下一页，边界归右侧区）；链接/按钮/代码复制/横滑容器优先，长按永不翻页；只有未被消费的 plain tap 才进区决策 |
| `TocNavigation` | `:core:reader` | `anchor → blockIndex` 按 TOC 顺序 occurrence-aware（第 k 个同名条目 → 第 k 个同名 heading 块），复用 `ParsedDocument.toc`（同源，无二次解析）；未知 anchor 回退 `positionOps`，仍未知返回 null；到达用静态加粗（无动画，E-Ink 安全） |
| `ReadingMode` / `keepBlock` | `:core:reader` | `CONTINUOUS_SCROLL`/`PAGE_TURN`；切换不重建 list state，首可见块不变（`keepBlock` 钳界）；`keepBlock` 同时是 T09 排版变化（字号/行距/边距）重排后滚回的接口 |
| ReaderScreen 接线 | `:app` | 恢复/TOC 跳转 `scrollToItem`（instant），翻页 `scroll { scrollBy(±distance) }`（instant）；全路径无 `smoothScroll`/`animateScrollToItem`（E-Ink FR-6）；`PAGE_TURN` 下 `userScrollEnabled=false`（手指拖动不抹屏）；菜单/标题为 in-flow 行，`viewportSize` 已是可见高（overlay 遮挡若出现则经 `PageTurn.visibleHeightPx` 扣除）；静态 `x / n · p%` 进度；E-Ink 侧 `GenericEInkController.onPageTurn`、Standard 侧 NoOp |
| 持久化 | `:app` `SettingsStore` + `:core:storage` `ReaderSettings` | 阅读模式 + overlap（3–8%，默认 5%）存 DataStore；落位/恢复仍走 REQ-0003 位置管线（`jumpToBlock` 即时写，不经节流） |

- E-Ink 默认 `PAGE_TURN`、Standard 默认 `CONTINUOUS_SCROLL`（`ReadingMode.defaultForEink`，首次值；之后存档优先）。
- 三区默认三等分（左右区宽度实现记录：各 1/3，REQ-0005 Open Question 初值；误触率走查留 T12/T13）。
- 无分页引擎/zone 编辑器/完整手势系统（本节即 T08 停止条件）。

## 6. Reader 模块禁令

- 不直接访问 Room、SAF、ContentResolver、Onyx SDK（overview §5）。
- 不自研 Markdown parser；分块层可复用解析器 AST，但不发明覆盖全部 Markdown 的自研 AST。
- 不实现真分页排版引擎、可配置 tap-zone 编辑器、完整手势系统（T08 停止条件）。
