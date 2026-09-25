# Markdown 渲染方案调研（T00）

[English](markdown-renderers.md) | 中文

- 日期: 2026-09-17
- 范围: 仅 `docs/research/markdown-renderers.md`
- 约束: 只读调研，不复制任何参考项目代码，只记录 URL / 访问日期 / tag / commit / 许可证 / 可借鉴设计 / 不采用原因。版本结论来自 Maven Central / 官方文档 / 仓库源码快照，无法确认标“未验证”。

## 1. 来源与版本快照（访问日期均为 2026-09-17）

|#|来源 URL|版本 / tag / commit|许可证（仓库 LICENSE 原文核对）|
|---|---|---|---|
|S1|https://github.com/noties/Markwon|最新 release tag `v4.6.2`（releases feed 顶部仍为 v4.6.2）；Maven Central `io.noties.markwon:core:4.6.2` Last Updated 2021-02-08；master 可见最新提交 2021-03-15 `sample, add copy code block`|Apache License 2.0（`/LICENSE` 全文核对）|
|S2|https://github.com/noties/Markwon/blob/master/build.gradle（根）|`commonMarkVersion = '0.13.0'`；`config min-sdk = 16`；传递依赖 `com.atlassian.commonmark:commonmark`、`commonmark-ext-gfm-strikethrough`、`commonmark-ext-gfm-tables` 均为 0.13.0|—（构建事实）|
|S3|https://github.com/noties/Markwon/blob/master/CHANGELOG.md|v4.6.2 新增 `image` 模块 `DefaultDownScalingMediaDecoder`（#329）；v4.6.0 tasklist 改 GFM 实现（block 解析改节点后处理，#291），tables 新增 `TableAwareMovementMethod`（#289）；v4.2.0 注明 commonmark-java 0.13.0 对应 CommonMark spec 0.29|—|
|S4|https://noties.github.io/Markwon/docs/v4/install.html|官方 artifact 清单：core / editor / latex / strikethrough / tables / tasklist / html / image / image-coil / image-glide / image-picasso / inline-parser / linkify / recycler / recycler-table / simple-ext / syntax-highlight；全 artifact 同版本号|—|
|S5|https://noties.io/Markwon/docs/v4/ext-tables/|表格默认限制原文：占满 TextView 全宽、所有列等宽、表格内容不可复制到剪贴板；含链接必须用 `TableAwareMovementMethod`；完整体验走 `recycler-table`（`TableLayout`，可包 `HorizontalScrollView`）|—|
|S6|https://noties.io/Markwon/docs/v4/ext-tasklist/|`TaskListPlugin.create(context \|颜色 \|Drawable)`；状态切换仅视觉，持久化自理（官方给出 ClickableSpan 切换范例）|—|
|S7|https://noties.io/Markwon/docs/v4/recycler/|`MarkwonAdapter`（按 block 分条目，`SimpleEntry` 带解析缓存，可为 `FencedCodeBlock` 定制 `HorizontalScrollView` 条目）|—|
|S8|https://noties.io/Markwon/docs/v4/recycler-table/|`TableEntry` + `TableEntryPlugin` 渲染 `TableBlock` 为原生 `TableLayout`；警告：用此方案时**不**与 `TablePlugin` 混用|—|
|S9|https://noties.io/Markwon/docs/v4/image/|`ImagesPlugin`：SchemeHandler（File / DataUri / Network / OkHttp / 自定义）+ MediaDecoder（GIF / SVG / Default）；`FileSchemeHandler.createWithAssets` 约定 `file:///android_asset/`|—|
|S10|https://noties.io/Markwon/docs/v4/core/configuration.html|`LinkResolver`（默认 `LinkResolverDef`：起 Activity，无处理者则静默忽略，可自定义拦截库内链接）；`ImageDestinationProcessorRelativeToAbsolute` / `ImageDestinationProcessorAssets`（只处理无 scheme 的 URL）；`ImageSizeResolverDef`（%/em/px，自动缩到 canvas 宽，height 不支持 %）|—|
|S11|https://github.com/markedjs/marked（`package.json`、`LICENSE`）|master 快照 `version: 18.0.13`；官方文档警告：marked **默认不 sanitize 输出 HTML**，须配 DOMPurify 等|MIT|
|S12|https://github.com/mikepenz/multiplatform-markdown-renderer（releases feed + `gradle/libs.versions.toml`）|最新 `v0.45.0`（2026-08-28）；解析器 `org.jetbrains:markdown:0.7.9`；M2/M3 双模块，`rememberMarkdownState` 异步解析，GFM 表格/tasklist/alerts 开箱|Apache-2.0（LICENSE 另含 Erik Hellman 部分的 MIT 声明）|
|S13|https://github.com/jeziellago/compose-markdown（`markdowntext/build.gradle`、releases feed）|最新 `0.7.2`（2026-04-27）；其 `build.gradle` 直接依赖 `io.noties.markwon:{core,ext-strikethrough,ext-tables,html,linkify,ext-tasklist}:4.6.2` + 一个 Markwon fork（`com.github.jeziellago:Markwon:58aa5aba6a`）+ Coil3；minSdk 21；JitPack 分发|MIT|
|S14|https://github.com/commonmark/commonmark-java（`README.md`、`LICENSE.txt`）|core 最新 `0.30.0`（坐标已迁至 `org.commonmark`；旧 `com.atlassian.commonmark` 止于 0.17.x）；Android min API 19 且为 best-effort；Parser/Renderer 可多线程复用|BSD 2-clause 风格（Robin Stocker，`LICENSE.txt` 原文）|

## 2. 方案详述

### 方案 A — Markwon（原生 Spannable，无 WebView）✅ 首选

- **What**：Android 原生 Markdown 库。commonmark-java 解析 → 渲染为系统 Spannable，可直接用于 TextView / Button / Toast。插件化：`core` + `ext-tables`（`TablePlugin`）+ `ext-tasklist`（`TaskListPlugin`）+ `recycler`（`MarkwonAdapter`）+ `recycler-table`（`TableEntry`）+ `image`（`ImagesPlugin`，按需只用 SchemeHandler / MediaDecoder 扩展点，不引入 glide / picasso / coil 模块）。
- **架构**：`Markwon.parse(String) -> Node` / `render(Node) -> Spanned` / `setMarkdown(TextView)` 三段式，可在后台线程 parse + render，主线程只做 `setParsedMarkdown`。自有分块层按顶层 block 切分后喂给 `MarkwonAdapter`，每 block 一个 RecyclerView 条目；宽表与代码块用独立 `HorizontalScrollView` 条目；超大单块拆分为纯文本条目降级。
- **对照验收维度的依据（非空话）**：
  - 原生渲染：是，Spannable，无 WebView（S1 README 原文）。
  - RecyclerView/分块：官方 `recycler` artifact 支持按 block 分条目 + `SimpleEntry` 缓存（S7）。
  - 大文件行为：默认单 TextView 整篇渲染，超大单块存在主线程 measure/layout 代价 → 本方案用自有分块 + 超大块纯文本降级 + 可取消后台解析应对；10 MB 不承诺完整富文本（与计划 T06 技术门槛一致）。
  - 表格：默认 TextView 实现有官方书面限制（全宽 / 等宽列 / 不可复制 / 链接需 `TableAwareMovementMethod`）（S5）；M1 以 NORMAL 模式 + 横向滚动容器保证窄屏可达全部列，`TableEntry` / `TableEntryPlugin` 为演进路径（S8，不与 `TablePlugin` 混用）。
  - 任务列表：`TaskListPlugin` GFM 实现，勾选切换仅视觉、持久化自理（S6）—— Leaflet 是只读阅读器，不持久化勾选，恰好匹配。
  - 相对图片/文档链接：`LinkResolver` 自定义拦截库内 `.md/.txt` + fragment（默认实现处理不了就静默忽略，必须覆盖）（S10）；相对图片经 `ImageDestinationProcessor` + 自定义 `SchemeHandler` 映射到 SAF content URI，拒绝越授权树；GIF/SVG 解码器 MVP 不引入。
  - E-Ink 适用性：高。无 WebView、无动画依赖；主题色默认取 TextView textColor，灰度/高对比主题接线直接；翻页/滚动由自有 Reader + MotionPolicy 控制。
  - 维护状态（如实）：**自 4.6.2（2021-02-08）后无新 release；master 可见头停留在 2021-03；属于事实停滞。**是否被 GitHub 标记 archived：未验证（访问日快照未确认横幅）。缓解见 §5 R1。
  - 许可证：Apache-2.0，可商用，记入 THIRD_PARTY。
  - APK 体积：未验证（无 AAR/APK 实测；只用 core/tables/tasklist/recycler(+recycler-table) 最小面，不含图片加载网络库；实测留给 T13）。
- **可借鉴**：`MarkwonAdapter` 分块思想与 `SimpleEntry` 缓存；`TableEntry` 的 TableLayout 映射；`ImageDestinationProcessor` 相对转绝对思路（改为相对转 SAF）；`TableAwareMovementMethod`（默认表格内的链接点击处理）。
- **不采用的部分**：不引入 `image-glide` / `image-coil` / `image-picasso` / okhttp 渲染侧依赖；`TablePlugin` 与 `TableEntryPlugin` 二选一，不混用。

### 方案 B — WebView + marked.js ❌ 不采用

- **What**：本地 `assets` HTML 模板 + marked UMD 包，`evaluateJavascript` 注入 Markdown，输出 HTML 渲染。marked 最新 18.0.13，MIT（S11）。
- **架构**：JS 解析 → 整页 DOM。表格/tasklist 靠 HTML/CSS 全功能呈现；但相对图片与 SAF content URI 需 JS bridge / 自定义 scheme，相对文档链接拦截依赖 `shouldOverrideUrlLoading`，搜索定位需源码 offset 对 DOM 映射。
- **不采用原因**（逐条可执行）：
  1. E-Ink 不适用：WebView 独立合成与滚动/动画语义不受 `EInkController` / `MotionPolicy` 细粒度控制，残影与刷新策略无法兑现；内存/进程代价高于 TextView 方案（定性判断，实测值未验证，但方向明确）。
  2. 大文件整页 DOM，与 Reader 按块定位/搜索映射（REQ-0003/0008）冲突。
  3. 安全面：marked 默认不 sanitize，须自带 DOMPurify（官方警告），本地只读场景下是无谓负担。
  4. MVP 不新增网络能力，WebView 引入整套浏览器面（JS、远程资源策略）与安全边界相悖。
- **可借鉴**：无实现借鉴；仅当原生渲染路径被证伪时的回退候选（只读纯文本降级优先于 WebView）。

### 方案 C — Compose Markdown 库（mikepenz 为主，jeziellago 为辅）❌ 不采用

- **What / 架构**：mikepenz `multiplatform-markdown-renderer` v0.45.0（Apache-2.0）：JetBrains Markdown AST → 全量 Composable（`MarkdownComponents` 逐节点可覆盖），`rememberMarkdownState` 异步解析，图片经 Coil2/Coil3 transformer（S12）。jeziellago `compose-markdown` 0.7.2（MIT）：本质是 **TextView 渲染再包一层 `MarkdownText` 给 Compose 用**，且直接依赖 Markwon 4.6.2 全套 + 私有 Markwon fork + Coil3，JitPack 分发（S13）。
- **不采用原因**：
  1. Leaflet `:core:reader` 已定为 View/RecyclerView 阅读组件；为 Markdown 渲染引入 Compose 运行时是倒置依赖，需重写 Reader，增大的体积与复杂度未经证实有收益（体积差未验证）。
  2. jeziellago 是“Markwon 套壳 + Compose 包装 + 私有 fork”，对 Leaflet 等于多一层间接依赖与供应链风险，无任何 Markwon 直引没有的东西。
  3. mikepenz 图片默认走 Coil 网络链，与 MVP 不联网原则冲突（可定制，但属额外工作）；其重组刷新语义与 E-Ink 刷新策略的接线复杂度高于 TextView/RecyclerView（未验证实测，故不选）。
- **可借鉴**：`MarkdownComponents` 逐节点可覆盖的定制思想（映射为 Markwon 的 `configureSpansFactory` / 自定义 Entry）；后台解析状态 hoisting 思路；Coil transformer 可插拔设计（MVP 不用网络图片，仅记录）。

### 方案 D — 自研 parser ❌ 不采用

- **What**：手写 CommonMark + GFM（表格/tasklist/删除线）解析与 Span 渲染，或直接基于 `org.commonmark:commonmark:0.30.0`（BSD，S14）自建渲染层。
- **架构**：若做则为 解析 → 自有 AST → Span / Composable。commonmark-java 本身提供 `includeSourceSpans` 源码位置、`TextContentRenderer` 纯文本渲染、Parser/Renderer 多线程复用（S14）。
- **不采用原因**：成本 = spec 合规测试集 + 表格/tasklist/HTML 块语义 + 大文件性能调优，回报 = 与 Markwon 已有能力重叠；计划 T00/T06 已明确“不自研 parser”“不先发明覆盖全部 Markdown 的自研 AST”。唯一允许的是**分块层**复用解析器 AST 做块切分，且前提是 Markwon 暴露的 `parse/render(Node)` 扩展点不够用 —— 该前提是否成立：未验证，留给 T06 spike。
- **可借鉴**：commonmark-java 的 `includeSourceSpans` 源码位置思想可用于搜索/定位映射（若直接依赖 commonmark 做分块）；`TextContentRenderer` 可做纯文本降级与搜索可见文本抽取的备选。

## 3. 四方案对比表

|维度|A Markwon 原生|B WebView + marked|C Compose 库|D 自研 parser|
|---|---|---|---|---|
|原生渲染|是（Spannable，无 WebView）|否（整页 HTML）|mikepenz：Compose 非 View；jeziellago：套壳 TextView|是，但从零造|
|RecyclerView/分块|官方 `recycler` 按 block 分条目 + 缓存|无，整页 DOM|无等价物（仍整 AST 重组，且引入 Compose 运行时）|需自建|
|大文件行为|单块过大需自有拆分 + 纯文本降级（本报告首选含此设计）|整页 DOM，内存/排版集中|整 AST 重组，大文件策略自理|全部自理|
|表格|默认等宽列/全宽/不可复制（官方书面限制）；演进 `TableEntry`|HTML table 全功能 + CSS|mikepenz GFM 开箱；jeziellago 经 Markwon|自实现|
|任务列表|GFM，只读够用（切换仅视觉）|需 CSS/JS|开箱|自实现|
|相对图片/文档链接|`LinkResolver` + 自定义 `SchemeHandler` 接 SAF（本报告设计）|需 JS bridge + URL 拦截|需自定义 transformer/拦截器|全自研|
|E-Ink 适用性|高（无动画依赖，主题直接）|低（刷新/滚动不可控）|中低（重组刷新语义 + 新运行时）|高，但成本否决|
|维护状态|**停滞**：4.6.2/2021-02-08 后无 release，master 头 2021-03|marked 活跃（18.0.13）但容器（WebView）是问题|mikepenz 活跃（v0.45.0/2026-08）；jeziellago 0.7.2/2026-04 但套壳 Markwon|无维护负担，有开发负担|
|许可证|Apache-2.0|MIT（+需自带 DOMPurify 类 sanitizer 许可）|Apache-2.0 / MIT|commonmark-java BSD（若复用）|
|APK 体积影响|未验证（最小 artifact 面，无网络图片库；实测 T13）|WebView 无 APK 增量但运行时内存/进程代价|Compose 运行时 + Coil 链，增量最大（未验证具体 KB）|最小，但人力成本最高|

## 4. 首选方案与固定版本组合

**首选：Markwon `core + ext-tables + ext-tasklist` + 自有分块 / RecyclerView 适配，不自研 parser，不引入 WebView / Compose / 网络图片库。**

```kotlin
// 固定版本（T02 前冻结，全局版本目录由集成任务统一管理）
val markwonVersion = "4.6.2"
implementation("io.noties.markwon:core:$markwonVersion")
implementation("io.noties.markwon:ext-tables:$markwonVersion")
implementation("io.noties.markwon:ext-tasklist:$markwonVersion")
implementation("io.noties.markwon:recycler:$markwonVersion")
implementation("io.noties.markwon:recycler-table:$markwonVersion") // 表格演进用，不与 TablePlugin 混用
// 传递（来自 Markwon POM，不手动升级）：com.atlassian.commonmark:{commonmark,commonmark-ext-gfm-tables,commonmark-ext-gfm-strikethrough}:0.13.0
// 不引入：image-glide / image-coil / image-picasso / okhttp（渲染侧）/ WebView / Compose Markdown
```

- AGP / Kotlin / minSdk / targetSdk 组合：未验证（待 T02 工具链冻结，Markwon 自身 minSdk 16 不代表 Leaflet minSdk）。
- `:core:markdown` 对 Markwon 的直接调用必须收敛在一个 `MarkdownRenderer` 适配器后，为 R1 替换留单一接缝。
- 渲染链：SAF / ContentUri 文档流 → 后台可取消解析（Markwon parse → commonmark-java 0.13.0 AST）→ 自有分块器（按顶层 block 切分，超大单块拆纯文本降级条目）→ RecyclerView（`MarkwonAdapter`：普通块 TextView 条目 / FencedCodeBlock 与 TableBlock 独立横向滚动条目）→ 自定义 LinkResolver + SchemeHandler / ImageDestinationProcessor 接库内相对资源，越授权树拒绝。E-Ink 策略经 DisplayProfile / MotionPolicy / EInkController 注入，不进渲染库。

## 5. 风险清单

|ID|风险|影响|缓解（映射任务）|
|---|---|---|---|
|R1|Markwon 维护停滞（2021 后无 release）|新 AGP / targetSdk 编译与运行时兼容自担|最小 artifact 面 + `MarkdownRenderer` 适配器隔离；T02 双 flavor 编译即验证|
|R2|commonmark 传递版本旧（0.13.0 / spec 0.29）|缺新版 spec 修复；是否可安全覆盖到 `org.commonmark` 新版：未验证|接受旧版，不强制覆盖（与 Markwon 内部 API 耦合）；输入为本地用户文档，XSS 面远小于 WebView；记入 THIRD_PARTY|
|R3|超长单块（单个巨大段落/代码块/表格）仍是单 TextView 条目|主线程 measure/layout 抖动，极端 ANR/OOM|自有分块阈值拆分 + 纯文本降级横幅 + 可取消解析；T03 样本（多小块 + 单个巨大块）验证，不编造阈值|
|R4|表格默认实现限制（等宽列/全宽/不可复制/链接需 TableAwareMovementMethod）|窄屏宽表体验|M1：横向滚动容器 + NORMAL 模式保证可达全部列（T09）；TableEntryPlugin 为演进路径|
|R5|相对资源需自研桥接（content URI 不可当路径拼接，`..` 不得越授权树）|图片/跨文档链接失效或越权|自定义 SchemeHandler + LinkResolver + 授权树校验；T10 用例覆盖空格/中文/`../`/缺失目标|
|R6|后人重提 WebView/Compose|方向反复|本报告 §2B/§2C 不采用原因即决策记录，ADR-0004 引用|

## 6. 未验证项（不得当作结论使用）

1. APK/AAR 体积实测值（待 T13）。
2. 大文件分块阈值与帧率/内存数字（待 T03 样本 + T13 基线）。
3. Markwon 4.6.2 在新 AGP/Kotlin/targetSdk 下的编译兼容（待 T02）。
4. 目标 BOOX Android 版本基线（待真机矩阵）。
5. Markwon 仓库是否被标记 archived 的精确状态；open issue 精确数。
6. 直接依赖 `org.commonmark` 新版做分块/覆盖传递依赖的可行性（待 T06 spike）。
7. mikepenz/Compose 在 E-Ink 上的实测残影与刷新表现（不选，故不测）。
