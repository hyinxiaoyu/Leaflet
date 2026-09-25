# 构建

[English](build.md) | 中文

### 环境（工具链已冻结；库版本调整依据见下文）

- Gradle daemon 必须使用 JDK 17（AGP 8.13.x）。仓库未设置 `org.gradle.java.home`（R2-00：机器专属路径不可移植，已移除旧 macOS 绝对路径）。运行 Gradle 前，将 `JAVA_HOME` 指向本机 JDK 17，例如 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`；也可在用户本地 `~/.gradle/gradle.properties` 设置 `org.gradle.java.home`，不要提交该设置。模块编译另由 `kotlin jvmToolchain(17)` 固定。CI（`ubuntu-latest`、Temurin 17）同样通过 `JAVA_HOME` 选择 JDK。
- Android SDK 需安装 `platforms;android-35` 和 `build-tools;35.0.0/36.0.0`，可通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 环境变量或未提交的 `local.properties` 指定。
- 使用 Gradle Wrapper 8.13（`./gradlew`）、AGP 8.13.2、Kotlin 2.0.21。
- 可以存在宿主机 `~/.gradle/init.gradle` 仓库注入，但构建不依赖它：`settings.gradle.kts` 使用 `PREFER_SETTINGS`，依赖优先从 `google()` / `mavenCentral()` 解析。

以下命令在仓库根目录执行，并于 2026-09-17 验证通过：

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullRelease :app:assembleEinkRelease
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
```

APK 输出到 `app/build/outputs/apk/<full|eink>/<debug|release>/`。Debug 构建会在 flavor applicationId 后追加 `.debug`（Full Debug 为 `io.leaflet.reader.debug`，E-Ink Debug 为 `io.leaflet.reader.eink.debug`）。

### T02 版本证据（四个库为何与 T00 提案不同）

T00 提案中的 `activity-compose 1.13.0`、`lifecycle 2.11.0`、`navigation-compose 2.10.1` 和 `compose-bom 2026.09.00`，在冻结的 AGP 8.13.2 + compileSdk 35 下无法通过 `:app:assembleFullDebug`：其传递依赖（`core-ktx 1.18.0`、Compose `1.12.x`、`navigationevent 1.1.2`）至少要求 compileSdk 36 和 AGP 9.1.0（Gradle 错误文本，2026-09-17）。按 Main 决策（选项 A：工具链保持冻结），T02 采用可在 SDK 35 下成功构建的最新组合：

| 组件 | T02 版本 | T00 提案 |
| --- | --- | --- |
| androidx.activity:activity-compose | 1.9.3 | 1.13.0 |
| androidx.lifecycle（viewmodel-compose/runtime） | 2.8.7 | 2.11.0 |
| androidx.navigation:navigation-compose | 2.7.7 | 2.10.1 |
| androidx.compose:compose-bom | 2024.10.01 | 2026.09.00 |

保持不变：AGP 8.13.2、Gradle 8.13、Kotlin 2.0.21、KSP 2.0.21-1.0.27、compileSdk/targetSdk 35、minSdk 29、Room 2.8.5（为 T04 冻结，T02 尚未使用）、datastore-preferences 1.2.1、coroutines 1.10.2、junit4 4.13.2。为 `:core:eink` 中的 `@ColorInt` 增加了最小依赖 `androidx.annotation:annotation 1.9.1`（Apache-2.0，已记录在 `THIRD_PARTY_LICENSES.md`）。
