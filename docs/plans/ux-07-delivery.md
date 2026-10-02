# UX-07 Delivery — Table Alignment, Image Sizing, and Copy Feedback

English | [中文](ux-07-delivery.zh.md)

Date: 2026-09-30
Package: UX-07 in [UI/UX Optimization Tasks](ui-ux-optimization-tasks.md), ISS-023/024/025
Requirements: [REQ-0007](../requirements/REQ-0007-typography-tables-code.md), [REQ-0009](../requirements/REQ-0009-local-resources-links.md)
Status: IMPLEMENTED — software acceptance passed; E-Ink hardware NOT_RUN.
Review: executor self-review; no independent agent review.

## Product decision and acceptance

The user's request authorizes continued product refinement. This iteration selects an existing P1 rendering task with confirmed defects: row-local table widths make columns drift, a decoded 16×16 icon expands to 840×840 physical pixels in the emulator, and code-copy feedback never clears. The existing READY requirements cover these paths; their acceptance clarifications are updated in both languages.

- Tables share widths across all styled cells, preserve empty slots, have 1 dp grid dividers and a 2 dp header rule, and retain horizontal reachability and positional search highlighting.
- Images remain centered at decoded physical size unless viewport width or 320 dp height requires proportional reduction. Device density is respected; tall/panoramic aspect ratios are not clamped.
- Copy retains the exact fence-free code body. Feedback occupies the existing single-line action button for two seconds after the latest tap, with no inserted feedback row; repeated taps restart the timer.

Stop condition: these acceptance paths, relevant existing page regressions, JVM tests, lint, ktlint, and both debug builds pass. Hardware refresh/ghosting is a separate unverified gate. No dependency, database, public API, editor, network service, or release change is included.

## Implementation and changed files

`TableGrid` uses Compose intrinsic measurement of actual styled content, then one fixed-size measurement per cell. All cells in a column share the maximum width and all cells in a row share the maximum height. Borders use `onSurface`. The existing `TableBlockEntry` still owns search-coordinate mapping and horizontal scrolling. `boundedImageDimensions` applies a scale no larger than one in physical pixels, while `ImageEntry` keeps the full-width decode container and converts the bitmap layout back to dp. A composition-scoped `LaunchedEffect` cancels/restarts copy-feedback timing.

```text
app/src/main/kotlin/io/leaflet/reader/ui/reader/
  ReaderScreen.kt       # bind grid, bounded centered bitmap, transient copy action
  TableGrid.kt          # shared widths/heights and grid dividers
  ImageDimensions.kt   # pure bounded physical-pixel sizing
app/src/test/kotlin/io/leaflet/reader/ui/reader/
  ImageDimensionsTest.kt
app/src/androidTest/kotlin/io/leaflet/reader/ui/
  ReaderRenderingLayoutTest.kt
  PageViews.kt          # native-heading visibility helper
  ReaderEntryTest.kt / ReaderDisplayTest.kt / ReaderImageTest.kt
docs/development/ux07-screenshots/  # eight before/after captures
```

The change also updates paired REQ-0007/0009 clarifications, reader architecture, the UX-07 task card, this delivery report, and CHANGELOG. ADR-0004/0005/0006 stay applicable; no architectural decision is superseded. Core AGENTS constraints are preserved: read-only originals, UI/ViewModel/storage ownership, offline image policy, shared reader across flavors, and no hardware-refresh promise. Both Full and Eink use the same implementation; both Standard and E-Ink runtime modes are tested. Concurrent changes to UX-06 overlays, reading defaults, settings, and their tests appeared during this session and are preserved; their behavior is owned by the separate code-review delivery. Final integration checks include that worktree.

## Verification

Environment: macOS ARM64, JDK 17.0.9, frozen repository toolchain, Android 15 ARM64 emulator (`dev_arm64_api35`, 1080×2400, density 420 dpi). Page tests compose the real reader/parser with the existing fake storage seam; image-pipeline tests exercise the live test provider. The JVM fixture suite includes `tables.md` and `relative-images.md`; page geometry uses focused equivalents.

| Check | Result |
| --- | --- |
| New page tests against pre-change `ReaderScreen.kt` (`5a75429`) | Expected failure: 6/6; column starts differ by 169 px, tiny icon measures 840 px, tall image upscales, both copy timeouts fail |
| All JVM variants | PASS: 694 records / 307 unique methods, zero failures/errors/skips; six new sizing tests plus four concurrent reading-default methods |
| `lint` / `ktlintCheck` | PASS |
| Full/Eink debug app and androidTest APK builds | PASS |
| Final Full page regression | PASS: 57/57; latest concurrent TOC adjustment rechecked with 9/9 rendering/TOC/search cases |
| Final Eink page regression | PASS: 57/57; latest concurrent TOC adjustment rechecked with 9/9 rendering/TOC/search cases |
| Screenshot review | PASS: alignment/borders, natural tiny image size, inline copy feedback |
| Real E-Ink contrast, refresh, ghosting; TalkBack | NOT_RUN |

The first expanded 54-test run failed six legacy checks per flavor because they expected the permanently visible Compose chrome title removed by UX-06. The document heading was still visible through the native Markwon TextView. `PageViews.hasVisibleText` now checks that actual view and its visible bounds, and the three affected test classes retain their body, TOC, search, image, mode, and rotation assertions. Concurrent code writes also produced one transient unresolved-tag compile failure; the completed code was rechecked. Formatting failures were resolved without behavior changes. Shared-worktree builds also intermittently failed to resolve functions whose source remained present; final validation therefore ran in an isolated source copy under `/tmp`, whose Kotlin sources and build configuration matched the workspace by SHA-256 comparison.

One concurrent search regression initially failed because its helper checked the topmost preceding paragraph instead of the current landing block, and two previous steps wrapped to the last match rather than returning to the first. The test now measures the current match's native view, returns one step, and checks the current index; the focused device rerun passed. No product behavior was changed for this correction.

Initial development checks found missing constraint-extension imports and one ktlint blank-line violation, both corrected. The combined build also exhausted the default 512 MiB Gradle heap when compiling Kotlin in-process. A 2 GiB heap, two workers, and disabled parallel execution passed; these are command-local settings, with no repository configuration change. Existing deprecation warnings remain.

Actual final build command (JDK 17 and an isolated temporary `GRADLE_USER_HOME` were set):

```bash
./gradlew test lint ktlintCheck \
  :app:assembleFullDebug :app:assembleEinkDebug \
  :app:assembleFullDebugAndroidTest :app:assembleEinkDebugAndroidTest \
  -Pkotlin.compiler.execution.strategy=in-process \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m' \
  --max-workers=2 --no-parallel --offline --no-daemon
```

The selected page suites are `ReaderRenderingLayoutTest` (6), `ReaderInteractionTest` (19), `ReaderEntryTest` (5), `ReaderSyntaxPageTest` (5), `ReaderImageTest` (1), `ReaderImagePipelineTest` (6), `ReaderResourceTest` (9), and `ReaderDisplayTest` (6): 57 per flavor after the concurrent work added three overlay regressions. Both app/test APK pairs were installed with `adb install -r`, then run with `adb shell am instrument -w -r -e class <comma-separated-suite-names> -e renderingScreenshotPrefix <prefix> <test-package>/androidx.test.runner.AndroidJUnitRunner`. Test packages are `io.leaflet.reader.debug.test` and `io.leaflet.reader.eink.debug.test`.

The final concurrent TOC change removes the closing menu title from the reveal offset. After the broad 57-case passes, both flavor builds and all JVM/static gates were repeated, followed by the six UX-07 cases plus `tocClickLandsOnItsSection`, `tocLandingIsNotCoveredByTheTopChrome`, and `firstBlockSearchMatchStaysBelowTheSearchBar`: 9/9 per flavor, zero skips. The final tested source/config snapshot matched the workspace. Installable debug APKs and SHA-256/test summaries are retained at [app/build/outputs/ux07](../../app/build/outputs/ux07/verification.json).

For a repeat run through Gradle, use `:app:connectedFullDebugAndroidTest` and `:app:connectedEinkDebugAndroidTest` with `-Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderRenderingLayoutTest` for the focused new suite; omit the class filter for the entire app suite.

## Visual evidence

These are real production-reader captures from the instrumented emulator host, using synthetic documents and bitmaps. They demonstrate software layout, not E-Ink hardware behavior.

| Scenario | Before | After |
| --- | --- | --- |
| E-Ink runtime table | [Capture](../development/ux07-screenshots/table-eink-before.png) | [Capture](../development/ux07-screenshots/table-eink-after.png) |
| Standard runtime table | [Capture](../development/ux07-screenshots/table-standard-before.png) | [Capture](../development/ux07-screenshots/table-standard-after.png) |
| 16×16 local icon | [Capture](../development/ux07-screenshots/small-image-before.png) | [Capture](../development/ux07-screenshots/small-image-after.png) |
| Copy feedback | [Capture](../development/ux07-screenshots/copy-feedback-before.png) | [Capture](../development/ux07-screenshots/copy-feedback-after.png) |

## Limits, next candidate, and cleanup

Real E-Ink verification, TalkBack, vendor refresh control, and release ownership remain outstanding. No product release or commit was made. Pixel rounding can differ by one pixel. The unmodified image-loader lifecycle and decode budget retain the limitations in REQ-0009.

The next candidate is the existing UX-04/05 navigation track: `LibraryBrowseScreen` still renders the path as plain text, scan controls change the list's vertical placement, and file rows have only 4 dp vertical padding. First satisfy UX-01/02 and UX-04 prerequisites, then add ancestor breadcrumbs, a stable scan-status slot, and 48 dp row targets. This is a proposed follow-up, with no implementation in this iteration.

Temporary emulator/Gradle copies, source backups, pulled captures, and logs were created only under `/tmp/leaflet-ux07.ml4UAV` and `/tmp/leaflet-ux07-gate.log`; cleanup is complete: the emulator was stopped and these temporary paths were removed. Eight valuable captures are intentionally retained at `/Users/clover/source_dev/Leaflet/docs/development/ux07-screenshots/`. The final debug APKs and `verification.json` are retained at `/Users/clover/source_dev/Leaflet/app/build/outputs/ux07/` as reviewable delivery artifacts. The isolated build reports were removed with the temporary source copy; existing workspace build artifacts belong to the concurrent work and were preserved. Existing user files and test fixtures are preserved.
