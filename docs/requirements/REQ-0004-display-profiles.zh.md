# REQ-0004 — 显示策略、Flavor 与 E-Ink 能力边界

[English](REQ-0004-display-profiles.md) | 中文

Status: IN_PROGRESS
Priority: P0
Milestone: M0（T02 最小策略 → T12 全面接线；M1 验收）


来源：`docs/archive/init.md` §2（动画约束）、§3（DisplayProfile）、§4–§6（flavor 与发行策略）、§18（EInkController）、§21–§22（双设计系统）、§26（MVP：双 Profile、模式切换）；`docs/plans/M0-M1-implementation-plan.md` T02/T12；`docs/research/eink-readers.md` §5。

## 问题

Leaflet 有两个正交概念极易混淆：发行版本（`full`/`eink` 两个 APK）与运行模式（STANDARD/EINK 两种显示策略）。若用散落的 `if (isEink)` 控制动画/滚动/刷新，E-Ink 约束必然漏网（某处忘记关 smooth scroll 即造成残影与闪烁），且 eink flavor 会被普通 Android 依赖污染体积。需要集中策略对象 + 能力对象 + 厂商无关刷新接口，三者各司其职。

## 用户故事

- 作为 E-Ink 设备用户，我希望默认进入无动画、无平滑滚动、低干扰的阅读界面，以便翻页清晰且不受残影或闪烁打扰。
- 作为同时使用两类设备的用户，我希望在设置中切换显示模式并即时生效（原则上无需重启），以便同一 App 在手机与墨水屏上都易用。
- 作为 eink flavor 用户，我希望获得更小的安装包和更稳定的功能子集，以减少对存储有限的墨水屏设备的负担。

## 范围

包含：

- `DisplayProfile` 抽象（至少 `StandardDisplayProfile` / `EinkDisplayProfile`）与字段：`animationsEnabled`、`smoothScrollingEnabled`、`rippleEnabled`、`highContrastEnabled`、`defaultBackground/defaultForeground`、`scrollBehavior`、`pageTurnBehavior`、`refreshPolicy`、`readerDecorations`（init §3 字段全集）。
- `MotionPolicy`：全局动效总开关，所有动画代码经其裁决；E-Ink 下默认全关。
- `AppCapabilities`：功能可用性（如是否含某 Standard 专属能力），与显示策略解耦——“能不能用”是 Capabilities，“好不好看/动不动”是 DisplayProfile。
- `EInkController` 厂商无关接口（`onReaderEnter/onReaderExit/onPageTurn/onScrollStart/onScrollEnd/requestPartialRefresh/requestFullRefresh`）：MVP 仅 `NoOp`/`Generic`（系统刷新）实现，不集成 Onyx SDK。
- 双 flavor：`full` 与 `eink` 均可切换运行模式（eink 默认 EINK，full 默认 STANDARD）；临时 applicationId 集中配置；Reader 不复制到 flavor 目录，共享 ≥ 90% 核心代码。
- E-Ink 关闭项清单（强制）：animation、smooth scroll、overscroll 动效、ripple（OFF 或极弱）、crossfade、复杂透明/阴影/gradient/blur、无限 loading 动画、高频 UI 更新。
- 设置 → 显示模式切换 + About 页（REQ-0010 占位，本 REQ 只定策略接线要求）。

## 非目标

明确不包含（计划 §1 不做项适用）：编辑器、账号与云同步、AI/RAG、PDF/EPUB、完整标注、插件、全库 FTS、大屏双栏（暂缓）、复杂动画与 Material motion/shared element（Standard 也不做）；以及：Onyx/BOOX 厂商 SDK 集成（M5 才考虑）、声称硬件局刷/全刷的设置项、普通 Android 专属动画制作、flavor 各自 fork 一套 Reader。

## 用户体验

1. 设置 → “阅读显示模式”：`○ 标准 Android / ● E-Ink` 单选；切换即时生效（原则上不重启），重启后保持选择。
2. E-Ink 模式视觉：纯色背景、高对比文字、无阴影/半透明/渐变；加载态为静态文字 + 百分比/细进度条；菜单呼出/隐藏无过渡动画。
3. Standard 模式视觉：Material 3 简洁风，允许轻微 ripple 与正常滚动；同样无复杂过渡动画（M0/M1 阶段）。
4. 两个 flavor 的设置中都有该开关；eink flavor 首次启动默认选中 E-Ink。
5. 设置中不出现无法兑现的硬件能力项（如“局刷/全刷间隔”“GC/REGAL 波形”）。

## 功能需求

- FR-1（抽象）：`DisplayProfile` 含 init §3 全部 10 个字段；`StandardDisplayProfile` 与 `EinkDisplayProfile` 两套预设；UI 与 Reader 经 Profile/MotionPolicy 取值，禁止在 Screen 中散落 `if (isEink)` 分支做动效决策。（映射 §3、T12“控制策略集中”）
- FR-2（MotionPolicy）：任何动画/过渡/Crossfade/shimmer/无限 loading 的启停必须经 `MotionPolicy` 裁决；EINK 预设下 `animationsEnabled=false` 且无旁路。（映射 §2、T12 验收）
- FR-3（双 flavor 可切换）：`full` 与 `eink` 两个 flavor 均保留基础模式切换能力；默认模式 full=STANDARD、eink=EINK；选择持久化，进程重建/重启后保持。（映射计划 §2 解释、T02/T12 验收）
- FR-4（E-Ink 关闭清单）：EINK 预设下 animation=OFF、smoothScroll=OFF、overscroll 动效=OFF、ripple=OFF 或极弱、crossfade=OFF、复杂透明/阴影=OFF、无限 loading 动画不存在、进度更新节流。（映射 §2 清单、T12 验收）
- FR-5（Capabilities 解耦）：`AppCapabilities` 表达功能可用性（如某 Standard 专属能力在 eink flavor 不可用），不得与 DisplayProfile 混用；flavor 差异经 Capabilities + flavor 源码集表达，不复制 Reader。（映射 T02/T12）
- FR-6（EInkController 边界）：接口含 §18 七个方法；MVP 仅提供 `NoOpEInkController`/`GenericEInkController`（系统能力范围内刷新）；`Generic` 不得声称触发硬件局刷/全刷；Reader 不直接引用 Onyx SDK。（映射 §18–§19、T12“刷新边界”）
- FR-7（生命周期接线）：Reader 进入/退出、翻页、滚动起止调用 `EInkController` 对应方法；无厂商 SDK 时行为退化为 NoOp/系统默认，不得 crash。
- FR-8（包体积纪律）：eink flavor 不引入动画资源、Web/JS runtime、重型编辑器、AI/网络/analytics/广告 SDK；新增大型依赖必须记录理由与体积影响（见 REQ-0011 占位）。

## 验收标准

- AC-1：两 flavor 分别安装启动 → eink 默认为 E-Ink 模式、full 默认为 Standard；设置中均可切换到另一模式并即时生效（不重启），杀进程重启后选择保持。
- AC-2：E-Ink 模式下走查全部阅读路径（打开/翻页/滚动/菜单/加载/错误态）→ 无 smooth scroll、无 overscroll 发光/回弹动画、无 ripple（或极弱）、无 crossfade、无限 loading 动画零出现（检查表逐项打勾）。
- AC-3：代码审查断言：无 Screen/Reader 直引 Onyx SDK；动效启停均经 MotionPolicy/Profile（抽查无裸 `isEink` 动效分支）。
- AC-4：设置页无“局刷/全刷/波形/快速模式”等硬件能力项；`Generic/NoOp` 文档注释明确“不触发硬件刷新”。
- AC-5：`./gradlew :app:assembleEinkDebug :app:assembleFullDebug` 均可构建（命令待 T02 验证后以 README 为准），两 APK 应用身份可区分（名称/applicationId 不同）。
- AC-6：无“支持 XXX”空话：每项均有构建产物/走查表/审查记录为据。

## 边界情况

- 模式切换时正在阅读：切换后留在同一文档同一位置（与 REQ-0005 协同），仅视觉/动效策略变化。
- 升级安装保留模式选择；数据清除后回到 flavor 默认。
- eink flavor 运行 Standard 模式：基础阅读正常，仅策略切换，不解锁 eink 被裁剪的 flavor 功能（Capabilities 仍约束）。

## E-Ink 考量

- 本 REQ 即 E-Ink 核心约束：纯色、高对比、无半透明/阴影/渐变/模糊、无动画 elevation、无 shimmer、无限 loading 禁止、减少 UI 频繁更新（init §21 全集）。
- 进度/百分比更新节流，避免每分钟级高频区域刷新致闪（KOReader 教训，见 research §5）。

## Standard Android 考量

- Standard 同样保持简洁（init §22）：Material 3 + 轻微 ripple + 少量 transition + 正常滚动 + dark theme 可后置 dynamic color；不为 M0/M1 制作页面过渡/shared element/crossfade。

## 性能考量

- 策略对象查值零分配、主线程安全；模式切换不重建解析产物（复用 REQ-0003 缓存）；eink flavor 体积基线见 `docs/performance/apk-size.md`（T02 建立、T13 验收）。

## 存储与数据影响

- DataStore：`displayMode` 选择、`highContrast` 等用户覆盖项；Room 不存策略。
- flavor applicationId 集中配置（`full: io.leaflet.reader` / `eink: io.leaflet.reader.eink` 为建议值，正式发布前确认）。

## 测试计划

- JVM 单测：两套预设字段矩阵（关闭清单逐项断言）、Capabilities 与 Profile 不混用（类型级断言/审查）。
- Instrumented：双 flavor 安装启动、模式切换即时性与持久性、阅读路径动效走查表。
- 实机残影/刷新结论仅来自 E-Ink 实机（T13 矩阵），缺设备标 BLOCKED/NOT_RUN。

## 依赖

- T02（flavor、最小策略、CI）；REQ-0005（翻页行为经 `pageTurnBehavior`）、REQ-0002/0003（加载态与恢复滚动经策略）。
- 被依赖：T12 设置/About 完整化、T13 发布候选。

## 待确认问题

- ripple“极弱”的可验证定义：T12 定案为直接 OFF（`EinkDisplayProfile.rippleEnabled = false` + Theme `LocalIndication provides null`，JVM 单测 `einkPresetDisablesEverythingAnimated` 锁定）；“极弱”变体不再保留。
- Standard 模式未来动画清单（M3）不在本 REQ 展开。
- 正式 applicationId、签名、许可证待项目所有者确认（计划 §2），不阻塞开发。
- JDK/Gradle/AGP/Kotlin/Compose 版本组合：T02 以构建证据冻结（AGP 8.13.2 / Kotlin 2.0.21 / compileSdk 35 / minSdk 29，见 `docs/development/build.md`）；后续升级须 ADR。
