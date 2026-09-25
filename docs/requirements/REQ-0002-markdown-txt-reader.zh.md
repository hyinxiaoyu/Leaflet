# REQ-0002 — 文档解析与基础阅读

[English](REQ-0002-markdown-txt-reader.md) | 中文

Status: IN_PROGRESS
Priority: P0
Milestone: M0（T03 样本 → T04 存储基础 → T06 基础 Reader；M0 出口闭环）


来源：`docs/archive/init.md` §9（Renderer 方向）、§10（格式与语法表）、§14（标题规则）、§15（Reader 能力）、§26（MVP：MD/TXT 阅读、标题提取）；`docs/plans/M0-M1-implementation-plan.md` T03/T06；`docs/research/eink-readers.md` §5 静态加载/降级落点。

## 问题

用户资料是 Markdown 与纯文本两种形态混杂：AI 生成的长 Markdown（含表格、代码块、任务列表）与 TXT 笔记并存。阅读器必须两者都可读：Markdown 按 §10 语法表渲染为可读排版，TXT 原样可读；遇到不支持语法（LaTeX、Mermaid、复杂 HTML、脚注等）时整篇仍可读；超大 AI 文档打开可取消、不卡死、不崩溃。

## 用户故事

- 作为 AI 资料阅读用户，我希望打开 `.md/.markdown` 文件时能看到标题、列表、引用、代码、表格等基础排版，以便阅读长篇技术文档，而不只是看到源码。
- 作为 TXT 用户，我希望 `.txt` 文件按原样显示（保留换行并正确处理 BOM），以便纯文本笔记不乱码、不丢格式。
- 作为 E-Ink 用户，我希望不支持的语法降级为可读文本，超大文件明确说明降级而不是卡死，以便每个文件都有确定的打开结果。

## 范围

包含：

- 文件打开：经 `DocumentSource` 按 Document ID 开只读流（不拼文件路径），后台可取消解析，加载/失败/降级三态。
- 格式：`.md`、`.markdown`、`.txt`（大小写不敏感）。
- Markdown 基础语法（init §10 必做表）：H1–H6、段落、粗体、斜体、删除线、无序/有序列表、任务列表、引用、代码块、行内代码、链接、图片占位（真实图片加载见 REQ-0009 范围，此处仅语法级可读）、分隔线、表格（基础可用，交互增强见 REQ-0007 类）。
- 后置语法（LaTeX、Mermaid、复杂 HTML、脚注、数学公式、PlantUML）：graceful degradation——以纯文本/代码块形式原样展示，不使整篇失败。
- 标题规则：YAML frontmatter `title` → 首个 H1 → 去扩展名文件名；frontmatter 只支持限定子集，异常元数据不得使正文失败。
- TXT：UTF-8 + BOM 去除；未知编码有明确提示或降级，不虚报自动识别。
- 分块渲染架构：解析产物为 `ParsedDocument{title, toc, blocks, positionMap}`，逐块渲染（RecyclerView 方向），禁止把整篇无条件装入单 TextView；单个巨大块需有界策略（分段纯文本降级或等效），阈值由 T03/T13 实测确定。
- 与后续定位兼容：块模型自带位置映射，供 REQ-0003（位置）、REQ-0006（TOC）、REQ-0008 类搜索使用。

## 非目标

明确不包含（计划 §1 不做项适用）：编辑器（含任何改写原文能力）、AI Chat/RAG/向量库、PDF/EPUB/CBZ、OCR、手写/复杂 annotation、双链知识库、插件系统、WebDAV/Git/Syncthing 客户端、账号与云同步、完整标签系统、在线商店；以及：LaTeX/Mermaid 完整渲染、表格 CARD/WRAP 模式（仅 NORMAL，见排版类 REQ）、语法高亮强制着色（灰度可辨即可）、全库 FTS、大屏双栏、复杂动画。

## 用户体验

1. 打开文档：显示静态“正在打开…”（文字 + 计件/百分比，无旋转动画）→ 内容逐块出现；可通过返回/关闭取消解析，取消后释放资源、无残留半页。
2. 失败态：文件消失/失权/损坏 → 明确错误页（原因 + “返回” + “重试”），不白屏、不 toast 一闪而过。
3. 降级态：未知语法块显示原文（代码块样式）+ 可选小字标注“该语法暂以原文显示”；超大单块降级显示纯文本分段 + 顶部说明条“本文档部分内容以纯文本显示”。
4. 标题：浏览列表与 Reader 标题栏显示同一标题（规则见 FR-4）。
5. 空文件：显示“空文件”占位而非空白 crash。
6. 快速切换文档（连点两篇）：旧解析结果不得覆盖新文档；取消旧任务并释放其资源。

## 功能需求

- FR-1（打开与取消）：经 `DocumentSource` 按 ID 开只读流，后台协程解析；关闭/切换文档时取消旧任务并关闭流；旧任务的迟到结果不得覆盖当前文档。（映射 T06 验收“可取消后台解析、旧结果不覆盖”）
- FR-2（语法表）：§10 必做语法在常规样本上渲染可读：H1–H6 层级可辨、段落分段、粗/斜/删除线、两类列表、任务列表（勾选态只读展示）、引用、代码块、行内代码、链接可点击、图片至少占位、分隔线、表格行列可读。（映射 T06 验收前半）
- FR-3（优雅降级）：LaTeX/Mermaid/复杂 HTML/脚注/数学公式等未知语法 → 以原文纯文本块展示；任何单块解析异常被捕获为降级占位块，整篇其余块正常。（映射“未知语法降级”）
- FR-4（标题）：`title = frontmatter.title（限定子集，string 标量）→ 首个 H1 文本 → 去扩展名文件名`；frontmatter 缺失/非法/非标量时跳过不报错，正文不受影响。（映射 T06 验收“标题提取、异常元数据不使正文失败”）
- FR-5（TXT/BOM）：TXT 按 UTF-8 解码并去除 BOM；保留换行与空行；空文件显示占位。（映射 T06 “UTF-8/BOM 策略明确”）
- FR-6（未知编码）：非 UTF-8 且无法解码时 → 显示“编码无法识别，已按替换字符显示部分内容”降级页或占位，不得虚报“已自动识别为 XX 编码”，不得 crash。（映射 T06“未知编码有明确提示或降级”）
- FR-7（分块与大块有界）：整篇不得无条件装入单 TextView；`ParsedDocument.blocks` 为渲染与定位的唯一来源；单个巨大块（超长单段/单代码块）触发有界策略：分段纯文本降级或等效分页加载，保证可取消、无 OOM/ANR/crash。（映射 T06 技术门槛）
- FR-8（三态契约）：Reader 对每篇文档暴露 `Loading/Content(degradedBlocks标注)/Error(reason)` 三态；加载态为静态展示（见 E-Ink 节）。
- FR-9（多语言）：中日英混排与 emoji 不乱码、不丢字；malformed Markdown（如未闭合代码围栏/表格错列）不使整篇失败。

## 验收标准

- AC-1：给定覆盖 §10 全语法的样本（含 H1–H6、粗斜删、两类列表、任务列表、引用、代码块、行内代码、链接、图片、分隔线、基础表格）→ 逐项视觉可辨（测试以渲染块类型断言 + 人工走查表），无整篇失败。
- AC-2：给定含 LaTeX 块、Mermaid 块、复杂 HTML、脚注的样本 → 整篇正常打开，未知块以原文代码块样式展示，其余块不受影响。
- AC-3：给定 frontmatter `title`、仅 H1、无 frontmatter 无 H1 三类文件 → 标题分别 = frontmatter 值、H1 文本、去扩展名文件名；给定非法 frontmatter（未闭合/非标量 title）→ 正文正常显示，标题回退到下一级。
- AC-4：给定带 BOM 的 TXT、空文件、中日英 emoji 混排 TXT → BOM 不可见、空文件占位、多语言无乱码。
- AC-5：给定损坏编码文件 → 出现明确编码提示/降级，无 crash，提示文案不含具体编码名称断言。
- AC-6：给定 10MB 样本（含“多小块”与“单个巨大块”两种，来自 T03）→ 打开可取消（取消后 1s 内停止、无 OOM/ANR/crash）；巨大单块触发降级说明条；全程允许“有说明的纯文本降级”，禁止静默截断与假装读取完成。
- AC-7：快速切换 A→B 文档（A 解析未完成）→ 最终显示 B，A 的迟到结果被丢弃且其流已关闭。
- AC-8：无“支持 XXX”空话：每项 AC 均有样本 + 断言/走查记录。

## 边界情况

- 空文件、仅 frontmatter 无正文、仅一行超长文本（无换行 1MB 单行）。
- 未闭合代码围栏、表格分隔行缺失、嵌套列表深度 ≥ 6、链接目标为空。
- 图片引用缺失/损坏：本 REQ 仅要求占位不 crash，完整图片策略见 relatives 类 REQ。
- 文件在解析中途被外部删除/失权：进入 Error(reason) 态，可重试。
- 相同标题多 H1：标题取首个 H1；TOC 重复处理见 REQ-0006。

## E-Ink 考量

- 加载态：静态文字 + 百分比/计件细进度条，节流更新；禁止无限旋转 loading、shimmer、crossfade。
- 代码块默认 soft-wrap ON（灰度可辨，不依赖彩色高亮）；表格 NORMAL 模式横向滚动见排版类 REQ，本 REQ 保证行列可达。
- 尽量避免半透明、阴影、gradient、blur 与高频 UI 更新。

## Standard Android 考量

- 允许轻微 ripple 与正常滚动；图片/链接交互与 E-Ink 共用同一块模型，仅视觉强度可经 DisplayProfile 调整；不为 Standard 引入专属重型渲染路径。

## 性能考量

- 解析在后台线程；整篇单 TextView 禁止；大块有界策略阈值待 T03/T13 实测（100KB/1MB/5MB/10MB）后回填。
- 后台任务切换/取消必须释放流与中间 AST；内存峰值记录模板见 T03。

## 存储与数据影响

- 不缓存正文到数据库；解析产物为内存态，可按文档变化失效的内存/磁盘缓存（策略见 REQ-0003）；数据库仅存标题/TOC 摘要（如需加速列表展示，由 T06 实现时决定并记录）。
- 原文只读：解析过程不写回、不复制为内部文档。

## 测试计划

- 样本：T03 `testdata/markdown/` 全覆盖 §10 + 空文件、大 TXT、重复标题、BOM、损坏编码、超长单段/单代码块、嵌套列表、中日英 emoji。
- JVM/单测：标题规则矩阵、frontmatter 异常矩阵、降级块类型断言、旧结果覆盖防护。
- Instrumented：10MB 两类样本打开/取消走查；快速切换文档测试。
- 人工走查表记录每项语法“可读”结论；性能数字只记录实测，不编造。

## 依赖

- T03 样本与 fixture；T04 `DocumentSource` 只读流契约；REQ-0001 的 Document 元数据。
- 被依赖：REQ-0003（位置映射）、REQ-0006（TOC 同源）、搜索类 REQ（可见文本搜索）。

## 待确认问题

- Markdown 解析器选型（Markwon 及插件链版本、维护状态）待 T00 定案后回填；本 REQ 不锁定具体库版本。
  → T06 回填：Markwon 4.6.2（Apache-2.0），`core + ext-strikethrough + ext-tables + ext-tasklist + recycler (+recycler-table` 演进依赖，插件不混用 `TableEntryPlugin`)；传递 `com.atlassian.commonmark` 0.13.0 接受不覆盖；直接调用收敛于 `MarkdownRenderer` 单适配器（ADR-0004）。无图片网络库（T10 平台解码）。
- 单巨大块分段阈值（行数/字符数）待 T03/T13 实测，未验证前实现方自定初值并记录。
  → T06 初值：`BlockLimits.MAX_BLOCK_CHARS = 32KiB` 字符（`core/markdown/.../TextPipeline.kt`），超限块拆分为有界纯文本条目 + “部分内容以纯文本显示”横幅；会话另有 16MiB 整文件读取上限（`ReaderSession.MAX_READ_BYTES`，覆盖 10MB 样本层）。阈值待 T03/T13 实测修订，不视为推翻 ADR-0006。
- frontmatter 限定子集的具体键白名单（除 `title` 外是否支持 `date/tags` 只读展示）待 T06 定案。
  → T06 定案：仅 `title`（string 标量，可选单/双引号）；`date/tags` 等一律忽略；未闭合 fence / 非标量 title 回退到下一级标题规则且正文不受影响。
