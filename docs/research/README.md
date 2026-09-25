# T00 — Technical Evidence and Feasibility Audit

English | [中文](README.zh.md)

Status: IN_PROGRESS (toolchain partly measured; the other three reports were then being drafted)
Date: 2026-09-17
Source requirements: `docs/archive/init.md` §§46, 8–9, 18–19; task T00 is in `docs/plans/M0-M1-implementation-plan.md`.

## Files in This Directory

| File | Content | Status at this snapshot |
| --- | --- | --- |
| `README.md` (this file) | Measured toolchain, pinned-version proposal, risk list | Measured; awaiting T02 build verification |
| `markdown-renderers.md` | Markwon / WebView+marked / Compose Markdown / custom parser comparison | Drafting |
| `eink-readers.md` | Pilcrow / mdreader-android / Markor / KOReader comparison | Drafting |
| `onyx-epd.md` | OnyxAndroidDemo EPD API and decision not to use SDK in MVP | Drafting |

## 1. Local Toolchain Measurements (2026-09-17)

| Item | Measured result |
| --- | --- |
| OS / architecture | macOS 15.6.1 arm64 (Darwin 24.6.0) |
| Default `JAVA_HOME` | JDK 25.0.1 at `/Library/Java/JavaVirtualMachines/jdk-25.0.1.jdk`; **not for Gradle daemon**, which requires JDK 17 |
| Available JDK 17 | `17.0.9 LTS` at `/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home`; `java -version` verified; use for daemon |
| System Gradle | 8.4 (sdkman, Kotlin 1.9.10); host information only; project uses Wrapper |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Both `/opt/homebrew/share/android-commandlinetools` |
| command-line tools | 22.0 (`source.properties`) |
| build-tools | 36.0.0 only |
| platforms | android-35, android-36, android-37.0 |
| platform-tools / adb | 37.0.1 |
| emulator / system image | emulator 37.1.11; `system-images;android-35;google_apis;arm64-v8a` revision 9 |
| NDK | 29.0.14206865; recorded only, MVP has no native code |
| git | **Directory was not a Git repository at measurement time**, verified by `git status`; T02 was expected to initialize it |

## 2. Pinned-Version Proposal

T02 later verified a build; four AndroidX proposals differ from what was implemented. Maven facts below were measured directly from `maven-metadata.xml`/POM on 2026-09-17:

- Google Maven AGP stable versions included 8.13.2, 9.0.1, 9.1.1, 9.2.1, 9.3.2, and 9.4.0 (`lastUpdated 20260910`); 9.5.0-alpha05 is preview and excluded.
- Latest stable Room runtime was 2.8.5 (`lastUpdated 20260909`); DataStore preferences was 1.2.1 (1.3.0-alpha11 was preview).
- Maven Central Markwon core was 4.6.2 (`latest/release`, last updated 2021-02-08), Apache-2.0, transitively using `com.atlassian.commonmark:commonmark:0.13.0` and `androidx.annotation:annotation:1.1.0`.
- Measured stable proposals: activity-compose 1.13.0; lifecycle viewmodel-ktx 2.11.0; navigation-compose 2.10.1; Compose BOM 2026.09.00. Alpha previews were excluded.

| Item | Proposal | Basis |
| --- | --- | --- |
| Gradle daemon JDK | 17 (`org.gradle.java.home` pointing at local JDK) | Android JDK guidance: AGP 8.x/9.x requires JDK 17 to run |
| AGP | `8.13.2` | Stable Google Maven measurement; mature 8.x line; avoid 9.4.0 as too new for M0/M1 unless T02 evidence requires reassessment |
| Gradle Wrapper | `8.13` | Pair with AGP 8.13; verify actual sync in T02 |
| Kotlin | `2.0.21` | Compose compiler plugin shares Kotlin version since Kotlin 2.0; KSP `2.0.21-1.0.27` paired |
| compileSdk / targetSdk | `35` | Installed API 35 and AGP ≥8.6 support; avoid unverified AGP/new API interaction with 36/37 |
| minSdk | `29` (Android 10) | Covers Leaf generation and later Go/Palma; recheck Room/activity minimums in T02 |
| Room / DataStore | `2.8.5` / `1.2.1` | Latest stable versions measured above |
| activity-compose | proposal `1.13.0` → T02 `1.9.3` | Proposal transitives need compileSdk 36 + AGP 9.1.0; downgrade builds with frozen toolchain |
| lifecycle | proposal `2.11.0` → T02 `2.8.7` | Proposal conflicts with frozen AGP 8.13.2 + SDK 35; downgrade verified |
| navigation-compose | proposal `2.10.1` → T02 `2.7.7` | Same toolchain conflict; downgrade verified |
| Compose BOM | proposal `2026.09.00` → T02 `2024.10.01` | Same toolchain conflict; downgrade verified |
| Markwon | `core/tables/tasklist:4.6.2` | Maven/POM measurement, Apache-2.0; avoid Glide/Coil for size/E-Ink; T10 uses platform decode + viewport sampling |
| Renderer architecture | Markwon parse AST → project block splitting → RecyclerView/View per block; plain-text fallback for giant blocks | See `markdown-renderers.md`; no full-document TextView or custom parser |

## 3. Target-Device Baseline (Literature, Not Measurement)

BOOX devices generally lag mainstream phones by two to four Android generations and major OS upgrades are uncommon: Leaf generation API 29; Go 6 / Leaf 2/3 / Palma generation API 30; Go 7 series API 32/33; 2024–2025 flagships API 33, some API 35. Proposal: minSdk 29 + targetSdk 35 is a reasonable compatibility range. Ghosting/refresh conclusions require physical E-Ink; the available Android 35 ARM64 emulator is only for layout/logic.

## 4. Risks

1. Markwon is stagnant: no release after 4.6.2 (2021-02-08), and visible master head was 2021-03. Mitigate with limited core/table/tasklist API, isolate behind project block rendering, downgrade giant blocks; a future parser swap needs a new ADR without changing Reader contract.
2. AGP 8.13.2 is conservative, not newest. Keep M0/M1 stable; upgrade only with concrete benefit.
3. No physical E-Ink device. Matrix rows must remain NOT_RUN/BLOCKED in T13; never claim pass.
4. No Git repository at measurement time. T02 initializes it and makes logical commits; database changes include migration tests.
5. Default JDK 25. Docs/CI must not treat default `JAVA_HOME` as build JDK; select JDK 17 explicitly.

## 5. Open Items Before T01

Close or retain as Proposed: final minSdk 29 compatibility with Room 2.8.5 / proposed activity-compose 1.13.0 (T02 lint/sync); Markwon table/task/relative-resource/giant-block details (`markdown-renderers.md`); Onyx distribution/license (`onyx-epd.md`); performance thresholds (only after T03 samples; do not fabricate).
