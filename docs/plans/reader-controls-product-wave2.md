# Reader Controls — Product Wave 2

English | [中文](reader-controls-product-wave2.zh.md)

Review ID: READER-CONTROLS-20261001. State: IMPLEMENTED, DEVICE ACCEPTANCE PENDING. Scope: REQ-0014, continuing the user's ebook-reading polish request. This independent record does not confirm earlier acceptance reports.

## Outcome

A static footer now exposes Previous/Menu/Next instead of relying entirely on hidden tap zones. Previous/Next use the existing instant turn path and real list boundaries, and remain absent in continuous-scroll mode. Progress output and logical-position persistence are preserved. Controls have at least 48 dp height and standard enabled/disabled semantics.

The reading menu wraps its existing TOC, search, refresh, mode and back actions, plus a Help entry. Its height is capped at 40% of the fixed Reader viewport; extra actions scroll inside the menu rather than resizing the body list. LocalMotionPolicy selects a no-fling implementation when animations are disabled, so the E-Ink menu stops on release; Standard retains the existing Foundation fling behavior.

Help reuses the existing tutorial and can be reopened without clearing its persisted seen flag. Tutorial display and pending initial logical-position restoration guard background Reader actions, including touches in page margins. A pointer leaving the Reader permanently cancels its gesture even if it re-enters before release. Body swipes still turn only once on release, and child controls/table/code gestures retain priority.

## Work split and implementation

| Task | Files and deliverable | State |
| --- | --- | --- |
| RC-01 Visible navigation | New ReaderControls.kt navigation bar; ReaderScreen.kt delegates to existing list/menu/turn callbacks with real boundary states | Implemented |
| RC-02 Reachable menu | FlowRow in a capped independent scroll container; BoxWithConstraints supplies the viewport cap; centralized MotionPolicy controls fling | Implemented |
| RC-03 Help and input safety | Existing tutorial gains a session re-open entry; background input is guarded; PageGesture/ReaderPageGestures cancel pointer exit/re-entry | Implemented |
| RC-04 Verification | Three new JVM gesture regressions, one menu-motion test, five new production page/component cases, resources and fresh dual-flavor component compilation | Scoped checks pass; device execution pending |

REQ-0014 was written as READY before implementation and is now IN_PROGRESS. The requirements index, Reader architecture and paired changelog are updated. Shared Reader state, original-file read-only access, manual wiring, flavor separation and centralized motion policy are retained. No vendor SDK, dependency, migration, page-count fiction or physical-key mapping was added. Earlier uncommitted Home/library/position/image/table changes and dated evidence remain intact.

The primary new files are:

```text
app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderControls.kt
app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderMenuMotionTest.kt
app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderControlsPageTest.kt
docs/requirements/REQ-0014-reader-controls.md
docs/requirements/REQ-0014-reader-controls.zh.md
docs/plans/reader-controls-product-wave2.md
docs/plans/reader-controls-product-wave2.zh.md
docs/plans/review-evidence/2026-10-01-reader-controls/
```

## Actual verification

Environment: macOS ARM64, JDK 17.0.9, frozen Kotlin/Compose compiler 2.0.21, Foundation 1.7.5, ktlint 1.0.1 and build-tools 35.0.0. The checker reads cached resolved dependencies and existing merged resources/manifests as link inputs. Current English/Chinese/flavor strings plus 48 dependency resource sets are compiled, linked and used to generate actual Java R classes. Core paging/settings and ten current Reader files are freshly compiled. Resource-only containers are temporary linker artifacts, not installable app APKs.

| Check | Actual result |
| --- | --- |
| Existing paging/settings plus pointer-boundary contracts | PASS — 41 JVM tests, including 3 new pointer exit/re-entry regressions |
| Conservative menu fling contract | PASS — 1 JVM test verifies no scroll calls for negative/zero/positive velocities; 42 executed JVM tests total |
| Changed Kotlin formatting | PASS — ktlint with the existing Compose naming exception |
| Actual resources/link/generated R, Full/Eink | PASS — both variants; no invented R symbols |
| Reader components and page suite, Full/Eink | PASS — four fresh Kotlin/Compose component compilations |
| ReaderInteractionTest + ReaderControlsPageTest | 22 + 5 methods compiled; NOT_RUN on Android |
| Gradle 8.13 Full/Eink APK gate | BLOCKED before task execution: FileLockContentionHandler reports java.net.SocketException: Operation not permitted |
| Full suite/lint, actual screens, real E-Ink/TalkBack | NOT_RUN; no complete app-build/device acceptance claim |

The five new page/component cases cover visible navigation with stationary menu toggles, single-screen boundary-disabled buttons, repeated Help and blocked tutorial-margin input, a continuous-mode menu without page buttons, and all six menu actions at 280 dp width/200% font scale under a 120 dp cap. They exercise the production Reader or production ReaderActionMenu, not a test-only UI copy. Existing interaction cases compile against the changed Reader and cover viewport distance, head/tail, overlap, links and wide table/code ownership.

The first Reader compilation caught the wrong ExperimentalLayoutApi import and an implicit BoxWithConstraints receiver hidden by ColumnScope. Both were corrected before the successful gates; the initial diagnostics are retained separately. Existing ImageLoader and LocalLifecycleOwner warnings remain outside this slice. A source review also caught the new menu's default fling; centralized policy and the no-scroll contract now cover it.

## Evidence and limits

[Machine-readable results and source hashes](review-evidence/2026-10-01-reader-controls/results.json), compiler/resource/test logs, initial diagnostics and the self-cleaning checker are retained under `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-reader-controls/`. All compiler/resource/Gradle scratch directories were created only in `/tmp/leaflet-reader-controls-*` and removed on checker exit, including failed attempts. Declared reports/evidence are intentional delivery artifacts. No screenshot, installable APK, commit or release is claimed.

Progress remains the existing block-based output and saved fallback semantics; this wave does not invent stable page numbers. Actual button readability, menu wrapping, Help interaction, list geometry, pending-restore input and E-Ink ghosting still require device execution. Device evidence prevents marking REQ-0014 DONE.

## Reproduction and next gate

Re-run the scoped proof with the frozen local cache:

```bash
python3 docs/plans/review-evidence/2026-10-01-reader-controls/run_checks.py \
  --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

When Gradle/ADB sockets are available, use JDK 17 and run:

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderControlsPageTest,io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderControlsPageTest,io.leaflet.reader.ui.ReaderInteractionTest
```

Then measure the fixed list bounds while opening/closing the menu at document end, all actions in narrow portrait/landscape at 200% font scale, final-line visibility, no E-Ink menu inertia, pointer exit/re-entry and Help on a phone and a real E-Ink device. Preserve earlier reports as their dated snapshots.
