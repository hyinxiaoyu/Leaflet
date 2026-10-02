# 电子书翻页交互跟进

[English](page-turn-interaction-followup.md) | 中文

审查编号：PAGE-TURN-20261001。范围：用户要求电子书式左右点击或滑动。状态：IMPLEMENTED；设备验收待执行。本文件是独立交付/验证记录，不确认此前的验收报告。

## 交付结果

两种发行版本和显示模式下，未配置的阅读器现在均默认 PAGE_TURN。明确选择的连续滚动偏好继续保存且可选。左/右三分之一区域（含页边距）上一屏/下一屏，中间区域切换阅读菜单。正文横向向左/右滑动，在松手时一次下一屏/上一屏。拖动中正文保持静止，翻页立即执行且无动画。每次仍使用现有“可见高度减重叠”算法（默认 5%），未实现排版分页引擎（typeset pagination engine）。

宽表格/代码手势、链接、按钮与复制控件优先。短滑、竖向意图、长按和多指均不能触发翻页。阅读位置、原始文件、依赖与存储格式保持兼容。通用 EInkController 仍是系统默认/NoOp 接口，不保证硬件局部刷新（partial refresh）。

## 诊断与任务拆分

| 任务 | 证据与对应变更 | 状态 |
| --- | --- | --- |
| PT-01 手势识别 | 旧阅读器只有点击检测，没有正文滑动识别。增加经过测试的 PageGesture 状态机与单一松手触发 Compose 检测器，子控件消费优先 | 已实现 |
| PT-02 点击范围与当前设置 | 原点击检测排除了横向内边距。将识别移至应用页边距前的 Reader 容器；rememberUpdatedState 提供最新回调/重叠/controller，Mutex 串行执行翻页 | 已实现 |
| PT-03 首次使用行为 | Standard 默认连续滚动，其两侧点击无动作。ReaderSettings/ReadingMode 现在默认 PAGE_TURN；保留有效明确偏好；双语首次提示说明滑动方向 | 已实现 |
| PT-04 验证 | 新编译 JVM 契约、双发行版本 Reader/交互组件、改动资源及格式检查；明确区分整包构建与设备执行 | 范围内检查通过；设备门禁待执行 |

在手势实现前更新了现有 READY 状态 REQ-0005，并在默认策略实现前更新该策略。当前状态为 IN_PROGRESS，FR-9/AC-8 记录本次手势行为。保留九月历史默认值证据并明确说明已被取代。保留其他未提交的首页、资料库、阅读位置、表格与图片改动。

## 手势契约

普通点击必须保持在系统触摸容差（touch slop）内，并在系统长按阈值前结束。点击区按整个 Reader 容器宽度三等分。滑动先在越过触摸容差后确立横向意图；最终横向位移至少为 `max(48 dp, 2 × touchSlop)`，且大于竖向位移。抬手时最多发出一个动作。子控件消费、竖向意图、在确立横向意图前达到长按阈值或多指均永久取消本次手势。已确立意图的横向滑动允许较慢地结束，不会转成长按。滑动折返至起点附近不会回退为点击。

沿用相同 LazyListState 与逻辑阅读位置持久化路径。PAGE_TURN 下正文列表仍不可拖动。CONTINUOUS_SCROLL 仍支持竖向滚动与中间菜单点击。有效已存连续滚动选择不会被静默重写：保留该模式的用户可在设置或中间阅读菜单选择逐屏翻页。无效值现在回退 PAGE_TURN 而不改写存储；无需迁移，旧版仍能识别两种已存模式字符串。

## 实际验证

环境：macOS ARM64；JDK 17.0.9；冻结的 Kotlin/Compose 编译器 2.0.21、ktlint 1.0.1、build-tools 35.0.0。独立检查器只将缓存的已解析依赖 JAR 与现有 app/资源产物作为链接依赖。重新编译核心翻页/设置与九个 Reader 源文件；不重建完整 APK。

| 检查 | 结果 |
| --- | --- |
| JVM 手势、位移、三区、仲裁、目录与模式契约 | PASS — 29 项，包含 10 项新增手势测试 |
| ReaderSettings 与真实 SettingsStore 默认值/持久化测试 | PASS — 6 + 3；JVM 总计 38 项 |
| Reader 组件与交互套件，Full/Eink | PASS — 四次新编译 Kotlin/Compose 组件 |
| ReaderInteractionTest 真实页面套件 | 22 个方法已编译（19 个现有 + 3 个新增）；Android 上 NOT_RUN |
| 英文/中文字符串经 AAPT2 | PASS — 两个资源文件编译通过；不声称资源链接和 APK 打包通过 |
| 改动 Kotlin 文件经 ktlint | PASS — 保留项目 Compose 命名例外 |
| Gradle 8.13 构建 Full/Eink APK | 任务执行前 BLOCKED — FileLockContentionHandler 报 java.net.SocketException: Operation not permitted |
| 实际触摸行为、手机/墨水屏、旋转、残影、TalkBack | NOT_RUN — 本沙箱没有重建的可安装应用或设备执行 |

三个新页面方法使用真实 ReaderScreen/session/parser 路径：松手前正文不动，滑动与点击翻页距离一致且允许 2 px 误差；两侧页边距接受点击；短滑/竖滑/长时间按住正文均不移动阅读器。现有页面用例覆盖表格/代码横向可达、链接/复制、重叠、菜单遮挡、快速翻页边界及模式切换保留位置。短滑样本使用按密度缩放的距离，确保越过触摸容差但不达到滑动阈值。

机器可读结果、源码哈希、编译/测试日志与自清理检查器保留在 [review-evidence/2026-10-01-page-turn/](review-evidence/2026-10-01-page-turn/)。测试/资源/编译/Gradle 临时产物仅创建于 `/tmp/leaflet-page-turn-*`，退出时由 TemporaryDirectory 删除。本报告及证据为有意保留的交付物；不声称提供截图、可安装 APK、提交或发布。

## 后续验收门禁

使用本地冻结缓存重现范围内检查：

```bash
python3 docs/plans/review-evidence/2026-10-01-page-turn/run_checks.py \
  --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

允许 Gradle/ADB 套接字后，使用 JDK 17 执行：

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

在手机和真实墨水屏设备上验收前，必须验证双向翻页、松手前无正文运动、页边距点击、保留明确连续滚动选择、宽表格/代码手势归属、打开菜单、末行及连续快速翻页。设备证据待补，因此不能声明 DONE 或发布。
