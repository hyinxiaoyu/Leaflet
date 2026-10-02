# 阅读器代码审查 — 2026-09-30

[English](code-review-2026-09-30.md) | 中文

## 结论与范围

提交 `5a754292c1ea02f33af0cb917f5cf24c29dbb763`（`feat(reader): freeze the reader list geometry and float the chrome (UX-06)`）中有五个 P2 问题，状态均为 OPEN。验收 UX-06 前应解决浮层（overlay）相关问题。外部链接与阅读模式问题涉及同一提交首次纳入版本控制的其他生产代码，不能将其直接归因于 UX-06 布局改动。

> **跟进状态（2026-09-30）：** 五个问题均已在被审查提交之上修复；CR-01/02/03 的关闭回归与 CR-04 的设备场景仍为 NOT_RUN。见本报告末尾的[解决结果](#resolution-2026-09-30-follow-up)。

本次重点审查阅读器浮层、翻页、导航、设置及关联的存储与测试路径，并非全仓穷尽审计。静态源码路径已确认；用户可见故障尚未在设备上独立复现。下文复现步骤是建议补充的回归场景（regression scenario），不是已执行的测试。

所有源码行号均对应被审查提交，而非正在并行变化的工作区。本报告的问题编号仅在本报告内使用，不重编号历史 R2 缺陷台账。执行者此前的证据见 [UX-06 交付报告](ux-06-delivery.md)。

| 编号 | 优先级 | 问题 | 需求 / 任务 | 状态 |
| --- | --- | --- | --- | --- |
| CR-20260930-01 | P2 | 末尾钳制可能在页脚遮挡内容揭示前停止 | REQ-0005 FR-2/FR-7; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-02 | P2 | 目录跳转落点可能仍在标题浮层下方 | REQ-0006; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-03 | P2 | 首块搜索落点无法移到搜索浮层下方 | REQ-0008; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-04 | P2 | 包可见性预检查可能拒绝可启动的外部 URL | REQ-0009 FR-10 | FIXED — discriminating device regression NOT_RUN |
| CR-20260930-05 | P2 | E-Ink 新安装默认采用连续滚动 | REQ-0005 FR-4 | FIXED — JVM regressions PASS |

<a id="resolution-2026-09-30-follow-up"></a>

## 解决（2026-09-30，跟进）

这五个问题均已在被审查提交之上的工作区中处理。浮层相关问题通过修改阅读器的遮挡策略修复；另外两个问题则在各自指名的生产代码中修复。设备级复现仍未执行（本机未连接设备或模拟器），因此 CR-01/02/03 的关闭回归与 CR-04 的包可见性场景仍为 NOT_RUN。

**CR-20260930-01 — 末尾钳制。** 列表的尾部间距现在预留实测的底部外层控件高度（`max(32 dp, bottomChromePx + 8 dp)`），而不再是固定的 32 dp；翻页钳制改为向列表询问其真实滚动边界（`listState.canScrollForward` / `canScrollBackward`），而不再根据最后一项相对 `viewportEndOffset` 的位置来推断。因此在末尾时，最后一行位于页脚和已打开的阅读菜单之上，继续翻页不再产生任何变化。尾部间距只在外层控件本身变化时变化，且绝不移动任何条目：该内边距位于最后一个条目之后，因此切换菜单时正文仍不会重排。
回归：`ReaderInteractionTest.tailTurnRevealsTheLastLineAboveTheFooterAndTheOpenMenu` 断言组合出的最低正文行结束于底部外层控件的 `boundsInRoot.top` 之上——先在菜单关闭时断言，再在菜单打开时断言——并断言随后的翻页不会移动文档。阅读器的两个外层控件层带有语义标签（`ReaderChromeTags`），因此测试测量的是真实浮层矩形，而不是从内边距推断。

**CR-20260930-02 — 目录落点。** `jumpToAnchor` 现在在返回正文时会关闭阅读菜单（从而关闭标题浮层），且落点复用与搜索相同的揭示式跳转。
回归：`ReaderInteractionTest.tocLandingIsNotCoveredByTheTopChrome` 执行真实的抽屉点击，断言菜单已消失、所选标题的顶部位于顶部外层控件之下，并断言随后的翻页仍让正文避开该控件。

**CR-20260930-03 — 首块搜索落点。** 各类提示与搜索栏现在把自身实测高度预留为列表的头部预留空间（headroom）（`contentPadding(top = topInsetPx)`），因此正文在其下方留有内边距；第 0 块中的命中——任何向上滚动都无法将其揭示——现在可见。文档标题被有意排除在该头部预留空间之外，且打开搜索栏会关闭菜单，因此切换菜单仍不会移动正文。后续块的跳转使用 `scrollToItem(target, -topChromePx)`（已记录的偏移约定：负值把条目向下推）。
回归：`ReaderInteractionTest.firstBlockSearchMatchStaysBelowTheSearchBar` 通过真实界面打开搜索、在真实输入框中输入，并断言组合出的最上方正文行在首个命中、后续命中、再次回到首个命中以及翻页模式下都起始于顶部外层控件之下。

**CR-20260930-04 — 外部链接预检查。** `MainActivity.launchExternalUrl` 不再查询 `Intent.resolveActivity(packageManager)`；它直接启动并保留 `ActivityNotFoundException` 恢复路径，这是在包可见性下移交 URL 的官方推荐做法。由于不发起查询，未添加 `<queries>` 声明。
回归限制：要区分验证该修复，需要一台安装了可启动、但应用无法查询的处理应用的设备，本环境无法提供；该问题提出的设备场景仍是关闭步骤。

**CR-20260930-05 — E-Ink 默认阅读模式。** `ReaderSettings.defaultReadingModeFor(displayMode)` 现在提供首次运行的默认值（E-Ink → `PAGE_TURN`，Standard → `CONTINUOUS_SCROLL`）；`SettingsStore.readingMode` 在键缺失时使用它，阅读器与 Settings 屏幕也用它作为预读初始值。用户显式保存的选择仍然优先，切换显示模式后亦然：策略是阅读模式属于独立设置，因此从 Standard 切换到 E-Ink 的安装会保留已存模式，直到用户主动选择翻页。
回归（JVM，已执行且通过）：`ReaderSettingsTest.firstRunReadingModeFollowsTheDisplayMode` 与 `SettingsStoreReadingModeTest`（全新 E-Ink 安装、全新 Standard 安装，以及显式选择在重读和显示模式切换后仍保留）。

本次跟进的验证（2026-09-30）：`./gradlew test --rerun-tasks` 执行 694 个 JVM 用例（307 个去重方法），0 失败、0 错误、0 跳过；`lint` 与 `ktlintCheck --rerun-tasks` 通过；`:app:assembleFullDebug` 与 `:app:assembleEinkDebug` 产出 `app-full-debug.apk`（29,206,813 B）与 `app-eink-debug.apk`（28,488,137 B）；两个变体的 androidTest 源集均可编译。插桩测试本身——以及上文的全部设备场景——均为 NOT_RUN：未连接设备或模拟器（`adb devices` 为空，且未安装 AVD）。交付记录见 [ux-06-delivery.md](ux-06-delivery.md)。

## CR-20260930-01 — 末尾钳制忽略页脚遮挡

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:384–387`，固定尾部间距位于第 444 行。
- **触发与影响：** 在 `PAGE_TURN` 中，最后一项的底部可能处于完整列表视口（viewport）内，却被进度页脚遮住。此时 `atBottom` 会拒绝下一次翻页，即使尾部间距仍留有可滚动空间，最后几行也无法揭示。展开的菜单还可能超过固定 `32.dp` 尾部间距。
- **原因：** 翻页距离扣除了 `bottomChromePx`，但末尾判断直接比较 `info.viewportEndOffset`，没有扣除遮挡或考虑剩余滚动空间。
- **最小修复方向：** 使用列表实际滚动边界，并确保最终内容可以在页脚上方揭示。处理展开控件时保持列表几何尺寸固定，不引入随菜单变化的列表尺寸调整。
- **回归与关闭条件：** 选择合适的文档长度与排版，使最后一项初始落在页脚下且仍有滚动空间。向前翻页并断言最终文字的实际边界位于页脚上方，再验证继续翻页无变化。分别覆盖菜单展开与关闭；仅断言最后一个块已参与组合（composition）不足以证明可读。

## CR-20260930-02 — 目录落点仍被标题遮挡

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:395–403`；标题可见条件位于第 589–595 行。
- **触发与影响：** 打开阅读菜单，进入目录（table of contents, TOC），选择距文档末尾足够远的标题。目标标题会落在列表顶部，被仍显示的文档标题浮层遮住。
- **原因：** `jumpToAnchor()` 关闭 `tocOpen`，却保留 `menuVisible` 为 true。它调用 `scrollToItem(target)`，未使用搜索和同文档链接跳转的顶部控件避让逻辑。
- **最小修复方向：** 为目录落点应用一致的顶部遮挡处理策略，或在返回正文时显式关闭遮挡落点的菜单/标题。保留即时滚动和既有位置契约。
- **回归与关闭条件：** 通过真实目录点击后，断言目标标题的可见边界位于剩余顶部控件下方。覆盖提示条存在的情况，并验证后续翻页；仅检查 `firstVisibleItemIndex` 不能证明可见性。

## CR-20260930-03 — 首块搜索揭示被顶部边界钳制

- **源码：** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:196–198`，搜索落点位于第 349–356 行，列表间距位于第 444 行。
- **触发与影响：** 搜索第 0 块开头的文字。搜索计数显示已有命中，但当前命中仍可能隐藏在浮动搜索栏下。
- **原因：** `scrollToItem(0)` 后，`revealBelowTopChrome()` 请求负向 `scrollBy`。列表已经位于顶部且没有顶部内容间距，因此无法按所需距离将首块向下移动。
- **最小修复方向：** 提供在文档开头也能生效的揭示策略，例如与冻结几何尺寸要求兼容的固定顶部预留空间。仅使用负向滚动无法解决这一边界情况。
- **回归与关闭条件：** 使用无标题且首行包含查询词的文档。通过真实界面打开搜索，断言实际命中文字位于搜索栏下方。覆盖两种阅读模式，以及从后续命中跳回首块；不能用模型中的命中记录或计数代替可见性证明。

## CR-20260930-04 — 外部链接预检查可能误报没有处理应用

- **源码：** `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt:122–129`；`app/src/main/AndroidManifest.xml` 没有相应的 `<queries>` 声明。
- **触发与影响：** 在 Android 11+、应用目标 API 为 35 时，设备可能已安装可启动但对包查询不可见的 URL 处理应用。预检查仍可能返回 null，误报没有外部应用并阻止 URL 启动。
- **原因：** 代码将 `intent.resolveActivity(packageManager)` 作为 `startActivity()` 的前置条件。包可见性（package visibility）可以过滤查询结果，而启动 Activity 仍被允许。[Android 官方指南](https://developer.android.com/training/package-visibility/use-cases#open-urls) 建议直接启动并捕获 `ActivityNotFoundException`，或在确需预检查时声明适当的查询范围。
- **最小修复方向：** 移除强制解析预检查，保留既有 `ActivityNotFoundException` 恢复路径。如果保留预检查，应声明预期的查询覆盖范围。
- **回归与关闭条件：** 在已安装但被包查询可见性排除的 URL 处理应用下，验证真实 MainActivity 启动路径；另测没有处理应用的情况。分别确认成功移交与可恢复提示。当前基于回调（callback）的链接测试没有覆盖 PackageManager 或 Activity 启动。

## CR-20260930-05 — E-Ink 默认阅读模式为连续滚动

- **源码：** `app/src/main/kotlin/io/leaflet/reader/SettingsStore.kt:119–122`；`core/storage/src/main/kotlin/io/leaflet/storage/prefs/ReaderSettings.kt:53–55`；模式读取位于 `ReaderScreen.kt:240–246`。
- **触发与影响：** 在未保存 `reading_mode` 偏好的全新 `eink` 安装中打开文档，会进入 `CONTINUOUS_SCROLL`；左右触控区点击无效，垂直手指滚动启用，而非需求规定的 E-Ink 默认翻页模式。
- **原因：** 缺失偏好统一使用 `ReaderSettings.READING_MODE_DEFAULT`，其值为 `CONTINUOUS_SCROLL`。存储层的 `defaultMode` 没有参与阅读模式默认值计算。
- **最小修复方向：** 未保存用户选择时，根据实际显示模式推导默认值：EINK → PAGE_TURN，STANDARD → CONTINUOUS_SCROLL。保留用户显式保存的阅读模式选择。
- **回归与关闭条件：** 验证两个分发变体（flavor）的首次使用默认值，以及真实侧边点击/拖动行为。验证显式选择在重开和模式变化后仍保留，并记录 Standard 变体在首次选择阅读模式前切换到 EINK 的默认策略。

## 验证与边界

使用 `git archive` 将被审查提交提取到独立的 `/tmp/leaflet-review.*` 目录，避免并行源码修改影响结果。环境为 macOS ARM64、JDK 17.0.9、Gradle Wrapper 8.13，已安装 Android SDK 位于 `/opt/homebrew/share/android-commandlinetools`。既有 Gradle 依赖复制到临时用户目录；检查使用离线模式并禁用构建缓存（build cache）复用。

```bash
./gradlew --offline --no-daemon --no-build-cache \
  :core:reader:testDebugUnitTest \
  :core:storage:testDebugUnitTest \
  :app:testFullDebugUnitTest \
  :app:testEinkDebugUnitTest
```

| 检查项 | 结果与边界 |
| --- | --- |
| 隔离提交 JVM 检查 | PASS；exit 0；24 个 XML 报告共执行 249 个测试用例，0 失败、0 错误。该数字不是全项目去重测试数量。 |
| 首次工作区检查 | FAILED；并行新增且未跟踪的 `TableGrid.kt` 无法解析 `constrainWidth` / `constrainHeight`。该文件不在被审查提交中，失败不属于已提交代码问题。 |
| 针对这些问题的新增设备回归 | NOT_RUN；上述建议场景尚未执行。 |
| E-Ink 硬件、布局检查器（layout inspector）证据及截图 | 本次审查为 NOT_RUN。 |
| Lint、ktlint、APK 构建及全套验收 | 本次审查未重新运行；此前执行者结果仅作为历史证据。 |

既有 JVM 测试通过不能关闭这些问题。关闭需要上述能区分缺陷的回归，以及适用的双变体检查。本次审查及其文档不授权产品实现，也不将需求/任务状态改为 DONE。

## 产物与清理

- 临时提交快照、复制的 Gradle 缓存、日志及隔离测试报告仅创建于 `/tmp/leaflet-review.*`；验证后这些目录均已删除。
- 首次工作区检查更新了 `/Users/clover/source_dev/Leaflet/app/build/` 与 `/Users/clover/source_dev/Leaflet/core/*/build/` 下的既有标准 Gradle 构建产物。为避免干扰并行开发予以保留；未删除无关源码或既有产物。
- 本次文档跟进新增本报告及其完整中文配对文件，并在 UX-06 交付报告配对文件中添加入口。不修改产品源码或测试，不创建额外临时文件。
