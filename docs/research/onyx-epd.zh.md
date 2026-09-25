# Onyx EPD 研究报告

[English](onyx-epd.md) | 中文

> 调研日期：2026-09-17（UTC）。T00 技术证据，只读调研形成；未复制参考项目源码，所有 API 名称均为事实性接口引用。版本结论均来自官方文档页 / 演示工程源码 / GitHub API / mvnrepository 页面实测；无法确认的项明确标“未验证”。

## 0. 来源标识

- 参考仓库：`onyx-intl/OnyxAndroidDemo` — https://github.com/onyx-intl/OnyxAndroidDemo
- 仓库 LICENSE（Apache-2.0，版权人 ONYX INTERNATIONAL INC）：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/LICENSE
- Git tags（2026-09-17 经 GitHub API 实测）：**无 tag**（`https://api.github.com/repos/onyx-intl/OnyxAndroidDemo/tags` 返回空列表）。因此本报告以 commit 锚定，不以 tag 锚定。
- Commits（GitHub API 实测，2026-09-17）：
  - `ec512c79c0d39c1e294214d36580ce4c914bbea6` — 2026-09-10 — "Update LICENSE owner"
  - `f1a346d8083653b40b9f5e61c633f91511dc23c4` — 2026-09-09 — 合并 "sdk version update"（PR #142）
  - `60f35871a4cc5bcbba74216cc6c1fd92119cbecf` — 2026-09-09 — "Fix INSTALL_FAILED_NO_MATCHING_ABIS by including arm64-v8a ABI"
- 关键官方文档页（均已 raw 实测 2026-09-17）：
  - EPD 刷新总览：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Screen-Update.md
  - EpdController：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EpdController.md
  - EpdDeviceManager：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EpdDeviceManager.md
  - UpdateMode / UpdateOption 全表：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Update-Mode.md
  - Base SDK（含版本与 changelog）：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Onyx-Base-SDK.md
  - E-Ink 开发 UX 指南：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Eink-Develop-Guide.md
  - 前光：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/FrontLightController.md ；触摸区域：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/EPD-Touch.md ；笔：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Onyx-Pen-SDK.md ；存储：https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/DeviceEnvironment.md
- 演示工程源码（raw 实测 2026-09-17，注意实际路径含 `app/OnyxAndroidDemo/` 中缀，README 中的短路径已过时）：
  - `app/OnyxAndroidDemo/src/main/java/com/android/onyx/demo/EpdDemoActivity.java`（EpdDeviceManager / EpdController 调用实测）
  - `app/OnyxAndroidDemo/src/main/java/com/android/onyx/demo/RefreshModeDemoActivity.java`（`Device.currentDevice().getAppScopeRefreshMode / setAppScopeRefreshMode` 实测）
  - `app/OnyxAndroidDemo/build.gradle`（依赖坐标、ABI、packagingOptions 实测）
  - 根 `build.gradle`（仓库声明实测）

## 1. SDK 分发方式与版本（实测）

- 官方仓库 README 声明（实测）：`com.onyx.android.sdk:onyxsdk-device:1.1.11`、`com.onyx.android.sdk:onyxsdk-pen:1.2.1`；另需 jitpack（DBFlow/scribble 传递依赖）与 `repo.boox.com` 仓库。
- 演示工程实际使用（`app/OnyxAndroidDemo/build.gradle` 实测）：`onyxsdk-device:1.3.5.3`、`onyxsdk-pen:1.5.4.4`。README 与工程内版本号不一致——引用时以工程文件与 changelog 为准，README 视为滞后。
- 根 `build.gradle` 实测仓库声明：`http://repo.boox.com/repository/proxy-public/` 与 `http://repo.boox.com/repository/maven-public/`（**明文 http**，见 §3 风险），外加 `google()`、`mavenCentral()`、`https://jitpack.io`。
- Base SDK 文档页声明最新 `onyxsdk-base:1.4.3.7`（含 Dict Query API 起的 changelog；EpdDeviceManager 自 1.2.3 加入；触摸区域 API 需 base ≥ 1.4.3）。
- mvnrepository 页面实测（2026-09-17）：`onyxsdk-device` 最新 `1.3.5.3`（日期 2026-09-07）、`onyxsdk-pen` 最新 `1.5.4.4`（日期 2026-09-07）。
  - https://mvnrepository.com/artifact/com.onyx.android.sdk/onyxsdk-device
  - https://mvnrepository.com/artifact/com.onyx.android.sdk/onyxsdk-pen
- **未验证**：上述版本是否真实托管于 Maven Central。`search.maven.org` 直查于 2026-09-17 返回 403，无法确认；mvnrepository 页面的 "Indexed Repositories" 为索引源列表，不等于 Central 托管证明。Leaflet 如需引用，必须走 `repo.boox.com` 私有仓库（并复核其 https/可用性），不得在 ADR 中宣称“Central 可用”。

## 2. API 要点（均有官方文档或源码实测出处）

### 2.1 EpdController（底层、primitive）

官方明确定位（EpdController.md 实测原意）：刷新 API 较为底层，**应用层推荐用 EpdDeviceManager 封装**，不要处处直调。实测到的接口名：

- `setViewDefaultUpdateMode(view, UpdateMode)`：设置某 View 的默认更新模式（文档例：`GU` 常规局刷、`REGAL` 文本页优化局刷）。
- `invalidate(view, UpdateMode.GC)`：全屏刷新去残影。
- `repaintEveryThing(UpdateMode.GC)`：整屏重绘（EpdDemoActivity 实测在用）。
- `applyAppScopeUpdate / clearAppScopeUpdate`：应用级刷新域控制（EpdDemoActivity 实测用 `ANIMATION_X`、`ANIMATION_QUALITY`、`DU_QUALITY`、`None` 四档切换）。
- `applyApplicationFastMode(app, enter, clear)`：文档页记载的快刷进出接口；**未验证**其在 `onyxsdk-device:1.3.5.3` 是否保留——演示源码实测使用的是 `EpdDeviceManager.enterAnimationUpdate / exitAnimationUpdate`，集成时以随包 API 为准。
- `setWebViewContrastOptimize(webView, enabled)`：防止 WebView 误入 A2 模式（base 1.4.3.4+）。Leaflet 无 WebView 路径，此条仅记录。
- 触摸区域（base ≥ 1.4.3，EPD-Touch.md）：`isCTPDisableRegion` / `setAppCTPDisableRegion`（Rect[] 或 int[] 两种重载，支持 exclude 区域）/ `appResetCTPDisableRegion`。Leaflet MVP 不需要。

### 2.2 EpdDeviceManager（推荐的应用层封装）

- 定位：兼容不同平台（文档称 RK3026 / IMX6）的统一封装，自动选择 normal/regal 局刷。
- 实测接口：`setGcInterval(n)`（多少次局刷后插一次全刷；demo 设 5）、`applyWithGCInterval(view, isTextPage)`、`applyWithGCIntervalWithoutRegal(view)`、`applyWithGCIntervalWitRegal(view, boolean)`（**注意源码拼写即 `WitRegal`**，照录以便未来对照）、`applyGCUpdate(view)`（强制全刷）、`enterAnimationUpdate(boolean)` / `exitAnimationUpdate(boolean)`（快刷进出）。
- 可借鉴设计：**GC 间隔计数器**（局刷 N 次后自动全刷清残影）是 Leaflet `GenericEInkController` 可以用纯框架方式模拟的策略（计数 + 偶发全量重绘请求），但必须明确那只是“系统刷新节奏”，不是硬件波形控制。

### 2.3 UpdateMode（来自 EPD-Update-Mode.md 全表照录，不增删）

`DU`（黑白）、`DU_QUALITY`（带抖动优化的 DU）、`GU`（16 级灰局刷）、`GU_FAST`（**已废弃**）、`GC`（16 级灰全刷）、`GC_4`（**已废弃**）、`ANIMATION`（比 DU 更快的黑白）、`ANIMATION_QUALITY`（带抖动优化的 animation）、`REGAL`（文本页优化的 16 级局刷）、`REGAL_D`（文档注明当前等同 REGAL）。

- Leaflet 语义映射（MVP 不调 SDK，只约束自有接口命名）：翻页/文本重排 ≈ REGAL 意图、全刷去残影 ≈ GC 意图、滚动/拖拽 ≈ FAST/animation 意图。**意图名可以借，能力承诺不可以借**（见 §5）。

### 2.4 UpdateOption / Fast Mode

- `UpdateOption`（应用级刷新档，RefreshModeDemoActivity 实测经 `Device.currentDevice().get/setAppScopeRefreshMode` 读写）：`NORMAL`（通用文本阅读效果好）、`FAST_QUALITY`（轻微残影，快速浏览图文）、`REGAL`（残影最小、深色底轻微闪烁，适合浅色底）、`FAST`（残影稍重，适合滚动图文）、`FAST_X`（细节损失大，适合网页/视频）。
- Fast Mode 本质是牺牲灰阶保速度的黑白/低灰模式，进出必须配对调用并在退出后安排一次正常刷新，否则 UI 会卡在低质量模式——演示源码中进出按钮是分离的，Leaflet 未来封装时必须做成 try/finally 或生命周期配对。MVP 不用。

### 2.5 设备检测

- 实测：`Device.currentDevice()` 为统一入口（RefreshModeDemoActivity 用它读写应用级刷新档；Scribble 文档用它进出 scribble 模式）。
- 实测：`DeviceUtils.setFullScreenOnResume(activity, fullscreen)`（README）。
- 实测：`DeviceEnvironment.getRemovableSDCardDirectory()`（外置存储；Leaflet 走 SAF，不采用）。
- 实测：`FrontLightFactory` 按机型动态选择单色/冷暖双色灯模型（按需查灯能力再 inflate UI 的模式值得借鉴到 `AppCapabilities`）。
- **未验证/推断**：按 `Build.MODEL / Build.DEVICE` 字符串分支 BOOX 机型是常见做法，但本次未在官方文档页找到推荐机型表；Leaflet **不得依赖具体机型**，只允许按能力（capability）分支。目标尺寸（6"/7"/10.3"）只用于响应式验证矩阵，不做机型特判。

### 2.6 与 Leaflet 无关、明确不采用的

- 笔/Scribble（`onyxsdk-pen`、TouchHelper、BrushRender、moveTo/lineTo、压力 `getMaxTouchPressure`）：Leaflet 是只读阅读器，无标注需求，不引入。
- 前光控制（需 `WRITE_SETTINGS` 权限）：MVP 不做，未来即使做也须走系统设置语义并单独申请权限，不在阅读器内静默改系统亮度。
- 字典/OTA/屏保/设置页拉起等 Misc API：超出范围，不采用。

## 3. 对 APK 体积 / Play 兼容 / ABI / native 的影响（实测 + 未验证分开写）

**实测（演示工程侧事实）：**

- `ndk { abiFilters "arm64-v8a", "armeabi-v7a" }`，且 2026-09-09 刚有提交修复缺 arm64-v8a 导致的 `INSTALL_FAILED_NO_MATCHING_ABIS`——说明 SDK 传递物与 ABI 子集强相关，Leaflet 若集成必须声明并实测双 ABI 安装。
- `packagingOptions { pickFirst 'lib/*/libc++_shared.so' }`——依赖图中存在 native `.so` 冲突需 pickFirst，SDK 有 native 传递是实锤信号（至少 C++ 运行时层面）。
- `multiDexEnabled true`、DBFlow（annotationProcessor + core + dbflow，经 jitpack）、`hiddenapibypass`、`multidex`、`constraintlayout` 等传递依赖——第三方仓 + jitpack 会扩大体积与供应链面。
- 演示工程 `compileSdk 30 / targetSdk 30 / AGP 3.6.4 / minSdk 24` 均为演示侧旧基线，**不代表 SDK 要求的上下限**；Leaflet 的 minSdk/targetSdk 以 T01 官方兼容表为准，不得引用此基线。
- 根仓库 URL 为**明文 http**——Leaflet 若在将来引用，必须评估是否切 https 镜像/代理、私有仓可用性、CI 可复现性及未来 Android 明文流量限制；私有仓本身也是供应链风险（单点、不可审计）。

**未验证（集成前必须补，MVP 不欠）：**

- APK 体积增量具体字节数：**未验证**（按约束未运行构建；T13 的 `apk-size.md` 补 full/eink 双包实测）。
- 完整 native `.so` 清单（哪些 ABI、各体积）：**未验证**。
- Play 兼容（Play SDK Index 收录、政策合规、目标 API 等级）：**未验证**，不得宣称可上架无碍。
- 前光 `WRITE_SETTINGS` 等权限对 merged manifest 的影响：Leaflet MVP 不引 SDK，故无影响；M5 集成时实测 merged manifest。

## 4. 许可证

- 演示仓库本身：Apache-2.0（LICENSE 实测，版权人 Onyx International Inc.）。
- mvnrepository 上 `onyxsdk-device` / `onyxsdk-pen` 页面显示为 **BSD 3-clause**——与仓库 LICENSE 不一致。**结论：SDK 构件的真实授权以随包 LICENSE/NOTICE 为准，当前标“未验证”**；M5 集成前必须下载构件核对随包文本并记入 `THIRD_PARTY_LICENSES`。
- 在核对完成前：不复制、不改写、不分发 SDK 内任何代码；本报告只记录接口名与行为描述。

## 5. MVP 决定：不集成 SDK（T00→T12 生效，M5 前不复议）

**决定：MVP（M0–M1，含 T12 显示策略验收）不集成任何 Onyx SDK，只交付 `NoOpEInkController` + `GenericEInkController`，刷新完全依赖系统行为。**

理由：

1. 私有仓（明文 http）+ 传递依赖（DBFlow/jitpack、native .so、multidex）会污染依赖图，与“eink flavor 做减法、full 不被污染”的发行目标冲突。
2. ABI/体积/Play/许可证四项均有未验证项，MVP 阶段背不起；且无 E-Ink 真机时任何波形结论都无法验收。
3. Leaflet MVP 的 E-Ink 体验完全可用框架手段实现：禁用动画/平滑滚动/overscroll/ripple/crossfade（DisplayProfile + MotionPolicy 集中控制）、viewport 翻页（overlap 默认 5%，允许 3%–8%）、静态/节流加载态、GC 节奏模拟（计数 N 次局刷意图后请求一次全量性质的系统重绘）。残影控制靠“少刷、整页刷、偶发全量”三原则，不是硬件波形。
4. 架构约束（AGENTS.md + docs/archive/init.md §18）：`Reader` 永不直引 Onyx SDK；`EInkController` 接口只暴露 `onReaderEnter / onReaderExit / onPageTurn / onScrollStart / onScrollEnd / requestPartialRefresh / requestFullRefresh` 类意图方法，不暴露 `UpdateMode` 类型。

**禁止的虚假承诺（T12 设置与文档必须遵守）：**

- 不得宣称 Generic/NoOp 可触发硬件局刷（REGAL/GU）或全刷（GC）。
- 不得在设置中提供无法兑现的硬件档位（UpdateOption 五档、GC 间隔、Fast Mode 开关）。
- “requestFullRefresh”在 MVP 的真实语义是“向系统请求一次全量重绘”，残影结论只能来自 E-Ink 实机矩阵；缺设备时标 NOT_RUN/BLOCKED，不标通过。

## 6. M5 隔离集成前提（全部满足方可建 `OnyxEInkController`，并先立 ADR-0007）

1. **源码隔离**：实现只进 `eink` flavor 独立 sourceSet（及 `:core:eink` 下的 onyx 子包/子模块）；`full` flavor 零引用，`Reader` 核心仍只依赖 `EInkController` 接口。
2. **依赖隔离**：Onyx 坐标只声明在 `eink` flavor 依赖块；`fullRelease` 产物不得含有 Onyx 字节码/native（T13 以 APK 分析自证）。
3. **版本 pin 与供应链**：固定经核验的 `onyxsdk-device` 版本（以随包 API 为准重核 §2.1 中标未验证项）；私有仓 https/代理、CI 可复现、传递依赖（含 DBFlow/jitpack 是否必需）逐项记录。
4. **许可证闭环**：随包 LICENSE/NOTICE 核对完成，`THIRD_PARTY_LICENSES` 更新；与仓库 Apache-2.0 不一致处写明。
5. **ABI/体积/权限基线**：双 ABI 实机安装验证、双包体积差记录、merged manifest 权限（尤其 WRITE_SETTINGS 类）评审。
6. **行为契约**：`OnyxEInkController` 把 §2 的 primitive 包成与 `Generic` 同语义的意图方法；Fast Mode 进出配对、GC 间隔可配并默认保守；非 BOOX 设备上自动降级为 Generic 行为（能力检测，不做机型白名单）。
7. **验收**：E-Ink 实机矩阵（6"/7"/10.3" 类别）给出残影/闪烁/翻页结论；失败回退即关闭该实现、回 Generic，不阻塞 full 包。

## 7. 后续跟踪

- 长期跟踪仓库 `onyx-intl/OnyxAndroidDemo` 的 SDK 版本更新与 doc 变更（本报告锚定 commits 见 §0）。
- ADR-0007（Onyx SDK 集成）在 M5 前保持 Proposed；T01 不得提前 Accepted。
