# Leaflet 架构总览

[English](overview.md) | 中文

## 1. 模块划分（6 模块，从计划 §3 起步）

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `:app` | Compose Shell、导航、ViewModel、manual DI、flavor 配置；按 library / reader / settings 分包 | 其余全部核心模块 |
| `:core:model` | Library、Document、ReadingPosition、轻量 ID / 值类型 | 仅 Kotlin 基础库；不依赖 Android UI |
| `:core:storage` | SAF 访问、扫描、Room、DataStore、Repository | `core:model` |
| `:core:markdown` | 原生解析适配（Markwon）、标题、TOC、渲染内容及位置映射 | `core:model`、经审计的 Markdown 依赖（Markwon 4.6.2 core/tables/tasklist/recycler） |
| `:core:reader` | View / RecyclerView 阅读组件、布局、滚动、搜索定位、交互 | `core:model`、`core:markdown`、`core:eink` |
| `:core:eink` | DisplayProfile、MotionPolicy、EInkController、通用策略 | Android 基础 API；不依赖 app / storage |

说明：

- 设计系统先放 `:app` 中，通过参数向 Reader 提供样式（计划 §3）。
- 数据库与 storage 先同模块、分包管理；暂不创建 `feature:*` 空模块或大型 DI 框架（`docs/archive/init.md` §28）。
- 依赖方向严格单向：`app → {reader, markdown, storage, eink, model}`；`reader → {markdown, eink, model}`；`storage → model`；`markdown → model`；`eink → （仅 Android 基础 API）`。`Reader` 不直接访问 Room、SAF 或厂商 SDK（ADR-0002、ADR-0005）。

## 2. 分层与数据流

```text
UI → ViewModel → Repository → DataSource
```

- UI 层（`:app` + `:core:reader` 视图组件）：被动渲染，无业务逻辑；阅读策略只从 DisplayProfile / MotionPolicy / AppCapabilities 读取，不散落 `if (isEink)`。
- ViewModel 层（`:app`）：持有 `StateFlow` 界面状态，编排协程任务（加载、扫描、搜索），处理取消与过期结果丢弃。
- Repository 层（`:core:storage`）：`LibraryRepository` / 文档元数据访问，封装 Room + SAF 细节，对外暴露 Flow / suspend 函数。
- DataSource 层（`:core:storage` 内部）：SAF `DocumentSource`、Room DAO、DataStore；Markdown 适配可使用解析器 AST，但不发明覆盖全部 Markdown 的自研 AST / 解析器（计划 §3）。

状态与异步：`StateFlow`（界面状态）＋ Kotlin Coroutines（后台解析、扫描、搜索均为可取消任务）。数据库用 Room，设置用 DataStore；二者分工见 §4。

DI：MVP 采用 manual DI（AppContainer 风格）；引入 Hilt / Koin 须另立 ADR 解释收益（`docs/archive/init.md` §29）。

## 3. 最小接口契约（计划 §3 全部 8 条，T01 冻结）

以下为跨模块交接契约，字段名冻结；破坏性变化须先暂停依赖任务（计划 §3 §61）。

### 契约 1 — 文档 ID（DocumentId）

- 文档 ID 为应用内部稳定标识（`core:model` 的轻量值类型，如 `DocumentId(value: String)`，底层为 UUID / 自增主键的字符串化，**不等于** URI）。
- `uri: String`（content URI）与 `relativePath: String` 是访问与显示信息；**不假设重命名/移动后 URI 一定稳定**。重命名后视为"同一 Library 下元数据变化的文档"，位置恢复走 anchor/progress 回退（见契约 5）。

### 契约 2 — LibraryRepository

```kotlin
interface LibraryRepository {
    suspend fun addLibrary(displayName: String, rootTreeUri: String): Library
    fun observeLibraries(): Flow<List<Library>>
    suspend fun removeLibrary(libraryId: LibraryId) // 只清本地引用与辅助数据，不删原文
    suspend fun refreshLibrary(libraryId: LibraryId): ScanJobId // 手动刷新，返回可观测的扫描任务
    fun observeScanState(libraryId: LibraryId): Flow<ScanState> // IDLE / SCANNING(静态节流进度) / SUCCEEDED / FAILED / CANCELLED
}
```

### 契约 3 — DocumentSource（Reader 侧唯一文档边界）

```kotlin
interface DocumentSource {
    suspend fun openStream(documentId: DocumentId): InputStream // 只读；调用方负责关闭
    suspend fun resolveRelative(
        fromDocumentId: DocumentId,
        relativeRef: String
    ): ResolvedResource? // 库内相对 MD/TXT/图片；允许树内 `..`，越授权树返回 null；未知 scheme 返回 null
}
```

### 契约 4 — Reader 输入与状态

```kotlin
data class ReaderInput(val documentId: DocumentId, val displayProfile: DisplayProfileId)

sealed interface ReaderState {
    data object Loading : ReaderState // 静态文字/百分比/细进度条，无限 loading 动画禁止
    data class Content(val document: ParsedDocument, val position: ReadingPosition) : ReaderState
    data class Degraded(val document: ParsedDocument, val notice: DegradedNotice) : ReaderState // 超大单块纯文本降级等，有说明横幅
    data class Failed(val reason: ReaderFailure) : ReaderState // 失权/文件消失/解析失败，可恢复
}
```

App 把可取消的加载任务交给 Reader；后台任务快速切换时旧结果不得覆盖新文档。

### 契约 5 — 版本化 ReadingPosition

```kotlin
data class ReadingPosition(
    val documentId: DocumentId,
    val version: Int = 1, // 契约版本；升级语义须向前兼容读取
    val blockIndex: Int = 0,
    val blockOffset: Int = 0,
    val progress: Float = 0f, // 0..1，可见进度
    val anchor: String? = null // 标题 anchor 或可引用文本片段，用于块序号失效时回退
)
```

不只依赖像素值；恢复优先级：同版本块/偏移 → anchor → progress。

### 契约 6 — ParsedDocument

```kotlin
data class ParsedDocument(
    val documentId: DocumentId,
    val title: String, // frontmatter title → 首个 H1 → 文件名（无扩展名）
    val toc: List<TocEntry>, // 层级 + 重复标题去重 anchor
    val blocks: List<RenderBlock>, // 可渲染块；Reader 导航禁止假设整文件对应一个 TextView
    val positionMap: PositionMap, // 块 ↔ 可见文本 ↔ 源码偏移的定位映射（搜索/跳转用）
    val degraded: Boolean = false, // 巨块降级 / 编码损坏
    val degradedNotice: String? = null, // 顶部静态提示文案
    val blockTexts: List<String> = emptyList(), // 每块可见文本（搜索坐标基准）
    val positionOps: PositionMapOps? = null, // 定位行为（锚点/字面量查询）
    val literalText: Boolean = false // R2 DEF-001：TXT 全文为字面文本，条目不得再作 Markdown 解析
)
```

`RenderBlock` 携带 `index/kind/text/source`（精确切片）/`language`/`links`/`images`；R2 DEF-001 追加
`literal: Boolean` —— 置位表示该块是原始文本（TXT 分块或 `PLAIN_FALLBACK` 降级块），条目必须按字面显示，
不得把 `#`/`**`/`[label](target)` 再解析为语法。

### 契约 7 — DisplayProfile / MotionPolicy / AppCapabilities

```kotlin
data class DisplayProfile(
    val animationsEnabled: Boolean,
    val smoothScrollingEnabled: Boolean,
    val rippleEnabled: Boolean,
    val highContrastEnabled: Boolean,
    val defaultBackground: Int,
    val defaultForeground: Int,
    val scrollBehavior: ScrollBehavior, // CONTINUOUS_SCROLL / PAGE_TURN
    val pageTurnBehavior: PageTurnBehavior, // viewport 翻页参数，overlap 默认 5%（允许 3%–8%）
    val refreshPolicy: RefreshPolicy,
    val readerDecorations: ReaderDecorations
)

data class MotionPolicy(val animationsAllowed: Boolean, /* 全局动效总开关语义 */)
data class AppCapabilities(
    val supportsStandardMode: Boolean,
    val supportsAnimations: Boolean,
    val supportsDynamicColor: Boolean,
    val supportsOnyxIntegration: Boolean,
    val supportsAdvancedSharing: Boolean
)
```

DisplayProfile / MotionPolicy 控制动效、滚动、对比度与刷新策略；AppCapabilities 控制功能可用性（由 flavor + 运行时环境决定）；二者不混用，不散落 Boolean。

### 契约 8 — EInkController

```kotlin
interface EInkController {
    fun onReaderEnter(view: View?)
    fun onReaderExit(view: View?)
    fun onPageTurn(view: View?)
    fun onScrollStart(view: View?)
    fun onScrollEnd(view: View?)
    fun requestPartialRefresh(view: View?)
    fun requestFullRefresh(view: View?) // MVP 真实语义：向系统请求一次全量重绘，非硬件 GC
}
```

MVP 仅实现确有用途的 `NoOpEInkController` / `GenericEInkController`，不创建假的 Onyx 实现（ADR-0005、ADR-0007）。

## 4. Room / DataStore 分工

- Room：Library 表、Document 表（含外键）、版本化 ReadingPosition、最近阅读； भविष्य Room FTS 预留（不建 FTS 表，只保证 schema 可扩展，`docs/archive/init.md` §25）。
- DataStore：用户设置（显示模式、字号/行距/页边距、字体选择）、UI 状态（上次 Library、菜单可见性等）。
- 腐蚀数据回退空默认，不得使启动失败（research eink-readers §1 可借鉴条）。

## 5. 架构守护规则（给实现 agent）

1. `:core:reader` 禁止 `import` storage 实现（Room DAO、SAF、ContentResolver 直调）与任何 Onyx 包。
2. `:core:markdown` 禁止引用 `:core:reader` / `:core:storage`；E-Ink 策略经参数注入，不进渲染库。
3. flavor 目录禁止出现业务实现复制；差异只经 DisplayProfile / MotionPolicy / AppCapabilities / EInkController 表达（ADR-0003）。
4. 任何动效代码必须可经 DisplayProfile / MotionPolicy 全局关闭；Reader 内禁止品牌/机型特判（能力检测代替机型白名单）。

## Open Questions

- minSdk 29 与 Room 2.8.5 / activity-compose 1.13.0 下限的最终复核（T02 以 lint/sync 为准）。
- 性能阈值数字（T03 建样本后才有测量依据，不编造）。
