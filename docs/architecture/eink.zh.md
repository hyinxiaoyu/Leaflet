# E-Ink 架构

[English](eink.md) | 中文

## 1. 四件套与职责

| 概念 | 性质 | 职责 |
| --- | --- | --- |
| DisplayProfile | 运行模式策略对象 | 动效、滚动、对比度、刷新策略、reader decorations；STANDARD / EINK 各一实现 |
| MotionPolicy | 全局动效总开关语义 | 任何动画代码必须可经 DisplayProfile 或 MotionPolicy 关闭 |
| AppCapabilities | 功能可用性 | 由 build flavor ＋ 运行时环境决定；控制功能显示/禁用/隐藏；与策略对象不混用 |
| EInkController | 厂商无关意图接口 | `onReaderEnter / onReaderExit / onPageTurn / onScrollStart / onScrollEnd / requestPartialRefresh / requestFullRefresh`；MVP 仅 NoOp / Generic |

- UI 禁止散落 `if (isEink)` 与设备品牌判断；能力分支只允许按 capability，不做机型白名单（research onyx-epd §2.5）。
- `:core:eink` 仅依赖 Android 基础 API；Reader 经接口调用；Onyx SDK 永不进入 Reader 依赖（ADR-0005）。

## 2. 两套 Profile 行为对照表

| 行为 | StandardDisplayProfile | EinkDisplayProfile |
| --- | --- | --- |
| animationsEnabled | true（允许轻微 transition） | **false** |
| smoothScrollingEnabled | true | **false**（翻页一律 instant scroll） |
| overscroll 动效 | 系统默认 | **OFF** |
| ripple | 轻微允许 | **OFF 或极弱** |
| crossfade / 透明 / 阴影 / gradient / blur / animated elevation / shimmer | 允许克制使用 | **OFF**；纯色背景＋高对比文字，尽量避免半透明 |
| loading 形态 | 允许常规 indicator | **静态文字＋百分比/细进度条**；无限 loading 动画禁止 |
| scrollBehavior 默认 | CONTINUOUS_SCROLL | PAGE_TURN |
| pageTurnBehavior | viewport 翻页（同算法） | viewport 翻页（同算法，overlap 默认 5%） |
| refreshPolicy | 系统默认 | 少刷、整页刷、偶发全量（见 §4 GC 节奏模拟） |
| readerDecorations | 常规 | 最大化阅读区，菜单点按呼出/自动隐；忌粗进度条＋高频时钟 |
| UI 更新频率 | 常规 | 节流更新；不高频刷新（百分比为主，忌每秒重绘） |

M0/M1 两 flavor 均保留基础模式切换（eink 默认 EINK，full 默认 STANDARD）；切换原则上不重启 App。

## 3. 翻页：viewport overlap 5%

```text
pageHeight = viewportHeight（实际可见高度，计入 Insets 与菜单遮挡）
next:     scrollBy(viewportHeight - overlap)
previous: scrollBy(-(viewportHeight - overlap))
overlap 默认 5%，REQ 允许 3%–8%（DataStore 可调，超出范围钳制）
```

- 首尾边界 clamp；旋转后重算；菜单遮挡计入可见高度；连续快速翻页不越界。
- E-Ink 禁止 smoothScroll；横向表格/代码手势、链接/按钮/复制、长按（不翻页）优先于翻页区域仲裁。
- 不实现真分页排版引擎（T08 停止条件）；模式切换保持当前位置（经 PositionMap + ReadingPosition 回退）。

## 4. GC 节奏模拟声明（非硬件控制）

- `GenericEInkController` 可维护一个"局刷意图计数器"：每 N 次 `onPageTurn` / `requestPartialRefresh` 意图后，发一次 `requestFullRefresh` 意图（N 默认保守值，待 T13 实机矩阵校准）。
- **声明：这只是"系统刷新节奏"（请求一次全量性质的系统重绘），不是硬件波形控制。** 不得宣称触发了 REGAL / GU 局刷或 GC 全刷；设置中不提供 UpdateOption 五档、GC 间隔、Fast Mode 等无法兑现的硬件开关（ADR-0005 §4、onyx-epd §5 禁止项）。
- Fast Mode 类能力 MVP 不做；进出配对、退出后正常刷新等语义留待 ADR-0007 集成时兑现。
- 残影/闪烁/翻页结论只能来自 E-Ink 实机矩阵（6"/7"/10.3" 类别）；缺设备时标 NOT_RUN / BLOCKED，不标通过。

## 5. MVP 实现清单（仅 NoOp / Generic）

- `NoOpEInkController`：全部意图方法空实现（Standard 侧或测试用）。
- `GenericEInkController`：意图方法记账＋按 DisplayProfile 触发系统行为（instant scroll、静态刷新请求、GC 节奏计数）；无任何 Onyx `import`。
- T12 接线（`profileFor` / `MotionPolicy.from` / `controllerForMode` / `capabilitiesForFlavor` 四缝合）：
  - `LeafletTheme` 经 `profileFor(modeId)` + `MotionPolicy.from` 解析策略；EINK 下 `LocalIndication provides null`（ripple OFF）+ 透明 glow 的 `OverscrollConfiguration`（overscroll 动效 OFF），STANDARD 原样保留平台默认；导航无过渡（instant swaps）。
  - `MainActivity` 单点收集 `SettingsStore.displayMode` 并灌入 Theme 全树；设置切换即重组生效（热切，不重启），DataStore 恢复后保持选择（REQ-0004 FR-3 / REQ-0010 FR-2）。
  - `ReaderContent` 经 `controllerForMode` 取控制器；`DisposableEffect(eink, docId)` 配对 `onReaderEnter/onReaderExit`，`turnPage` 内 `onScrollStart → scroll → onScrollEnd → onPageTurn` 包夹；全部 instant（`scrollToItem` / `scroll { scrollBy }`），无 `smoothScroll` 家族调用。
  - About 含版本/flavor/临时 ID/TBD 许可/第三方许可入口（文字链接，不复制全文）+ 系统刷新语义声明；设置/About 无 UpdateOption/GC 间隔/Fast Mode 硬件档位。
- T12 验收：E-Ink 下动画、smooth scroll、overscroll、ripple、crossfade 均符合关闭策略；双 flavor 可热切模式、恢复后保持选择；Reader 无品牌判断散落。

## 6. 阅读排版与外层控件策略

`DisplayProfile.readingLayout`（`ReadingLayoutPolicy`，`core/eink/.../ReadingLayoutPolicy.kt`）是 REQ-0004 FR-1 原有十个字段之后新增的第十一个 Profile 字段，承载排版与页面布局背后的数值，使共享 Reader 只有一条布局路径，各 Profile 只是数值不同。`ReadingLayoutResolver`（`:core:reader`）把用户持久化的档位与该策略转换为 sp/dp 数值；Reader 与设置页预览都调用它。Standard 解析结果与原有数值完全一致（由 `ReadingLayoutTest` 锁定）。

| 设置项 | Standard | E-Ink |
| --- | --- | --- |
| 正文字号 | 16 sp × 档位缩放（0.85–1.45） | 18 sp × 档位缩放 |
| 行高，档位 0/1/2 | 1.375 / 1.719 / 2.063 × 字号，叠加在字体自然高度上（沿用旧行为） | 1.45 / 1.60 / 1.80 × 字号，精确基线间距 |
| 页边距，档位 0/1/2 | 8 / 16 / 24 dp | 16 / 28 / 44 dp |
| 行宽（measure） | 不限制 | 36 em，居中；仅作用于流式正文（代码、表格、图片保持全宽） |
| 两端对齐 | 左对齐（右侧参差） | Markdown 段落按词间距两端对齐；字面 TXT / 降级块保持原样、右侧参差 |
| 断字 | 平台默认 | 正常频率并使用高质量断行 |
| 块间距 | 4 dp | 6 dp |
| 外层小字 | Material 3 字阶 | 至少 14 sp、Medium 字重；bodyMedium 16 sp、bodyLarge 18 sp |
| 正文 / 链接颜色 | 主题 `onSurface` | 主题 `onSurface`（纯黑）；链接沿用正文颜色，靠下划线区分 |
| 分隔线 / 卡片边框（`outlineVariant`） | `#D0D8CE` | `#666666`（原 `#999999`） |
| 页脚进度文字 | 实时 | 每秒至多一次可见更新（首个立即显示，突发变化合并为一次尾部更新） |
| 确认对话框 | 平台遮罩与窗口淡入淡出 | 无遮罩、无窗口动画、纯白容器、2 dp 描边 |

翻页重叠量刻意保持不变（3–8%，默认 5%）：REQ-0005 约束了范围，R3-05 验收测试也锁定了该值。默认值下在 6 英寸屏上翻页后大约保留一行文字；够不够属于真机矩阵问题。页面边缘出现被截断的文字行在没有分页引擎的前提下无法避免，而分页引擎不在 MVP 范围内。

未改动、仍待确认：黄色的“其他匹配项”搜索高亮在灰度屏上近乎白色（当前匹配项是黑白反色，不受影响），搜索栏 Material `TextField` 聚焦时光标闪烁。两者都需要先做设备检查再选定方案。

所有数值均为基于原理的默认值。面板对比度、残影、刷新表现和实际可读性在真机矩阵运行之前均未验证。

## Open Questions

- GC 节奏 N 的默认值：`GenericEInkController` 默认 12（`fullRefreshEveryNthIntent = 12`），待 T13 实机矩阵校准；此前任何残影结论标 NOT_RUN / BLOCKED。
- §6 中 E-Ink 的阅读排版数值（18 sp 基准、1.45–1.80 行高、16–44 dp 页边距、36 em 行宽、两端对齐）均为未实测的默认值；T13 须在 6、7、10.3 英寸面板上确认或重新调校。
- BOOX Android 版本基线（待真机矩阵；minSdk 29 ＋ target 35 组合已在 research 论证，T02 构建验证通过）。
