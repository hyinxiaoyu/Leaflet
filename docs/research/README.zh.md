# T00 — 技术证据与可行性审计

[English](README.md) | 中文

状态：IN_PROGRESS（工具链部分已实测；当时其余三份报告仍在编写）
日期：2026-09-17
需求来源：`docs/archive/init.md` §46、§8–9、§18–19；任务 T00 见 `docs/plans/M0-M1-implementation-plan.md`。

## 本目录文件

| 文件 | 内容 | 该快照中的状态 |
| --- | --- | --- |
| `README.md` | 工具链实测、固定版本提案、风险清单 | 已实测，待 T02 构建验证 |
| `markdown-renderers.md` | Markwon / WebView+marked / Compose Markdown / 自研 parser 对比 | 撰写中 |
| `eink-readers.md` | Pilcrow / mdreader-android / Markor / KOReader 对照 | 撰写中 |
| `onyx-epd.md` | OnyxAndroidDemo EPD API 与 MVP 不接 SDK 决定 | 撰写中 |

## 1. 本地工具链实测（2026-09-17）

| 项目 | 实测结果 |
| --- | --- |
| OS / 架构 | macOS 15.6.1 arm64（Darwin 24.6.0） |
| 默认 `JAVA_HOME` | JDK 25.0.1，路径 `/Library/Java/JavaVirtualMachines/jdk-25.0.1.jdk`；**Gradle daemon 不使用它**，AGP 要求 JDK 17 |
| 可用 JDK 17 | `17.0.9 LTS`，路径 `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`；`java -version` 已验证，daemon 使用此版本 |
| 系统 Gradle | 8.4（sdkman，Kotlin 1.9.10）；仅宿主信息，项目使用 Wrapper |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | 均为 `/opt/homebrew/share/android-commandlinetools` |
| command-line tools | 22.0（`source.properties`） |
| build-tools | 仅 36.0.0 |
| platforms | android-35、android-36、android-37.0 |
| platform-tools / adb | 37.0.1 |
| emulator / system image | emulator 37.1.11；`system-images;android-35;google_apis;arm64-v8a` revision 9 |
| NDK | 29.0.14206865；仅记录存在，MVP 不使用 native code |
| git | **测量时目录尚非 Git 仓库**，由 `git status` 验证；原计划 T02 初始化 |

## 2. 固定版本提案

T02 后续验证了构建；其中四项 AndroidX 实际版本与提案不同。以下 Maven 事实均于 2026-09-17 直接读取 `maven-metadata.xml` / POM：

- Google Maven 中 AGP 稳定版含 8.13.2、9.0.1、9.1.1、9.2.1、9.3.2、9.4.0（`lastUpdated 20260910`）；9.5.0-alpha05 为预览版，不采用。
- Room runtime 最新稳定版 2.8.5（`lastUpdated 20260909`）；DataStore preferences 最新稳定版 1.2.1（1.3.0-alpha11 为预览版）。
- Maven Central Markwon core 为 4.6.2（`latest/release`，最后更新 2021-02-08），Apache-2.0；传递依赖 `com.atlassian.commonmark:commonmark:0.13.0` 与 `androidx.annotation:annotation:1.1.0`。
- 实测提案稳定版：activity-compose 1.13.0、lifecycle viewmodel-ktx 2.11.0、navigation-compose 2.10.1、Compose BOM 2026.09.00；预览版不采用。

| 项目 | 提案 | 依据 |
| --- | --- | --- |
| Gradle daemon JDK | 17（`org.gradle.java.home` 指向本机 JDK） | Android JDK 指引：AGP 8.x/9.x 运行需 JDK 17 |
| AGP | `8.13.2` | Google Maven 稳定版；8.x 成熟线。9.4.0 较新，M0/M1 先求稳，T02 若受阻再评估 |
| Gradle Wrapper | `8.13` | 与 AGP 8.13 配套，T02 以实际 sync 验证 |
| Kotlin | `2.0.21` | Kotlin 2.0 起 Compose compiler 插件同 Kotlin 版本；KSP 配对 `2.0.21-1.0.27` |
| compileSdk / targetSdk | `35` | 本机安装 API 35，AGP ≥8.6 支持；避开 API 36/37 与 AGP 未验证组合 |
| minSdk | `29`（Android 10） | 覆盖 Leaf 与后续 Go/Palma；T02 复核 Room/activity 下限 |
| Room / DataStore | `2.8.5` / `1.2.1` | 上述 Google Maven 实测稳定版 |
| activity-compose | 提案 `1.13.0` → T02 `1.9.3` | 提案传递依赖需 SDK 36 + AGP 9.1.0；降级后冻结工具链构建通过 |
| lifecycle | 提案 `2.11.0` → T02 `2.8.7` | 与冻结 AGP 8.13.2 + SDK 35 冲突；降级通过 |
| navigation-compose | 提案 `2.10.1` → T02 `2.7.7` | 同上，降级通过 |
| Compose BOM | 提案 `2026.09.00` → T02 `2024.10.01` | 同上，降级通过 |
| Markwon | `core/tables/tasklist:4.6.2` | Maven/POM 实测、Apache-2.0；图片不引 Glide/Coil，T10 用平台解码与视口采样 |
| 渲染架构 | Markwon AST → 自有分块 → RecyclerView/View 逐块渲染；超大块降级纯文本 | 详见 `markdown-renderers.md`；不将整篇放入单 TextView，不自研 parser |

## 3. 目标设备基线（文献调研，非实测）

BOOX 设备通常比主流手机落后 2–4 代，出厂 Android 版本可能长期不变：Leaf 一代 API 29；Go 6 / Leaf 2/3 / Palma 一代 API 30；Go 7 系 API 32/33；2024–2025 旗舰多为 API 33，部分 API 35。minSdk 29 + targetSdk 35 是合理兼容范围。残影/刷新结论必须来自真实 E-Ink；android-35 ARM64 模拟器仅用于布局/逻辑验证。

## 4. 风险

1. Markwon 停滞：4.6.2（2021-02-08）后无 release，公开 master 最新可见提交为 2021-03。缓解：限制 core/table/tasklist API，自有分块隔离，大块降级；换 parser 需新 ADR 且保留 Reader 契约。
2. AGP 8.13.2 较保守而非最新；M0/M1 求稳，有明确收益再升级。
3. 无 E-Ink 实机：T13 矩阵必须标 NOT_RUN/BLOCKED，不得虚报通过。
4. 测量时尚无 Git 仓库；原计划 T02 建仓并按逻辑任务提交，数据库变更需迁移测试。
5. 默认 JDK 25：文档/CI 不得把它当构建 JDK，必须显式选 JDK 17。

## 5. T01 前未决事项

minSdk 29 与 Room 2.8.5 / 提案 activity-compose 1.13.0 的最终兼容性（T02 lint/sync）；Markwon 表格/任务列表/相对资源/大块细节（`markdown-renderers.md`）；Onyx 分发与许可证（`onyx-epd.md`）；性能阈值（需 T03 样本后测量，不得编造）。证据不足时保留 Proposed。
