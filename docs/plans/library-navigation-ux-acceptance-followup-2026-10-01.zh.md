# 资料库导航体验 — 验收跟进与修复（2026-10-01）

[English](library-navigation-ux-acceptance-followup-2026-10-01.md) | 中文

编号：`NAV-FOLLOWUP-20261001`
结论：**聚焦修复已验证；完整页面验收仍待执行。**
授权：用户要求验收，检查失败时直接实现修复。
此前记录：[NAV-ACC-20261001](library-navigation-ux-acceptance-2026-10-01.md)，保留为历史失败快照。
当前证据：[结果与源码哈希](review-evidence/2026-10-01-navigation-followup/results.json)。

## 复验与范围

开始时，工作区已包含 `AncestorRequest` 协议以及空闲、非活动、终态取消保护。重新直接编译并执行后，24 项既有导航、取消、选择回归及两项独立验收探针均通过。这些生产 ViewModel 改动已保留，本次不将其宣称为新编写的修复。

后续验收追踪了实际页面到 ViewModel 的回调，修复失败的页面绑定并补充回归覆盖。未引入依赖、数据库、存储契约、扫描算法或无关阅读器变更。

## 已确认残余缺陷与修复

`LibraryBrowseScreen` 使用 `val state by collectAsState()`。其面包屑回调在排队回调执行时读取 `state.libraryId`、`state.prefix` 来构造 `AncestorRequest`。委托读取会返回最新状态，因此即使 `openAncestor` 正确拒绝显式携带过期 A 身份的请求，A 的点击仍可能被重新标为资料库 B。

使用会更新的 `State` 和真实 ViewModel，调用全新编译的生产回调。修复前，B 从 `Docs/Notes` 移到根目录，断言失败。执行对象是真实编译器生成的回调，不是测试专用镜像实现；不代表 Android 视图或指针投递测试。[修复前输出](review-evidence/2026-10-01-navigation-followup/callback-before.txt)。

页面现在在渲染时捕获普通 `renderedLibraryId` 和 `renderedPrefix` 值，用该前缀生成分段，并在回调中捕获这些不可变字符串。Full、Eink 编译回调均保留 A 的来源，B 状态不变。[Full 输出](review-evidence/2026-10-01-navigation-followup/full-callback-after.txt)、[Eink 输出](review-evidence/2026-10-01-navigation-followup/eink-callback-after.txt)。

页面测试的类型提示选择器也改为未合并语义树 (unmerged semantics tree)：可点击行会合并子节点语义，携带描述的合并行并不是自己的带标签祖先。[固定版本 Foundation 1.7.5 原始证据](review-evidence/2026-10-01-navigation-followup/clickable-semantics-primary.txt)确认合并已启用。选择器仍限制在对应行内，没有弱化断言。

## 变更文件

| 文件 | 本次改动 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryScreens.kt` | 构造排队面包屑回调前捕获渲染时的资料库/前缀值 |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` | 未合并行内子节点查询；新增保留根点击后改变前缀的回归；页面方法共 12 项 |
| `CHANGELOG.md` / `.zh.md` | 描述用户可见的排队点击修正 |
| `docs/requirements/REQ-0012-library-navigation.md` / `.zh.md` | 追加局部验证证据，不把整体需求标为 DONE |
| 本报告配对文件及 `review-evidence/2026-10-01-navigation-followup/` | 新增结果和可复现探针，与旧验收/交付报告分开 |

新增页面回归保留真实根节点 `OnClick` 操作，改变目录后调用保留的操作，要求新状态不变。它已编译，但未在 Android 上执行。

## 实际验证

环境：macOS ARM64、JDK 17.0.9、Kotlin/Compose 编译器 2.0.21、冻结版本缓存依赖。使用两个发行版各自已解析的 lint-model 依赖/R jar，重新编译导航组件及 Theme 源码。其他 app/core jar 仅用于链接；不是完整应用构建或 APK 组装。

| 检查 | 结果 |
| --- | --- |
| 既有 JVM：Breadcrumb 8、Ancestor 11、Selection 2、Cancel 3 | PASS — 24/24 |
| 此前独立 ViewModel 探针 | PASS — 2/2；[合并输出](review-evidence/2026-10-01-navigation-followup/focused-jvm.txt) |
| 修复前生产 UI 回调 | FAIL — 1/1，B 被错误移到根目录 |
| 修复后生产 UI 回调 | PASS — Full 1/1、Eink 1/1；最终 JVM 执行共 28 条，27 个不同方法 |
| 两个发行版的导航组件与当前页面套件 | PASS — 四次直接 Kotlin/Compose 编译；[Full main](review-evidence/2026-10-01-navigation-followup/full-main-compile.txt)、[Full page](review-evidence/2026-10-01-navigation-followup/full-page-compile.txt)、[Eink main](review-evidence/2026-10-01-navigation-followup/eink-main-compile.txt)、[Eink page](review-evidence/2026-10-01-navigation-followup/eink-page-compile.txt) |
| 改动源码 ktlint 1.0.1 | PASS — 两个改动 Kotlin 文件，保留 app 的 Compose 函数命名例外；[输出](review-evidence/2026-10-01-navigation-followup/changed-ktlint.txt) |
| 标准 Gradle 测试/构建/lint/ktlint 门禁 | BLOCKED，任务尚未执行 — `FileLockContentionHandler` socket `Operation not permitted`；[输出](review-evidence/2026-10-01-navigation-followup/gradle-blocked.txt) |
| 12 项 Android 页面方法执行 | NOT_RUN — ADB 监听器 `Operation not permitted`；[输出](review-evidence/2026-10-01-navigation-followup/adb-blocked.txt) |
| E-Ink 实机 / TalkBack | NOT_RUN |

直接编译准备首次错误拆分了冒号分隔的 jar 列表，编译前已修正。独立格式工具准备阶段缺少日志依赖并选中了不兼容的 SLF4J API；解析缓存 CLI 依赖后，ktlint 返回退出码 0。这些准备失败未被当作产品失败或成功门禁。既有编译器弃用警告仍存在。

## 证据与完成边界

原来的两个 ViewModel 失败场景已验证通过，残余生产页面回调缺陷也已修复，并有 JVM 执行前后证据。历史失败报告未覆盖。NB-05 和完整 REQ-0012 验收仍等待实际页面测量、全部项目门禁及独立硬件/无障碍证据；仅编译不能确认行尺寸、扫描视口稳定性或手势行为。

保留的可自清理运行器支持再次执行普通及独立 JVM 检查：

```bash
python3 docs/plans/review-evidence/2026-10-01-navigation-followup/run_navigation_checks.py   --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

[编译回调探针](review-evidence/2026-10-01-navigation-followup/CompiledBreadcrumbCallbackProbe.kt)根据参数形状定位编译器生成回调，通过反射调用以避免依赖 Android 宿主。反射只在保留诊断材料中使用，不进入产品代码或常规 app 测试。探针需要重新编译的导航组件，不要求编译器私有方法名固定。

环境允许 socket 后，执行完整项目门禁及每个发行版的页面套件：

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
./gradlew :app:connectedEinkDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
```

临时编译输出、缓存依赖、格式配置、源码备份及日志仅创建在 `/tmp/leaflet-navigation-followup.2xdLrQ`，保留证据后已删除。JVM 运行器清理了自身 `/tmp` 工作目录。ADB 尝试使用本任务 TMPDIR，没有追加共享系统日志。有价值证据有意保留在 `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-navigation-followup/`。不宣称 APK、截图、发布或提交。
