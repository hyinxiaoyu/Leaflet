# ADR-0005 — E-Ink 控制器抽象

[English](ADR-0005-eink-controller-abstraction.md) | 中文

## Context

Leaflet 把 E-Ink 当作一等平台而非主题（`docs/archive/init.md` §21、§66 原则四）。UI 中不允许散落 `if (isEink)` 分支，而应通过统一策略对象控制（`docs/archive/init.md` §3）：`DisplayProfile`（动效、滚动、对比度、刷新策略、reader decorations）、`MotionPolicy`（动画总开关语义）、`AppCapabilities`（功能可用性，由 flavor + 运行时环境决定，与策略对象不混用，`docs/archive/init.md` §59）。

厂商刷新 API（Onyx `EpdController` / `EpdDeviceManager` / UpdateMode，`docs/archive/init.md` §19）能力强但绑定私有 SDK、分发与许可证均有未验证项（`docs/research/onyx-epd.md` §3–§4）。MVP 阶段的 E-Ink 体验完全可用框架手段实现（onyx-epd §5 理由 3），因此需要一个与厂商无关的意图接口，把"想做什么"与"硬件怎么做"解耦。

## Decision

1. 建立厂商无关接口 `EInkController`（`docs/archive/init.md` §18），只暴露意图方法，不暴露任何厂商类型（如 Onyx `UpdateMode`）：
   - 进入/退出阅读器：`onReaderEnter / onReaderExit`
   - 翻页/滚动：`onPageTurn / onScrollStart / onScrollEnd`
   - 刷新请求：`requestPartialRefresh / requestFullRefresh`
2. MVP 仅实现 `NoOpEInkController` 与 `GenericEInkController`（纯框架手段：禁用动画/平滑滚动/overscroll/ripple/crossfade、viewport 翻页、静态节流加载态、GC 节奏模拟，见 `docs/architecture/eink.md`）。不创建假的 `OnyxEInkController`（实施计划 §3 契约第 6 条）。
3. Reader 永不直引 SDK：`:core:reader` 只依赖 `EInkController` 接口；核心 Reader 不直接引用 Onyx SDK（`docs/archive/init.md` §18；AGENTS.md）。未来真实集成只进 `eink` flavor 独立 sourceSet，`full` 零引用。
4. 禁止硬件承诺：Generic/NoOp 不得宣称能触发硬件局刷（REGAL/GU）或全刷（GC）；设置中不展示无法兑现的硬件档位；`requestFullRefresh` 在 MVP 的真实语义是"向系统请求一次全量重绘"，残影结论只能来自 E-Ink 实机矩阵（onyx-epd §5 禁止项；T12 刷新边界）。
5. 任何动画代码必须可通过 DisplayProfile 或 MotionPolicy 全局关闭（`docs/archive/init.md` §2）；E-Ink 下动画、smooth scroll、overscroll、ripple、crossfade 均符合关闭策略，无无限 loading 动画（T12 验收）。

## Alternatives Considered

- UI 层散落 `isEink` Boolean 分支：最快，但策略不可测试、Standard/E-Ink 行为漂移、flavor 差异泄漏。否决，AGENTS.md 明确禁止散落 Boolean。
- MVP 直接集成 Onyx SDK：见 ADR-0007，M5 前不复议。
- 把刷新模式枚举（REGAL/GC/FAST）直接暴露给 Reader：Reader 与 BOOX 硬件语义耦合，非 BOOX 设备无意义。否决；意图名可以借（翻页≈REGAL 意图、全刷去残影≈GC 意图），能力承诺不可以借（onyx-epd §2.3）。

## Consequences

- `:core:eink` 只依赖 Android 基础 API，不依赖 app/storage（实施计划 §3 所有权表）；Reader 经接口调用，行为可单元测试、可替换。
- T12 显示策略验收以"关闭项确实关闭"为判定标准，而非刷新观感；系统刷新表现留给实机矩阵验证。
- GC 间隔计数器等策略以"系统刷新节奏"名义模拟，必须在文档中明确那不是硬件波形控制（onyx-epd §2.2 可借鉴设计）。

## Follow-up

- 真实 Onyx 集成的前提与验收见 ADR-0007；在七项前提满足前，任何 `OnyxEInkController` 命名的新增代码都视为违反本 ADR。
- DisplayProfile 两套行为对照表见 `docs/architecture/eink.md`；MotionPolicy 与 AppCapabilities 的字段冻结在 T01 契约中（见 `docs/architecture/overview.md`）。
