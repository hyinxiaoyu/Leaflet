# 阅读控制 — 产品迭代第二轮

[English](reader-controls-product-wave2.md) | 中文

审查编号：READER-CONTROLS-20261001。状态：IMPLEMENTED, DEVICE ACCEPTANCE PENDING。范围：REQ-0014，继续用户要求的电子书阅读打磨。本独立记录不确认此前的验收报告。

## 交付结果

静态页脚现在提供上一屏/菜单/下一屏，不再完全依赖隐藏点击区。上一屏/下一屏使用现有即时翻页路径与真实列表边界；连续滚动模式不显示翻页按钮。保留进度输出和逻辑位置持久化。控制高度至少 48 dp，使用标准启用/禁用语义。

阅读菜单中的现有目录、搜索、重新载入、模式及返回操作可换行，并增加操作说明。菜单高度限制为固定 Reader 视口的 40%；额外操作在菜单内滚动，不改变正文列表尺寸。动画禁用时，LocalMotionPolicy 选择无惯性实现，使墨水屏菜单松手即停；Standard 保留现有 Foundation 惯性行为。

帮助复用现有教程，可重新打开而不清除已存已读标记。教程显示和初始逻辑位置恢复未完成时保护后台 Reader 操作，包括页边距触摸。指针离开 Reader 时永久取消本次手势，即使松手前重新进入。正文滑动仍仅在松手时翻一屏，子控件/表格/代码手势仍优先。

## 任务拆分与实现

| 任务 | 文件与交付 | 状态 |
| --- | --- | --- |
| RC-01 可见导航 | 新 ReaderControls.kt 导航栏；ReaderScreen.kt 委托现有列表/菜单/翻页回调，使用真实边界状态 | 已实现 |
| RC-02 可达菜单 | FlowRow 放在限高独立滚动容器中；BoxWithConstraints 提供视口限高；集中 MotionPolicy 控制惯性 | 已实现 |
| RC-03 帮助与输入安全 | 现有教程新增会话内重开入口；保护后台输入；PageGesture/ReaderPageGestures 取消指针越界/重入 | 已实现 |
| RC-04 验证 | 3 项新 JVM 手势回归、1 项菜单动画测试、5 项新真实页面/组件用例、资源及双发行版本新编译组件 | 范围内检查通过；设备执行待完成 |

实现前将 REQ-0014 写为 READY，当前为 IN_PROGRESS。已更新需求索引、Reader 架构和成对更新日志。保留共用 Reader 状态、原始文件只读访问、手工接线、发行版本隔离与集中动画策略。未新增厂商 SDK、依赖、迁移、虚构页数或物理按键映射。保留此前未提交的首页/资料库/位置/图片/表格改动及历史证据。

主要新增文件：

```text
app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderControls.kt
app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderMenuMotionTest.kt
app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderControlsPageTest.kt
docs/requirements/REQ-0014-reader-controls.md
docs/requirements/REQ-0014-reader-controls.zh.md
docs/plans/reader-controls-product-wave2.md
docs/plans/reader-controls-product-wave2.zh.md
docs/plans/review-evidence/2026-10-01-reader-controls/
```

## 实际验证

环境：macOS ARM64、JDK 17.0.9、冻结的 Kotlin/Compose 编译器 2.0.21、Foundation 1.7.5、ktlint 1.0.1 与 build-tools 35.0.0。检查器读取缓存的已解析依赖和现有合并资源/清单作为链接输入。编译并链接当前英文/中文/发行版本字符串及 48 组依赖资源，生成真实 Java R 类。重新编译核心翻页/设置和 10 个当前 Reader 文件。仅含资源的容器是临时链接产物，不是可安装应用 APK。

| 检查 | 实际结果 |
| --- | --- |
| 现有翻页/设置与指针边界契约 | PASS — 41 项 JVM 测试，含 3 项新增指针越界/重入回归 |
| 保守菜单惯性契约 | PASS — 1 项 JVM 测试验证负/零/正速度均无滚动调用；实际执行 JVM 共 42 项 |
| 改动 Kotlin 格式 | PASS — ktlint 保留现有 Compose 命名例外 |
| 真实资源/链接/生成 R，Full/Eink | PASS — 两种发行版本；没有虚构 R 标识符 |
| Reader 组件与页面套件，Full/Eink | PASS — 四次新编译 Kotlin/Compose 组件 |
| ReaderInteractionTest + ReaderControlsPageTest | 22 + 5 个方法已编译；Android 上 NOT_RUN |
| Gradle 8.13 Full/Eink APK 门禁 | 任务执行前 BLOCKED：FileLockContentionHandler 报 java.net.SocketException: Operation not permitted |
| 完整套件/lint、实际页面、真实 E-Ink/TalkBack | NOT_RUN；不声明完整应用构建/设备验收通过 |

5 个新增页面/组件用例覆盖可见导航与不移动正文的菜单开关、单屏文档边界禁用按钮、重复帮助和教程页边距输入保护、连续模式保留菜单且无翻页按钮，以及 280 dp 宽/200% 字体下六个菜单操作在 120 dp 限高内可达。它们执行真实 Reader 或真实 ReaderActionMenu，不使用测试专属 UI 副本。现有交互用例针对改动 Reader 编译，覆盖视口位移、首尾、重叠、链接与宽表格/代码手势归属。

首次 Reader 编译发现 ExperimentalLayoutApi 导入路径错误，以及隐式 BoxWithConstraints 接收者被 ColumnScope 隐藏。成功门禁前均已修正；初始诊断独立保留。现有 ImageLoader 与 LocalLifecycleOwner 警告不属于本轮范围。源码检查还发现新菜单的默认惯性；当前通过集中策略和无滚动契约覆盖。

## 证据与边界

[机器可读结果与源码哈希](review-evidence/2026-10-01-reader-controls/results.json)、编译/资源/测试日志、初始诊断及自清理检查器保留在 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-reader-controls/`。所有编译/资源/Gradle 临时目录仅创建于 `/tmp/leaflet-reader-controls-*`，检查器退出时删除，包括失败尝试。报告/证据为有意保留的交付物。不声明截图、可安装 APK、提交或发布。

进度仍为现有按块输出和已存回退语义，本轮不虚构稳定页码。按钮实际可读性、菜单换行、帮助交互、列表几何、恢复未完成时的输入与墨水屏残影仍需设备执行。缺少设备证据，因此 REQ-0014 不能标记 DONE。

## 重现与后续门禁

使用冻结本地缓存重新执行范围内验证：

```bash
python3 docs/plans/review-evidence/2026-10-01-reader-controls/run_checks.py \
  --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

允许 Gradle/ADB 套接字后，使用 JDK 17 执行：

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderControlsPageTest,io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderControlsPageTest,io.leaflet.reader.ui.ReaderInteractionTest
```

随后在手机和真实墨水屏上测量：文末开关菜单时固定列表边界、200% 字体的窄竖屏/横屏全部操作可达、末行可见、墨水屏菜单无惯性、指针越界/重入和帮助。此前报告继续作为各自日期的快照保留。
