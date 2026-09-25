# Onyx EPD Research Report

English | [中文](onyx-epd.zh.md)

> Research date: 2026-09-17 UTC. T00 read-only evidence; no reference source copied. API names are factual interface references. Version claims were measured from official docs/demo source/GitHub API/mvnrepository; uncertain items are labeled unverified.

## 0. Source Identity

- Reference: `onyx-intl/OnyxAndroidDemo` — https://github.com/onyx-intl/OnyxAndroidDemo
- Repository LICENSE: Apache-2.0, copyright ONYX INTERNATIONAL INC — https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/LICENSE
- GitHub API on 2026-09-17 returned **no tags**; this report anchors commits, not tags.
- Measured commits: `ec512c79c0d39c1e294214d36580ce4c914bbea6` (2026-09-10, license owner update); `f1a346d8083653b40b9f5e61c633f91511dc23c4` (2026-09-09, SDK version update PR #142); `60f35871a4cc5bcbba74216cc6c1fd92119cbecf` (2026-09-09, include arm64-v8a to fix ABI install failure).
- Official docs measured raw on 2026-09-17: EPD screen update, EpdController, EpdDeviceManager, UpdateMode/UpdateOption, Base SDK, E-Ink UX, FrontLightController, EPD-Touch, Onyx Pen SDK, DeviceEnvironment under `https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/`.
- Demo source paths include the `app/OnyxAndroidDemo/` segment (README's short paths are stale): `EpdDemoActivity.java`, `RefreshModeDemoActivity.java`, app `build.gradle`, root `build.gradle`.

## 1. SDK Distribution and Versions (measured)

- Repository README declares `com.onyx.android.sdk:onyxsdk-device:1.1.11` and `onyxsdk-pen:1.2.1`, plus JitPack for DBFlow/scribble transitives and `repo.boox.com`.
- Demo build actually uses device `1.3.5.3` and pen `1.5.4.4`. README and build disagree; prefer project file/changelog and treat README as stale.
- Root build declares `http://repo.boox.com/repository/proxy-public/` and `http://repo.boox.com/repository/maven-public/` (**plain HTTP**, see §3), plus `google()`, `mavenCentral()`, and JitPack.
- Base SDK docs list `onyxsdk-base:1.4.3.7`; EpdDeviceManager exists since 1.2.3; touch-region API requires base ≥1.4.3.
- On 2026-09-17, mvnrepository listed device 1.3.5.3 and pen 1.5.4.4 (2026-09-07): https://mvnrepository.com/artifact/com.onyx.android.sdk/onyxsdk-device and `/onyxsdk-pen`.
- **Unverified:** whether these artifacts are hosted on Maven Central. Direct search.maven.org lookup returned 403. “Indexed Repositories” is not proof of Central hosting. A future Leaflet integration must verify HTTPS/availability of the private repository; do not claim Central availability in an ADR.

## 2. API Findings (official docs or source measured)

### 2.1 `EpdController` (low-level primitives)

Official docs describe this as low-level and recommend app-layer `EpdDeviceManager`, not calls scattered throughout the app. Observed names:

- `setViewDefaultUpdateMode(view, UpdateMode)`: default mode for a View; docs use GU for ordinary partial refresh and REGAL for text-page optimized refresh.
- `invalidate(view, UpdateMode.GC)`: full-screen refresh to clear ghosting.
- `repaintEveryThing(UpdateMode.GC)`: redraw entire screen; used by EpdDemoActivity.
- `applyAppScopeUpdate / clearAppScopeUpdate`: app-level refresh-domain control; demo switches among ANIMATION_X, ANIMATION_QUALITY, DU_QUALITY, None.
- `applyApplicationFastMode(app, enter, clear)`: documented Fast Mode entry/exit; **not verified** in device SDK 1.3.5.3. Demo uses `EpdDeviceManager.enterAnimationUpdate / exitAnimationUpdate`; integration must follow packaged API.
- `setWebViewContrastOptimize(webView, enabled)`: prevent WebView from entering A2 mode (base 1.4.3.4+); recorded only, Leaflet has no WebView.
- Touch-region APIs (base ≥1.4.3): `isCTPDisableRegion`, `setAppCTPDisableRegion` overloads for `Rect[]`/`int[]` including exclusion regions, and `appResetCTPDisableRegion`. Not needed by MVP.

### 2.2 `EpdDeviceManager` (recommended app wrapper)

Docs describe a unified wrapper across platforms such as RK3026/IMX6, automatically choosing normal/REGAL partial refresh. Observed methods: `setGcInterval(n)` (demo uses 5), `applyWithGCInterval(view,isTextPage)`, `applyWithGCIntervalWithoutRegal(view)`, `applyWithGCIntervalWitRegal(view,boolean)` (source spelling is `WitRegal`), `applyGCUpdate(view)`, and paired `enterAnimationUpdate(boolean)` / `exitAnimationUpdate(boolean)`.

Reusable idea: count N partial-refresh intents and insert a full refresh intent. Leaflet's Generic controller can simulate this as **system redraw cadence only**, never hardware waveform control.

### 2.3 `UpdateMode` (all entries from official table; no additions/deletions)

`DU` (B/W), `DU_QUALITY` (dithered DU), `GU` (16-gray partial), `GU_FAST` (deprecated), `GC` (16-gray full), `GC_4` (deprecated), `ANIMATION` (faster B/W than DU), `ANIMATION_QUALITY` (dithered animation), `REGAL` (optimized 16-gray text-page partial), `REGAL_D` (docs say currently equivalent to REGAL).

Leaflet intent analogy only: page/text reflow ≈ REGAL intent, ghost-clearing redraw ≈ GC intent, scroll/drag ≈ FAST/animation intent. **Intent names may be borrowed; capability claims may not.** MVP does not call the SDK.

### 2.4 `UpdateOption` / Fast Mode

Demo reads/writes app-level modes through `Device.currentDevice().get/setAppScopeRefreshMode`: NORMAL (general text), FAST_QUALITY (slight ghosting, quick mixed browsing), REGAL (least ghosting, slight dark-background flicker; suited to light background), FAST (more ghosting, scrolling), FAST_X (large detail loss, web/video).

Fast Mode trades grayscale for speed and requires paired entry/exit plus a normal refresh after exit or UI can stay low quality. Demo uses separate buttons; future wrapper would need try/finally or lifecycle pairing. MVP does not use it.

### 2.5 Device Detection

Measured: `Device.currentDevice()` is a common entry point; `DeviceUtils.setFullScreenOnResume`, `DeviceEnvironment.getRemovableSDCardDirectory`, and `FrontLightFactory` model selection based on light capability. The capability-query-before-inflating-UI pattern may inform AppCapabilities.

**Unverified/inference:** branching on `Build.MODEL/DEVICE` is common but no official recommended model list was found. Leaflet must not branch on exact BOOX models; only capability-based branching is allowed. 6/7/10.3-inch sizes belong to responsive test matrix, not model-specific logic.

### 2.6 Explicitly Out of Leaflet Scope

- Pen/Scribble (`onyxsdk-pen`, TouchHelper, BrushRender, moveTo/lineTo, pressure): read-only reader; no annotations.
- Front light (requires `WRITE_SETTINGS`): not MVP. Any future feature must use system settings semantics and explicit permission, not silently alter brightness.
- Dictionary/OTA/screensaver/settings-launch Misc APIs: out of scope.

## 3. APK Size, Play, ABI, and Native Impact

### Measured demo facts

- `abiFilters` includes `arm64-v8a` and `armeabi-v7a`; a 2026-09-09 commit fixed `INSTALL_FAILED_NO_MATCHING_ABIS` caused by missing arm64-v8a. If integrating, declare/test both ABIs.
- Packaging uses `pickFirst 'lib/*/libc++_shared.so'`, evidence of native `.so` collision (at least C++ runtime level).
- Demo enables multidex and includes DBFlow via JitPack, hiddenapibypass, multidex, constraintlayout, etc.; private repo + JitPack widen package and supply-chain surface.
- Demo compile/target SDK 30, AGP 3.6.4, minSdk 24 are old demo baselines, **not SDK requirements**. Leaflet uses T01 official compatibility evidence instead.
- Private Maven URLs use plain HTTP. A future integration must assess HTTPS mirror/proxy, repository availability, reproducible CI, and Android cleartext policy. Private repository is itself a supply-chain single point.

### Unverified (required before integration; not an MVP obligation)

APK byte delta, full `.so`/ABI inventory, Play compatibility/SDK Index/policy/target API, and front-light `WRITE_SETTINGS` merged-manifest impact remain unverified. T13 measures them if M5 integration is approved. MVP adds no SDK, so no permission effect now.

## 4. License

Demo repository itself is Apache-2.0 (LICENSE, ONYX INTERNATIONAL INC.). mvnrepository labels `onyxsdk-device`/`onyxsdk-pen` BSD 3-Clause, inconsistent with repository license. **The artifact's actual grant is unverified until its packaged LICENSE/NOTICE is checked.** Before M5, download artifacts, inspect included notices, and update `THIRD_PARTY_LICENSES`. Until then do not copy, modify, or distribute SDK code; this report records interface names/behavior only.

## 5. MVP Decision: No SDK Integration (effective T00→T12; do not revisit before M5)

M0–M1, including T12, uses only `NoOpEInkController` + `GenericEInkController`; refresh depends on system behavior.

Reasons:

1. Plain-HTTP private repository and transitives (DBFlow/JitPack/native/multidex) pollute dependency graph, conflicting with lean E-Ink and uncontaminated Full flavor.
2. ABI, package size, Play, and license all have unverified risk; no physical E-Ink device means waveform claims cannot be accepted.
3. MVP can achieve E-Ink behavior through framework policy: disable animation/smooth/overscroll/ripple/crossfade, viewport turns (5% default, 3–8% allowed), static/throttled loading, and a GC-like intent counter that occasionally requests system redraw. Ghosting strategy is “refresh less, redraw whole page, occasional full redraw,” not hardware waveform.
4. Architecture (AGENTS.md and archive §18): Reader never imports Onyx. EInkController exposes intent methods only (`onReaderEnter/Exit`, page/scroll start/end, partial/full refresh request), never `UpdateMode` types.

T12/UI/docs must not claim Generic/NoOp triggers REGAL/GU partial or GC full hardware refresh; do not expose UpdateOption, GC interval, or Fast Mode controls. In MVP, `requestFullRefresh` means request a system redraw. Ghosting conclusions require device matrix; missing device stays NOT_RUN/BLOCKED.

## 6. M5 Isolation Prerequisites (all required; create ADR-0007 first)

1. **Source isolation:** only E-Ink flavor source set and/or `:core:eink` Onyx package/module; zero Full references; Reader still depends on EInkController interface only.
2. **Dependency isolation:** Onyx coordinate only in E-Ink dependency block; prove `fullRelease` has no Onyx bytecode/native (T13 APK analysis).
3. **Pinned supply chain:** verify `onyxsdk-device` against packaged API; record HTTPS/proxy repository, reproducible CI, and every transitive (including whether DBFlow/JitPack is required).
4. **License closure:** inspect packaged LICENSE/NOTICE; update THIRD_PARTY_LICENSES and explain repository-license mismatch.
5. **ABI/size/permissions:** install both ABIs, record package size delta, review merged-manifest permissions, especially WRITE_SETTINGS-like.
6. **Behavior contract:** wrap primitives with Generic-equivalent intents; pair Fast Mode entry/exit; make GC interval configurable/conservative; non-BOOX falls back to Generic by capability detection, no model allowlist.
7. **Acceptance:** real devices in 6/7/10.3-inch categories report ghosting/flicker/turn results. On failure disable integration and return to Generic without blocking Full.

## 7. Follow-up

Track SDK/docs changes in `onyx-intl/OnyxAndroidDemo`; commit anchors are in §0. Keep ADR-0007 Proposed before M5; T01 must not mark it Accepted early.
