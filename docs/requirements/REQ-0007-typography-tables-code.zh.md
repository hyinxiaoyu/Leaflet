# REQ-0007 — 排版、表格与代码块

[English](REQ-0007-typography-tables-code.md) | 中文

Status: READY
Priority: P0
Milestone: M1

## 问题

AI 生成的 Markdown 资料多为长文档、宽表格与长代码行。在 6 寸 E-Ink 小屏上，固定字号与固定版式会导致阅读困难：字太小看不清、字太大翻页频繁；宽表格列被截断无法查看；长代码行被硬截断。用户需要可调且持久化的排版设置，以及窄屏可达全部内容的表格与代码块方案。


来源：`docs/archive/init.md` §15（Reader 必须支持字号/字体/行距/页边距）、§23（表格）、§24（Code Block）、§26（MVP 必做含字号/行距/页边距/字体选择基础能力）；`docs/plans/M0-M1-implementation-plan.md` T09；`docs/research/markdown-renderers.md` §4–5（R3 超长单块、R4 表格限制）。

## 用户故事

作为在 E-Ink 上阅读 AI 资料的用户，我希望调整并保存字号、字体、行距和页边距，为代码块切换 soft-wrap，并横向滚动查看宽表格，以便在小屏上阅读长文档、宽表格和长代码。

## 范围

包含：

- 正文排版四项：字号（font size）、系统字体选择（system font family）、行距（line spacing）、页边距（page margin）。
- 四项设置经 DataStore 持久化，重启与进程重建后恢复。
- 排版变化保持阅读上下文（参见 REQ-0003 位置恢复策略，不要求像素级精确，但不得跳回文档开头）。
- 代码块：等宽字体（monospace）渲染、语言标签（language label）显示、一键复制（copy）按钮、soft-wrap 开关；E-Ink 默认 soft-wrap = ON，用户可关闭；关闭后代码块可横向滚动。
- 表格：仅 `TableRenderMode.NORMAL` 模式，即宽表格置于横向滚动容器内，保证窄屏可访问全部列。
- 深色/灰度主题下正文、代码、表格文字可辨（对比度要求，不引入语法高亮引擎）。

## 非目标

明确不包含：

- 打包字体文件（font bundling）：只使用系统字体（对应实施计划"不默认打包字体文件"）。
- `TableRenderMode.WRAP`（列宽自适应换行）与 `TableRenderMode.CARD`（宽表转纵向字段卡片）：明确后置到 M2+，本 REQ 不实现，且不在 UI 中展示不可用的模式开关。
- 强制语法高亮（syntax highlighting）：不要求；如有极简实现，必须满足 E-Ink grayscale readability，且不得作为默认开启的复杂着色引擎。
- LaTeX / Mermaid / 脚注 / 复杂 HTML（`docs/archive/init.md` §10 可后置项）。
- 可配置 tap-zone 编辑器、完整手势系统（T08 停止条件）。
- 用户自定义字体导入、逐级字重/字距微调。

## 用户体验

- 阅读器菜单或设置页提供排版入口：字号滑杆或阶梯档位、字体选择（系统可用字体列表）、行距档位、页边距档位。
- 调整即时预览，无需重启；调整后阅读位置保持在当前上下文附近。
- 代码块右上角（或等效位置）显示语言标签（如 `kotlin`、`python`，无标注时不显示或显示 `text`）；提供复制按钮，点击后复制代码原文并给出静态提示（E-Ink 下无复杂动画提示）。
- soft-wrap 开关位于代码块菜单或排版设置中；E-Ink 初始默认 ON。
- 宽表格出现横向滚动条或滚动 affordance；表格内横向滑动只滚动表格，不触发翻页（与 REQ-0005 交互仲裁一致）。

## 功能需求

- FR-1：Reader 提供字号调节，至少 5 档可辨变化，当前档位持久化，重启后恢复。
- FR-2：Reader 提供系统字体选择，至少提供 2 个系统字体选项（含默认与等宽以外的正文字体，若系统仅一个可用则如实单选），选择持久化。
- FR-3：Reader 提供行距调节，至少 3 档，选择持久化。
- FR-4：Reader 提供页边距调节，至少 3 档，选择持久化。
- FR-5：代码块使用等宽字体渲染；显示 fence 标注的语言标签；无标注时不得显示错误语言名。
- FR-6：代码块提供复制按钮，点击复制代码块原文（不含 fence 标记）到剪贴板，复制结果可验证。
- FR-7：代码块提供 soft-wrap 开关；E-Ink 模式默认 ON；OFF 时代码块内容可横向滚动且长行完整可达。
- FR-8：表格以 NORMAL 模式渲染：表格整体置于横向滚动容器，窄屏下全部列经横向滚动可达，无列被永久裁剪。
- FR-9：UI 不得出现 WRAP/CARD 模式开关或入口（后置项不可见）。
- FR-10：排版四项的读写经统一设置层（DataStore），Reader 经参数接收样式，不在 Reader 内直读存储。

## 验收标准

- AC-1：修改字号/字体/行距/页边距后杀进程重启，四项均恢复为修改后值。
- AC-2：给定含 H1–H3、段落、列表的样本，逐档切换字号/行距/页边距，正文始终完整渲染，无截断、无 crash。
- AC-3：给定含 `kotlin` 与无标注 fence 的样本，语言标签显示正确，复制按钮产出 fence 内原文（逐字符比对）。
- AC-4：E-Ink 默认模式下新建阅读会话，代码 soft-wrap 为 ON；关闭后，给定超长单行代码样本，横向滚动可到达行尾字符。
- AC-5：给定宽表格样本（列数超出 6 寸视口），横向滚动后最后一列可见且内容正确；表格内横滑不触发翻页。
- AC-6：全局搜索 UI 文案与设置页均无 "WRAP" / "CARD" / "卡片表格" 开关。
- AC-7：灰度/深色主题下，正文、代码、表格边框与文字对比可辨（人工确认 + 截图留档）。

## 边界情况

- 空代码块、无标注 fence、标注为未知语言名：渲染不失败，复制返回空或原文，语言标签不虚报。
- 超长单行代码（单个巨大块）：允许纯文本降级条目 + 说明横幅（与 `markdown-renderers.md` R3 一致），不得 ANR/OOM。
- 表格列数极多或单元格含长文本：横向滚动仍可达；单元格内不要求自动换行（WRAP 后置）。
- 字体列表在不同系统上数量不同：单字体系统不得展示虚假多选项。
- TXT 文档：排版四项同样生效；代码/表格能力仅适用于 Markdown。

## E-Ink 考量

- E-Ink 默认代码 soft-wrap = ON（`docs/archive/init.md` §24）。
- 排版调整过程无 smooth animation、无 crossfade；字号切换为即时重排。
- 表格与代码横向滚动使用 instant scroll，禁用 smoothScroll（与翻页策略一致）。
- 复制成功提示使用静态文字，不使用 ripple/shimmer/旋转 loading。
- 不要求强制语法高亮；任何着色必须保证 grayscale readability。

## Standard Android 考量

- Standard 模式允许轻微 ripple 与少量 transition，但排版调整本身不依赖动画。
- 允许 dark theme；dynamic color 可后置。
- soft-wrap 默认值在 Standard 下允许为 OFF（未验证项，见 Open Questions），但开关必须存在且持久化。

## 性能考量

- 排版变化不得触发全文重新解析：复用已解析 block，仅重排版/重绑定（T09 验收"排版变化保持阅读上下文"）。
- 禁止明显 O(N²) 的文档处理（`docs/archive/init.md` §50）。
- 超大单块走分段/纯文本降级策略，阈值实测值待 T03/T13 确定，本 REQ 不编造数字。

## 存储与数据影响

- 排版四项 + soft-wrap 开关存 DataStore（用户设置范畴，`docs/archive/init.md` 允许数据库保存用户设置）。
- 不存储字体文件；不写原文；卸载/清库行为见 REQ-0011。
- 设置 schema 变更需可迁移，不得以清库掩盖设置丢失（实施计划通用 DoD）。

## 测试计划

- 单测：设置读写持久化（改值→重建→恢复断言）；语言标签解析（标注/无标注/未知标注）。
- 样本：复用 `testdata/markdown/` 的 `tables.md`、`code.md`、`long.md`、`chinese.md`（`docs/archive/init.md` §48）；超长单行代码用 T03 的"单个巨大块"样本。
- UI/instrumented：AC-4、AC-5 的滚动可达性断言；横滑不翻页的仲裁测试（与 REQ-0005 联合）。
- E-Ink 实机：按 `eink-test-matrix.md` 记录字体调整、代码块、表格项；无设备标 NOT_RUN。

## 依赖

- REQ-0003（阅读位置：排版变化后位置保持/回退策略）。
- REQ-0005（翻页与交互仲裁：表格/代码横滑优先于翻页区域）。
- REQ-0006（TOC：重排后 anchor 跳转仍有效）。
- REQ-0010（设置页承载排版入口）。
- T08（前置：导航与翻页仲裁先就绪，T09 依赖 T08）。

## 待确认问题

- 字号/行距/页边距的具体档位数与默认值：T09 已冻结——字号 5 档（0–4，默认 2，scale 0.85+0.15×step）、行距 3 档（0–2，默认 1，multiplier 1.00/1.25/1.50）、页边距 3 档（0–2，默认 1 → 8/16/24dp）。见 `ReaderStyle`。
- Standard 模式 soft-wrap 默认值：T09 行为——未设置时 E-Ink ON，Standard 取存值（`CODE_SOFT_WRAP_DEFAULT=true` 仍为存值缺省；Standard 未显式选择时的展示默认未验证，保留观察）。
- 系统字体枚举方式（按 family 查询 vs 固定候选列表）：T09 已决策——固定候选白名单（`system/sans-serif/serif`，monospace 仅代码块）。family-query 各 OEM 面差异大，未验证；白名单三项全 Android 可解析（`Typeface` 常量），单字体系统不展示虚假多选项（FR-2 如实单选由白名单语义保证）。
- 应用原生单元格网格绕过 Markwon 表格默认限制（`markdown-renderers.md` S5）。UX-07 根据实际带样式内容统一列宽；NORMAL 模式横向可达性仍有测试覆盖。真机可读性与链接点击待 T13 实机样本确认，仍未验证。

## UX-07 验收补充（2026-09-30）

- **FR-11 / AC-8：** 所有行共用根据带样式的单元格内容测量的列宽，包括表头和行内格式。空单元格保留网格位置；同一行的单元格高度统一。网格分隔线为 1 dp，表头下方分隔线为 2 dp。宽表格仍能横向滚动至所有内容，不触发翻页。
- **FR-12 / AC-9：** 代码复制反馈显示在现有操作按钮内，自最近一次点击起保持两秒，再恢复复制操作。反馈不得移动代码正文或后续内容；复制结果仍为去除围栏后的精确正文。
- 验证和截图记录在 [ux-07-delivery.md](../plans/ux-07-delivery.md)。本节补充既有 READY 需求；硬件对比度仍未验证。
