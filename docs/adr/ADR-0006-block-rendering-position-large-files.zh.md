# ADR-0006 — 块渲染、阅读位置与大文件

[English](ADR-0006-block-rendering-position-large-files.md) | 中文

## Context

Reader 是最高优先级模块（`docs/archive/init.md` §15），同时面临三个相互牵连的要求：大文件稳定（10 MB 不 OOM/ANR/crash，`docs/archive/init.md` §51）、阅读位置长期稳定（不只依赖像素 scrollY，`docs/archive/init.md` §15）、文档内搜索与 TOC 跳转需要渲染位置映射（T08/T11）。`docs/research/eink-readers.md` §1（Pilcrow）证实了"块级 RecyclerView + 块级 ScrollAnchor + 后台解析可取消 + 降级占位"路线的可行性，以及 `scrollToPositionWithOffset` 尾部 clamp 等滚动几何坑。

## Decision

1. 块级 RecyclerView：解析 → 分块 → RecyclerView 管线，每 block 一个条目回收渲染；Reader 导航（TOC 跳转、搜索定位、位置恢复）一律基于块寻址，不得假设一整个文件对应一个 TextView（实施计划 §3 契约）。
2. 版本化位置恢复：`ReadingPosition` 包含 `documentId + version + blockIndex + blockOffset + progress + anchor` 等可选字段（`docs/archive/init.md` §15；实施计划 §3）。恢复优先级：同版本块/偏移 → 内容变化后校验并回退至 anchor/progress；字体/宽度变化后同样校验回退；不假设块序号跨编辑稳定。缓存按文档变化失效。MVP 可先实现 scroll position + progress，但字段与回退语义从 Day 1 预留（`docs/archive/init.md` §15）。
3. 超大单块纯文本降级：单个巨大块（巨大段落/代码块/表格）不得无条件装入单 TextView 条目；超过阈值时拆分为纯文本条目并显示有说明的降级横幅（未知语法 graceful degradation 同理，`docs/archive/init.md` §10）。禁止静默截断、假装读取完成。
4. 10 MB 可取消有界：大文档加载/解析在后台可取消协程中进行，快速切换文档时旧结果不得覆盖新文档；取消后释放资源；上限 10 MB 样本必须可取消、无 OOM/ANR/crash，允许有说明的纯文本降级（实施计划 §2 大文档解释、T06 验收）。不以拒绝所有大文件代替阅读路径。
5. 位置映射契约：Markdown 按可见文本搜索，不把语法标记当正文；搜索使用解析/渲染位置映射，禁止把源码 offset 当屏幕坐标；搜索可取消，过期结果不覆盖（T11 实现约束）。

## Alternatives Considered

- 整篇单 TextView + 像素 scrollY 位置：实现最简单（mdreader-android WebView 路线同类），但大文件主线程 measure/layout 代价、旋转/字号一变位置即漂移，与 E-Ink 策略冲突。否决（research 对照组结论）。
- 真分页排版引擎：KOReader 式精确分页体验最好，但复杂度远超 MVP；T08 明确不做，以 viewport 翻页（overlap 默认 5%）代替。延期至 M2 以后按需重议。
- 正则后处理做搜索/定位映射：LaTeX 等内容会被标记污染（mdreader KaTeX 教训：必须在解析期保护，不能后处理正则）。否决，采用解析/渲染期位置映射。

## Consequences

- `:core:reader` 与 `:core:markdown` 的接口必须包含 `ParsedDocument`（标题、TOC、可渲染内容、定位映射）与版本化 `ReadingPosition`（见 `docs/architecture/reader.md`）。
- T03 必须提供"多小块"与"单个巨大块"两类样本及 100 KB/1 MB/5 MB/10 MB 生成脚本；阈值数字待实测，不编造。
- 阅读位置节流保存 + 退出补存（T07）；删除/重命名/失权的最近项有明确提示；Reader 页面重建不对无变化内容重复全文解析。

## Follow-up

- 分块阈值、GC 节奏、性能基线待 T03/T13 实测后回填；本 ADR 的"有界"数字屆时以实测为准，修改阈值不视为推翻本 ADR。
- 全库 FTS 明确延期（M4），但 Room schema 与 Repository 层须允许以后添加 Room FTS（`docs/archive/init.md` §25）；搜索本次只做当前已加载文档内字面量搜索（T11）。
