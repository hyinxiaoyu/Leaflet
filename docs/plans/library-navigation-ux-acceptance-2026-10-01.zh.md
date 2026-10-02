# 资料库导航体验 — 独立验收（2026-10-01）

[English](library-navigation-ux-acceptance-2026-10-01.md) | 中文

验收编号：`NAV-ACC-20261001`
类型：独立执行、限定范围的验收；与实现交付/自报记录分开。
结论：**NOT_ACCEPTED — 已复现两个生产 ViewModel 边界问题。**
范围：UX-05 / NB-00–NB-05 与 [REQ-0012](../requirements/REQ-0012-library-navigation.md)。
证据：[结果与源码哈希](review-evidence/2026-10-01-navigation-acceptance/results.json)。

## 证据分类

| 分类 | 本报告含义 |
| --- | --- |
| CONFIRMED_THIS_RUN | 本轮实际执行直接编译器/JUnit，或明确标注的静态/原始 API 检查 |
| HISTORICAL_SELF_REPORT | 既有交付/计划/任务记录中的陈述；本轮未独立重跑 |
| NOT_RUN / BLOCKED | 缺少执行证据或存在具体环境限制；都不等于通过 |

[原交付](library-navigation-ux-delivery.md)、[计划](library-navigation-ux-plan.md)、[任务卡](library-navigation-ux-tasks.md)及此前审查报告保持原样。它们的“820 条用例 / 341 个方法”、构建和 lint 陈述属于历史记录，不是本轮结果。本报告不撤销无关阅读器改动的历史验收，也不推断以前的命令从未执行。

这些记录中的“已交付/软件已验证”不足以关闭 NB-05：其页面矩阵自身标为 NOT_RUN，ktlint 也未完全通过。本次独立结论依据当前范围内的源码快照及明确的验证边界。

## 实际执行检查

环境：macOS ARM64；JDK 17.0.9；缓存中的 Kotlin 编译器 2.0.21、JUnit 4.13.2、Lifecycle 2.8.7、Coroutines 1.10.2。生产 `LibraryViewModel`、`Breadcrumbs`、`BrowseLevel` 及所需模型源码保持不变，在 `/tmp` 重新编译。既有 core/storage 字节码仅用于类型链接；被测 ViewModel 使用 `scanner = null`。不宣称执行了 Room/SAF/扫描器。

| 检查 | 结果 | 证据与边界 |
| --- | --- | --- |
| 全新直接 Kotlin 编译 | PASS | 当前生产/辅助/模型源码及聚焦测试；不是 Android 应用构建 |
| `BreadcrumbTest`（8）、`LibraryAncestorNavigationTest`（8）、`LibraryBrowseSelectionTest`（2） | PASS — 18/18 | [JUnit 输出](review-evidence/2026-10-01-navigation-acceptance/existing-junit.txt)；共用 JVM 代码，不是四个发行变体的执行 |
| 独立 `NavigationAcceptanceProbe` | FAIL — 2/2 | [探针源码](review-evidence/2026-10-01-navigation-acceptance/NavigationAcceptanceProbe.kt)、[JUnit 输出](review-evidence/2026-10-01-navigation-acceptance/independent-junit.txt) |
| 七个新增面包屑/行/扫描资源键 | PASS，仅静态 | 已解析 XML；每个默认键都有简体中文对应文本；不是全量语言/TalkBack 验证 |
| 聚焦 Gradle 测试调用 | BLOCKED，任务尚未启动 | [启动错误](review-evidence/2026-10-01-navigation-acceptance/gradle-blocked.txt)：`FileLockContentionHandler` → `java.net.SocketException: Operation not permitted` |
| Android 页面测试 / 当前 APK 构建 / lint / ktlint | NOT_RUN | Gradle 启动和/或 ADB socket 限制；未将旧输出重新标作本轮结果 |
| E-Ink 实机刷新/残影与 TalkBack | NOT_RUN | 本轮没有硬件/无障碍实测 |

ADB 安装本地监听器也因 `Operation not permitted` 失败（[本轮启动输出](review-evidence/2026-10-01-navigation-acceptance/adb-blocked.txt)）。列表中存在已安装的 `dev_arm64_api35` AVD，因此本轮记录为**沙箱访问受阻**，而不是“没有模拟器”。未请求绕过该限制。

## 已确认问题

### NAV-ACC-01 — 过期面包屑的来源身份丢失（P1）

**通过实际执行生产 ViewModel 确认。** 先生成资料库 A 的根目标，再把资料库 B 打开到 `Docs/Notes`，然后向 `openAncestor` 提交排队的 A 目标。预期：B 的状态不变。实际：B 的前缀变为空根目录。违反 REQ-0012 FR-2 / AC-2。

`app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` 中的 `openAncestor(target: String)` 只在方法调用时捕获资料库身份。原子更新校验防止的是调用过程中的切换，不是切换前生成的操作。UI 回调只提供路径；根字符串在所有资料库中都有效。现有“替换资料库”测试在 B 已位于根目录时发送 `Docs`，因此只证明非祖先目标被拒绝。

修复边界：NB-01/NB-02 的面包屑请求必须携带生成时的资料库与前缀/版本，和当前状态校验，并加入过期根目标、不同资料库同路径的用例。JVM 复现模拟排队的 UI 请求；Android 指针事件投递本身未运行。

### NAV-ACC-02 — 没有扫描所有者也能发布 CANCELLING（P2）

**通过实际执行生产 ViewModel 确认。** 打开资料库且没有活动扫描时，`cancelRefresh()` 会把 `IDLE` 改成 `CANCELLING`。`refreshJob` 不存在，因此没有被取消的任务负责发布终态。这是与 NB-03、AC-4 相关的恢复/状态机缺陷；完整活动扫描矩阵仍未执行。

`app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` 中，该方法取消可选任务后无条件覆盖进度。生产环境中，扫描结束后延迟到达的取消回调可能走同一个缺少/非活动所有者分支；取消界面可能卡住是基于源码的推断，不是设备观察。

修复边界：没有当前活动扫描或进度已到终态时忽略取消；保留已完成结果并验证取消所有权/竞态。关闭 AC-4 前，需通过真实扫描器接缝增加扫描完成后迟到取消的用例。

## 页面测试就绪情况与未验证行为

静态审查 `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` 发现，不能将六个已编译方法当作验收证据：

- 在含多个 document/MD 行的样本中使用单节点 `onNodeWithContentDescription(Document)` 和 `onNodeWithText(MD)` 查询，选择器缺少唯一范围；应在设备确认，并按行限定范围或明确采用多节点断言。
- 扫描 lister 只有一次性的 `CompletableDeferred` 门控。成功扫描释放后，不能再让重试扫描停在 SCANNING；等待这一瞬时阶段存在竞态。CANCELLING 也未单独测量。需要可重置/每次扫描独立的控制，并测量各必需阶段。
- 样本覆盖短名称和两层嵌套目录，未证明十层/窄屏可达性、200% 字号下的中文/英文长名称、空目录状态稳定性或 Standard/E-Ink 两种运行模式。执行两个发行版不等于证明两个运行策略。
- 已审查页面没有明确的不同前缀列表复位 `LazyListState`/前缀 key 处理。是否保留滚动位置而违反 FR-6，需要“已滚动目录 → 祖先目录”的页面复现；本报告不将其列为设备已确认失败。

最初对“无点击动作后调用 `performClick`”的疑点已**排除**。固定版本 Android ui-test 1.7.5 的字节码将 `performClick` 委托给 `performTouchInput`，对无操作节点注入物理点击合法。[缓存原始 API 证据](review-evidence/2026-10-01-navigation-acceptance/compose-click-primary-evidence.txt)避免将它列为误报。

## 验收矩阵

| 标准 | 结论 | 已证明及未证明内容 |
| --- | --- | --- |
| AC-1 祖先导航/返回 | PARTIAL | 基本 ViewModel 转换通过；真实点击与根目录退出未运行 |
| AC-2 路径身份/过期请求拒绝 | FAIL | 路径段匹配通过；过期根请求改变新选中的资料库 |
| AC-3 长路径 / 200% 字号 / 面包屑触控目标 | NOT_RUN | 十段生成有单测；实际可达性/边界未测量 |
| AC-4 扫描稳定性/恢复 | FAIL / PARTIAL | 已复现空闲取消缺陷；真实扫描阶段与像素/滚动不变量未运行 |
| AC-5 行、名称、类型提示、Reader 交接 | NOT_RUN | 源码有最小尺寸 modifier 和资源；实际点击边界/换行/回调点击未测量 |
| AC-6 既有契约 | PARTIAL | 后选资料库回归通过；未重新执行完整存储取消/索引保留及目录滚动复位证据 |
| AC-7 完整门禁 | BLOCKED | 已执行直接聚焦 JVM 检查；沙箱内无法执行 Gradle/构建/lint/ktlint |

依据这些结果，NB-05 不能验收通过。先修复两个确认的状态缺陷，使页面套件确定且范围充分，再执行缺少的 UI 和完整门禁。本次纯验收任务未修复生产代码。

## 复验与保留证据

证据运行器使用冻结版本缓存编译器/依赖，以及已编译的 core/storage JAR 做链接。它重新编译生产源码和探针，执行三个既有测试类及独立测试类，断言失败时返回非零退出码。使用可自清理的 `/tmp` 目录，不修改应用源码。请求协议变更后，应调整探针调用点，但保留过期来源场景。

```bash
python3 docs/plans/review-evidence/2026-10-01-navigation-acceptance/run_navigation_checks.py   --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

然后在允许 Gradle/ADB socket 的环境中执行项目 JVM/lint/ktlint 和双 debug 构建门禁，再对两个发行版运行修正后的页面套件：

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
./gradlew :app:connectedEinkDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
```

初次聚焦 Gradle 尝试使用 JDK 17、隔离 `GRADLE_USER_HOME`、`--offline --no-daemon`、进程内 Kotlin、2 GiB 堆和一个 worker；任何任务/测试开始前就失败。本报告不宣称存在成功的 Gradle 命令。

本任务仅创建这份双语独立报告及其保留证据目录。原交付/计划/任务/审查记录、常规 app 测试和产品代码均未修改。有价值证据有意保留在 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-navigation-acceptance/`，包括两个复现探针、复验运行器、原始结果、构建受阻错误、缓存 API 证据及源码/依赖哈希。记录结果后已删除 `/tmp/leaflet-navigation-acceptance.G9Ad29` 下的临时源码/缓存副本和编译产物，共享 ADB 启动日志只删除了本轮 PID 20796 的片段；其 15 个既有片段属于其他会话，保留在 `/private/var/folders/kg/sjpsnph952bczrr6s_t90c3m0000gn/T/adb.501.log`。已实际执行保留的复验运行器，再次复现 18 项既有测试通过、两项验收断言失败（[输出](review-evidence/2026-10-01-navigation-acceptance/recheck-runner.txt)），退出码为 1。未启动模拟器、未提交代码。
