# ADR-0004 — 原生 Markdown 渲染

[English](ADR-0004-native-markdown-rendering.md) | 中文

## Context

`docs/archive/init.md` §9 禁止第一版自研 Markdown parser，要求优先研究 Markwon 原生渲染，并给出"Parser → Block Model / AST → Renderable Blocks → Reader"目标与逐块渲染方向。T00 对比了 Markwon、WebView + marked、Compose Markdown 库、自研 parser 四条路线（`docs/research/markdown-renderers.md` §2–§3），结论为 Markwon 首选。

关键事实（均来自 research 实测，非记忆）：Markwon 最新 release `4.6.2`（2021-02-08，Apache-2.0），Maven Central 实测；传递依赖 `com.atlassian.commonmark:commonmark` 0.13.0（对应 CommonMark spec 0.29）；`recycler` artifact 提供按 block 分条目的 `MarkwonAdapter`（`SimpleEntry` 缓存）；表格默认实现有官方书面限制（全宽/等宽列/不可复制/链接需 `TableAwareMovementMethod`）；任务列表 GFM 实现、勾选切换仅视觉。

## Decision

1. 采用 Markwon `4.6.2`，artifact 面限定为 `core + ext-tables + ext-tasklist + recycler（+recycler-table 演进用）`；版本在全局版本目录冻结。不引入 `image-glide` / `image-coil` / `image-picasso` / okhttp 渲染侧依赖（`docs/research/README.md` §2；T10 用平台解码 + 视口采样）。
2. 自有分块：SAF / ContentUri 文档流 → 后台可取消解析（Markwon parse → commonmark-java 0.13.0 AST）→ 自有分块器按顶层 block 切分 → RecyclerView 逐块渲染（普通块 TextView 条目；FencedCodeBlock 与 TableBlock 独立横向滚动条目）。禁止把整篇文档无条件塞进单个超大 TextView（`docs/archive/init.md` §9；T06 技术门槛）。
3. 单一接缝：`:core:markdown` 对 Markwon 的一切直接调用收敛在一个 `MarkdownRenderer` 适配器之后；E-Ink 策略经 DisplayProfile / MotionPolicy / EInkController 注入，不进渲染库。
4. 接受传递的 commonmark 0.13.0，不手动覆盖到 `org.commonmark` 新版（与 Markwon 内部 API 耦合，覆盖可行性未验证，research R2）。
5. 不自研 parser：唯一允许的是分块层复用解析器 AST 做块切分；不先发明覆盖全部 Markdown 的自研 AST（实施计划 §3）。暂不支持语法（LaTeX / Mermaid / 复杂 HTML / 脚注等，`docs/archive/init.md` §10）必须 graceful degradation，不得使整篇渲染失败。

## Alternatives Considered

- WebView + marked.js：GFM 全功能一次得，但整页 DOM 无分块/回收/取消、位置只是像素 scrollY、E-Ink 刷新语义不可控、marked 默认不 sanitize 输出。不采用（research §2B；`eink-readers.md` §2 为对照组）。
- Compose Markdown 库：mikepenz 版需引入 Compose 运行时倒置 Reader 依赖、图片走 Coil 网络链；jeziellago 版是 Markwon 套壳 + 私有 fork + JitPack，多一层供应链风险。不采用（research §2C）。
- 自研 parser（或基于 commonmark-java 自建渲染层）：成本为 spec 合规 + GFM 语义 + 大文件调优，回报与 Markwon 重叠。否决，仅保留分块层复用 AST 的可能性（research §2D）。

## Consequences

- 风险 R1（Markwon 事实停滞：4.6.2 后无 release，master 头停留 2021-03）：缓解 = 最小 artifact 面 + `MarkdownRenderer` 隔离；T02 双 flavor 编译即验证兼容；必要时以新 ADR 替换解析器而不动 Reader 契约（`docs/research/README.md` §4）。
- 表格 M1 取 NORMAL 模式 + 横向滚动容器保证窄屏可达全部列；`TableEntry` / `TableEntryPlugin` 为演进路径（不与 `TablePlugin` 混用）；WRAP / CARD 只做架构预留，不展示不可用开关（T09 规则）。
- 相对图片经 `ImageDestinationProcessor` + 自定义 `SchemeHandler` 映射到 SAF content URI，拒绝越授权树；GIF/SVG 解码器 MVP 不引入；远程图片默认静态占位、不自动联网（实施计划 §2）。
- License：Markwon Apache-2.0，记入 `THIRD_PARTY_LICENSES`；APK 体积增量实测留给 T13。

## Follow-up

- T06 spike 验证 Markwon 适配在真实样本上的行为（含复用 `parse/render(Node)` 扩展点是否足够支撑分块；不成立则评估直接用 commonmark AST，可行性当前未验证）。
- 大文件阈值、分块粒度、帧率/内存数字待 T03 样本 + T13 基线实测，不编造（research §6 未验证项）。
