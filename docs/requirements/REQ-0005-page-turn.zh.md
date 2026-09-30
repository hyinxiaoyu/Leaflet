# REQ-0005 — Viewport 翻页、手势仲裁与阅读模式

[English](REQ-0005-page-turn.md) | 中文

Status: READY
Priority: P0
Milestone: M1（依赖 T07，经 T08 交付；导航与交互仲裁）


来源：`docs/archive/init.md` §16（Viewport 翻页与三区）、§17（双模式）、§26（MVP：翻页/连续滚动/三区/中央菜单）；`docs/plans/M0-M1-implementation-plan.md` T08；`docs/research/eink-readers.md` §5（KOReader overlap、Pilcrow clamp 教训）。

## 问题

E-Ink 屏不适合平滑滚动长滚：拖动过程多次刷新导致闪烁与残影，且手指滚动难以停在“刚好接上”的位置。需要 Viewport 翻页：每次按可见高度减去小幅 overlap 即时跳动，保证上下文连续；同时保留连续滚动给 Standard/需要的用户；点击区与内容手势（链接、横滚表格/代码）冲突必须有明确仲裁，否则误触频发。

## 用户故事

- 作为 E-Ink 读者，我希望点击右侧翻到下一屏、点击左侧翻到上一屏，并让相邻屏幕有少量重叠，以便逐屏阅读长文档且上下文连续，不因拖动而闪烁。
- 作为手机用户，我希望连续滚动自然顺滑，以便高效阅读短文档和浏览内容。
- 作为读者，我希望点击链接、横滑表格或长按时不会误翻页，以便内容交互行为可预测。

## 范围

包含：

- Viewport 翻页算法：`scrollBy ±(viewportHeight − overlap)`，`overlap` 默认 5%，需求允许范围 3%–8%（可设，未验证，默认 5% 写死或设置项见 FR）。
- 左/中/右三区：左 = 上一页、右 = 下一页、中 = 显示/隐藏阅读菜单；以后允许用户调整区域（本 REQ 只预留，不做 zone 编辑器）。
- 双模式：`CONTINUOUS_SCROLL` / `PAGE_TURN` 均可用；E-Ink 默认 PAGE_TURN，Standard 默认 CONTINUOUS_SCROLL；模式切换保持当前位置。
- 交互仲裁：链接/按钮/代码复制/横向表格·代码手势优先于翻页区；长按永不触发翻页。
- 边界处理：首/尾 clamp、Insets/菜单遮挡计入可见高、旋转重算、连续快速翻页不越界；E-Ink 用 instant scroll，禁止 smoothScroll。
- 进度指示：静态细进度（百分比数字为主），节流更新。

## 非目标

明确不包含（计划 §1 不做项适用）：编辑器、账号与云同步、AI/RAG、PDF/EPUB、完整标注、插件、全库 FTS、厂商 SDK、大屏双栏、复杂动画；以及：真正分页排版引擎（不做版式分页，只做 viewport 即时滚动）、可配置 tap-zone 编辑器/全手势系统（只做左/中/右三区）、捏放缩放（排版类 REQ 范畴）、自动滚屏。

## 用户体验

1. PAGE_TURN 模式：点右区 → 即时跳到下一屏（含 5% 重叠行）；点左区 → 上一屏；首屏再点左区无动作（或轻微静态提示“已到顶部”），尾屏同理。
2. 中央区点按 → 阅读菜单显示/隐藏；菜单遮挡计入翻页可见高，菜单展开时翻页仍落位准确。
3. CONTINUOUS 模式：手指滑动自然滚动（Standard 允许 smooth 特性，E-Ink 下仍遵循 MotionPolicy 禁 smooth，见 REQ-0004）。
4. 链接/按钮点按 → 跳转/动作，不翻页；宽表格/代码横滑 → 横向滚动，不翻页；长按 → 选择/菜单（后续）或无动作，绝不翻页。
5. 模式切换入口在阅读菜单/设置；切换后停在同一阅读块（位置语义见 REQ-0003）。

## 功能需求

- FR-1（翻页算法）：`next: scrollBy(viewportHeight − overlap)`，`prev: scrollBy(−(viewportHeight − overlap))`；`overlap = 5% × viewportHeight` 默认，允许范围 3%–8%（实现以常量或有限设置项承载，值必须可验证）。（映射 §16、T08）
- FR-2（可见高）：`viewportHeight` 取 Reader 实际可见高度，扣除系统 Insets 与菜单/进度条遮挡；旋转、菜单显隐、窗口变化后重算。（映射 T08“处理 Insets、菜单遮挡、旋转”）
- FR-3（三区）：左/中/右三个点击区，左=上一页、右=下一页、中=菜单显隐；区域划分默认三等分（左右区宽度实现记录，未验证），以后可调。（映射 §16 三区）
- FR-4（双模式）：`CONTINUOUS_SCROLL` 与 `PAGE_TURN` 均可切换；默认值 EINK→PAGE_TURN、STANDARD→CONTINUOUS_SCROLL；切换保持 `ReadingPosition` 同一块/锚点。（映射 §17、T08“模式切换保持当前位置”）
- FR-5（仲裁优先级）：链接/按钮/代码复制按钮/横向可滚容器（表格/代码）的触摸优先于翻页区；横向滚动手势不触发翻页；长按（≥ 长按阈值）永不触发翻页。（映射 T08“交互优先级”）
- FR-6（E-Ink 滚动）：EINK 预设下翻页与恢复滚动一律 instant（`scrollToPositionWithOffset` 类语义），禁止 `smoothScroll` 家族调用。（映射 §16 禁止项、T08）
- FR-7（边界）：首屏上翻/尾屏下翻 clamp，不得越界、不得空白抖动；连续快速多次翻页最终位置钳制在文档首尾内；尾部定位考虑 clamp 特性（如 Pilcrow spacer/headroom 教训，实现自定等效手段并记录）。（映射 T08“首尾边界、连续快速翻页不越界”）
- FR-8（进度）：阅读进度以静态百分比数字 + 细进度条展示，更新节流（与位置节流同源或 ≥ 1s），不高频重绘。（映射 KOReader 低干扰教训）

## 验收标准

- AC-1：PAGE_TURN 下测量：连续 5 次下翻，每次位移 = `viewportHeight − overlap`（误差 ≤ 2px），重叠行在相邻两屏均可见；`overlap` 实测比值 ∈ [3%, 8%] 且默认 5%（±0.5%）。
- AC-2：首屏点左区 → 位置不变、无越界空白；尾屏点右区 → 停在尾部、无越界；连续快速点按 10 次下翻 → 最终停在尾部，不越界、不 crash。
- AC-3：链接/按钮/代码复制按钮点按 → 触发自身动作且不翻页；宽表格上横滑 → 表格横滚、页面不翻；长按任一区 ≥ 500ms → 不翻页。
- AC-4：旋转屏幕后翻页位移按新可见高计算，首尾仍 clamp；菜单展开时下翻落位误差 ≤ overlap。
- AC-5：PAGE_TURN ↔ CONTINUOUS 切换 → 同一块/锚点（`blockIndex` 不变），无跳回顶部。
- AC-6：E-Ink 模式下无 `smoothScroll` 调用（代码审查 + 走查无平滑位移动画）。
- AC-7：无“支持 XXX”空话：位移、比值、次数、误差均有测量记录。

## 边界情况

- 单屏可装下全文：翻页键无动作，进度 100%。
- 菜单/进度条遮挡一半视口：可见高按实际扣除计算，不跳过遮挡内容。
- 旋转中途翻页：以最新布局值为准，旧手势不按旧高度结算。
- 超大块降级区（纯文本分段）：翻页按同样 viewport 语义工作，不假设块高等。
- TalkBack/键盘翻页（如有）：同样走 FR-1 位移，不另设步长（无障碍细节未验证，见 Open Questions）。

## E-Ink 考量

- instant 跳动 + 静态进度 + 节流更新；翻页时调用 `EInkController.onPageTurn`；无厂商 SDK 时为 NoOp/系统默认，不声称局刷。
- 禁 smooth/overscroll/ripple/crossfade（REQ-0004 清单同样适用本 REQ 走查）。

## Standard Android 考量

- CONTINUOUS 默认；允许正常滚动物理特性，但同样经 MotionPolicy/DisplayProfile 取值；三区在 Standard 同样有效（中央菜单一致）。

## 性能考量

- 翻页为 O(1) 滚动指令，不触发重解析；快速连翻与布局重算互斥（防竞态）；`viewportHeight` 缓存 + 失效重算。

## 存储与数据影响

- 持久化：当前阅读模式（`CONTINUOUS/PAGE_TURN`）与 `overlap`（如可设）存 DataStore；位置本身见 REQ-0003。
- 无 Room 变更。

## 测试计划

- JVM/单测：位移公式、overlap 比值边界、clamp 逻辑、仲裁优先级决策表。
- Instrumented：三区点击、横滑不翻、长按不翻、旋转重算、模式切换保位置、快速连翻越界。
- 走查：E-Ink 无 smooth、静态进度节流。

## 依赖

- REQ-0003（位置语义与节流）、REQ-0004（滚动/刷新策略）、REQ-0002（块模型可见高）、REQ-0006（TOC 跳转落位复用同管线）。
- 被依赖：T12 模式集成验收。

## R3 证据（2026-09-28）

R3-05 验收套件在真实合成视口上分别用 3% 与 8% 重叠比例测量翻页距离，并驱动连续五次翻页、首尾钳制、中央菜单、长按、链接点击以及表格/代码横向滚动。它发现并修复了 DEF-R3-05-01（点击处理器捕获了过期的 `overlap`，导致改动重叠比例后要等到切换模式或旋转才生效）。见 [r3-05-delivery.zh.md](../plans/r3-05-delivery.zh.md)。真机残影/刷新仍未验证。

## UX-06 证据（2026-09-30）

阅读器外层控件（标题、各类提示、搜索栏、阅读菜单、进度页脚）已从正文的流式 `Column` 移入
覆盖在满高列表之上的浮层（FR-2 由此成为真实计算：实测的顶部/底部外层控件高度通过
`PageTurn.visibleHeightPx` 扣除，而此前该值恒为零）。进度页脚现在是贴底的静态条（FR-8），
文档标题仅在阅读菜单打开时显示，一次性的不透明触控区引导用随模式变化的副标题说明 FR-3 的
三个区域；其关闭状态会被持久化，判断逻辑是纯函数 `TapZones.shouldShowHint`。位置语义、
FR-1 距离公式与 FR-6 无平滑滚动规则均未改动。见 [ux-06-delivery.md](../plans/ux-06-delivery.md)；
插桩验收测试可以编译，但没有设备时处于 NOT_RUN，E-Ink 硬件仍为 NOT_RUN。

## 待确认问题

- 左右区默认宽度（各 30%? 35%?）未验证，KOReader 网格只作参照；实现初值必须记录并走查误触率。
  - T08 记录：初值三等分（左右区各 1/3，`TapZones`），误触率走查留 T12/T13。
- `overlap` 是否做用户可调（3–8% 滑杆）还是常量 5%：未验证，默认常量 5%，可调性留 M2。
  - T08 记录：几何与 DataStore 键已就绪（`PageTurn.coerceOverlap` 3–8%、`SettingsStore.pageOverlap` 默认 5%、`PageTurnBehavior` 同源），用户可调滑杆 UI 本身留 M2（当前仅代码/测试层面可设）。
- 无障碍（TalkBack 焦点/键盘 Space 翻页）行为未验证，不在本 REQ 冻结。
