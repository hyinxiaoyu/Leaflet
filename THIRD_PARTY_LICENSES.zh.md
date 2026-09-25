# 第三方许可证（T02）

[English](THIRD_PARTY_LICENSES.md) | 中文

项目许可证：**待定，需由项目所有者决定。** T02 未添加 `LICENSE` 文件，Leaflet 项目本身目前未声明开源许可证。

下表列出 T02 构建实际引入的依赖、版本和许可证（2026-09-17 根据上游 POM/仓库核实）。T02 尚未引入 Markwon（T06 引入），也未引用 Onyx SDK。

## 构建与工具链（不打包进 APK）

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| com.android.tools.build:gradle (AGP) | 8.13.2 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-gradle-plugin (+ Compose 编译器插件) | 2.0.21 | Apache-2.0 |
| com.google.devtools.ksp（声明供 T04 Room 使用；T02 未生成代码） | 2.0.21-1.0.27 | Apache-2.0 |
| org.jlleitschuh.gradle:ktlint-gradle（仅格式检查） | 12.1.2 | MIT（插件）/ MIT（ktlint 引擎，com.pinterest.ktlint） |

## 随应用打包或编译使用

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| androidx.annotation:annotation | 1.9.1 | Apache-2.0 |
| androidx.activity:activity-compose | 1.9.3 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose (+ runtime-ktx) | 2.8.7 | Apache-2.0 |
| androidx.navigation:navigation-compose | 2.7.7 | Apache-2.0 |
| androidx.compose:compose-bom（通过 BOM 使用 UI/runtime/Material3） | 2024.10.01 | Apache-2.0 |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core / -android | 1.10.2 | Apache-2.0 |

这四个 AndroidX 版本与 T00 提案不同（activity 1.13.0 / lifecycle 2.11.0 / navigation 2.10.1 / BOM 2026.09.00）。提案组合无法在冻结的 AGP 8.13.2 + compileSdk 35 下构建，因为传递依赖要求 SDK 36 + AGP 9.1.0。错误证据和批准的选项 A 降级见 `docs/development/build.md`。

## 仅用于测试

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| junit:junit | 4.13.2 | EPL-1.0 |
| androidx.test:runner（仅 instrumented） | 1.6.2 | Apache-2.0 |
| androidx.test.ext:junit（仅 instrumented） | 1.1.5 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.10.2 | Apache-2.0 |
| androidx.room:room-compiler（KSP，仅构建期，不打包） | 2.8.5 | Apache-2.0 |
| androidx.compose.ui:ui-test-junit4（R2-07，仅 instrumented） | 1.7.5 | Apache-2.0 |
| androidx.compose.ui:ui-test-manifest（R2-07，instrumented + debug；不进入 release） | 1.7.5 | Apache-2.0 |

已解析的随包传递依赖（来自 `fullReleaseRuntimeClasspath`）：

| 组件族 | 许可证 |
| --- | --- |
| androidx.emoji2 / startup / profileinstaller / lifecycle / savedstate / sqlite（含部分 room-guava） | Apache-2.0 |

## T04 新增使用且版本冻结（T02 未使用）

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| androidx.room:room-runtime | 2.8.5 | Apache-2.0 |
| androidx.room:room-ktx | 2.8.5 | Apache-2.0 |

## T06 新增且版本冻结（依赖审计日期：2026-09-18）

Markwon 依赖链（ADR-0004、`docs/research/markdown-renderers.md` §4）采用单一接缝：所有直接 `io.noties.markwon.*` 调用都位于 `core/markdown/.../MarkdownRenderer.kt`。NORMAL TextView 路径选择 `TablePlugin`。`recycler-table` 为 T09 `TableEntry` 演进保留，但绝不与 `TablePlugin` 混用（其 KDoc 明确禁止）。不使用 image-glide/image-coil/image-picasso、okhttp、WebView 或 Compose Markdown。传递依赖不覆盖：`com.atlassian.commonmark` 0.13.0（BSD 2-Clause）；`androidx.recyclerview` 解析为 1.2.1（Apache-2.0；Markwon 请求 1.0.0）。

| 组件 | 版本 | 许可证 |
| --- | --- | --- |
| io.noties.markwon:core | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-strikethrough | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-tables | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-tasklist | 4.6.2 | Apache-2.0 |
| io.noties.markwon:recycler | 4.6.2 | Apache-2.0 |
| io.noties.markwon:recycler-table | 4.6.2 | Apache-2.0 |
| com.atlassian.commonmark:commonmark（Markwon 传递依赖） | 0.13.0 | BSD 2-Clause |
| com.atlassian.commonmark:commonmark-ext-gfm-tables（传递依赖） | 0.13.0 | BSD 2-Clause |
| com.atlassian.commonmark:commonmark-ext-gfm-strikethrough（传递依赖） | 0.13.0 | BSD 2-Clause |
| androidx.recyclerview:recyclerview（覆盖 Markwon 1.0.0） | 1.2.1 | Apache-2.0 |

Debug APK 体积：Full 约 27.21 MB / E-Ink 约 27.21 MB（2026-09-18 本地构建，T06、T07 前）。Release 基线由 T13 负责，见 `docs/performance/apk-size.md`。
