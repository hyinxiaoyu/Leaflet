# 测试

[English](testing.md) | 中文

测试分为三个层级，不能相互替代：

1. **JVM 单元测试**（`./gradlew test`）：验证纯 Kotlin 契约，包括 ReadingPosition 版本管理与锚点回退、E-Ink off-list、动效策略耦合、Generic 控制器重绘节奏、扫描规则，以及解析/标题/编码矩阵。它们在 CI 运行。当前基线：654 条 flavor 测试记录，按类名和测试名去重后 296 项，失败 0 项（2026-09-29，R3-08 收尾门禁）。R2-08 快照为 494 条 / 240 项。
2. **设备测试（instrumented tests）**：`:core:storage:connectedDebugAndroidTest`（Room/SAF/Scanner/迁移，17 项：16 项 DAO/SAF/扫描器 + `DirectoryMigrationTest` 的 1 → 2 schema 迁移）；`:core:markdown:connectedDebugAndroidTest`（10 MB 大文件验证，5 项）；`:app:connected{Full,Eink}DebugAndroidTest`（生产页面套件 + R3 交互/资源/大文档套件，每个 flavor 85 项，其中 3 项两阶段持久化用例在缺少种子阶段时按假设跳过；2026-09-29：Full 套件全绿，Eink 的 75 项套件加新增 27 项均通过；R2-07 快照为每 flavor 12 项）。页面套件由 `ReaderEntryTest` 3 项、`ReaderRecoveryTest` 5 项、`ReaderDisplayTest` 3 项和 `ReaderImageTest` 1 项组成；通过 `createComposeRule` 在伪存储边界上组合真实 `ReaderScreen`、session 和 Markwon parser。当前模拟器镜像会阻止 TEST 进程的宿主 Activity 启动，因此测试不会启动 Activity；见 `PageHarness` 和 `app/build.gradle.kts`。这类测试需要设备/模拟器，CI 不运行。连接设备的 App 测试若实际为空则不能证明任何内容；不得把 `NO-SOURCE` 输出记为 UI 覆盖。
3. **E-Ink 设备矩阵**（`eink-test-matrix.md`、T13）：残影与刷新结论只能来自真实 E-Ink 硬件。缺少设备的行必须保持 `NOT_RUN/BLOCKED`。

格式检查：`ktlintCheck` 覆盖 `:app` 和全部 `:core:*` 模块（R2-00 前只覆盖 app）。Compose 大写函数名通过 `app/build.gradle.kts` 的 `additionalEditorconfig` 豁免。
