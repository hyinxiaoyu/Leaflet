# Third-Party Licenses (T02)

English | [中文](THIRD_PARTY_LICENSES.zh.md)

Project license: **TBD — pending project owner decision.** T02 did not add a `LICENSE` file and Leaflet does not claim an open-source license.

Every dependency actually introduced by the T02 build is listed with version/license, verified against upstream POM/repository on 2026-09-17. Markwon is not introduced until T06. No Onyx SDK is referenced.

## Build / Toolchain (not shipped in APK)

| Artifact | Version | License |
| --- | --- | --- |
| com.android.tools.build:gradle (AGP) | 8.13.2 | Apache-2.0 |
| org.jetbrains.kotlin:kotlin-gradle-plugin (+ Compose compiler plugin) | 2.0.21 | Apache-2.0 |
| com.google.devtools.ksp (declared for T04 Room use; no code generated in T02) | 2.0.21-1.0.27 | Apache-2.0 |
| org.jlleitschuh.gradle:ktlint-gradle (format check only) | 12.1.2 | MIT (plugin) / MIT (ktlint engine, com.pinterest.ktlint) |

## Shipped / Compiled In

| Artifact | Version | License |
| --- | --- | --- |
| androidx.annotation:annotation | 1.9.1 | Apache-2.0 |
| androidx.activity:activity-compose | 1.9.3 | Apache-2.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose (+ runtime-ktx) | 2.8.7 | Apache-2.0 |
| androidx.navigation:navigation-compose | 2.7.7 | Apache-2.0 |
| androidx.compose:compose-bom (UI/runtime/Material3 via BOM) | 2024.10.01 | Apache-2.0 |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core / -android | 1.10.2 | Apache-2.0 |

These four AndroidX versions differ from the T00 proposal (activity 1.13.0 / lifecycle 2.11.0 / navigation 2.10.1 / BOM 2026.09.00). The proposed set cannot build with frozen AGP 8.13.2 + compileSdk 35 because transitives require SDK 36 + AGP 9.1.0. Error evidence and the approved option-A downgrade are in `docs/development/build.md`.

## Test Only

| Artifact | Version | License |
| --- | --- | --- |
| junit:junit | 4.13.2 | EPL-1.0 |
| androidx.test:runner (instrumented only) | 1.6.2 | Apache-2.0 |
| androidx.test.ext:junit (instrumented only) | 1.1.5 | Apache-2.0 |
| org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.10.2 | Apache-2.0 |
| androidx.room:room-compiler (KSP, build-only, not shipped) | 2.8.5 | Apache-2.0 |
| androidx.room:room-testing (LT-3 migration test, instrumented only) | 2.8.5 | Apache-2.0 |
| androidx.compose.ui:ui-test-junit4 (R2-07, instrumented only) | 1.7.5 | Apache-2.0 |
| androidx.compose.ui:ui-test-manifest (R2-07, instrumented + debug; not in release) | 1.7.5 | Apache-2.0 |

Resolved shipped transitives (from `fullReleaseRuntimeClasspath`):

| Artifact family | License |
| --- | --- |
| androidx.emoji2 / startup / profileinstaller / lifecycle / savedstate / sqlite (+ room-guava subset) | Apache-2.0 |

## Version-frozen, Newly Used in T04 (not used in T02)

| Artifact | Version | License |
| --- | --- | --- |
| androidx.room:room-runtime | 2.8.5 | Apache-2.0 |
| androidx.room:room-ktx | 2.8.5 | Apache-2.0 |

## Version-frozen, Newly Used in T06 (dependency audit 2026-09-18)

Markwon chain (ADR-0004; `docs/research/markdown-renderers.md` §4) uses a single seam: direct `io.noties.markwon.*` calls live in `core/markdown/.../MarkdownRenderer.kt`. `TablePlugin` is chosen for the NORMAL TextView path. `recycler-table` is retained for T09 `TableEntry` evolution, but `TableEntryPlugin` is never combined with `TablePlugin` (its KDoc prohibits this). No image-glide/image-coil/image-picasso, okhttp, WebView, or Compose Markdown. Accept transitives without override: `com.atlassian.commonmark` 0.13.0 (BSD 2-Clause); `androidx.recyclerview` resolves to 1.2.1 (Apache-2.0; Markwon requests 1.0.0).

| Artifact | Version | License |
| --- | --- | --- |
| io.noties.markwon:core | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-strikethrough | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-tables | 4.6.2 | Apache-2.0 |
| io.noties.markwon:ext-tasklist | 4.6.2 | Apache-2.0 |
| io.noties.markwon:recycler | 4.6.2 | Apache-2.0 |
| io.noties.markwon:recycler-table | 4.6.2 | Apache-2.0 |
| com.atlassian.commonmark:commonmark (transitive via Markwon) | 0.13.0 | BSD 2-Clause |
| com.atlassian.commonmark:commonmark-ext-gfm-tables (transitive) | 0.13.0 | BSD 2-Clause |
| com.atlassian.commonmark:commonmark-ext-gfm-strikethrough (transitive) | 0.13.0 | BSD 2-Clause |
| androidx.recyclerview:recyclerview (catalog override of Markwon 1.0.0) | 1.2.1 | Apache-2.0 |

Debug APK size: Full ≈27.21 MB / E-Ink ≈27.21 MB (2026-09-18 local build, T06 before T07). T13 owns the release baseline in `docs/performance/apk-size.md`.
