# Daily Reading Product Wave 1

English | [中文](daily-reading-product-wave1.zh.md)

Date: 2026-10-01
Requirement: [REQ-0013](../requirements/REQ-0013-daily-reading-home.md), `IN_PROGRESS`.
Status: IMPLEMENTED — scoped verification passed; Android/device acceptance pending.
User priority: daily reading loop, explicitly selected in this conversation.
Evidence: [machine-readable outcomes and source hashes](review-evidence/2026-10-01-product-wave1/results.json).

## Product outcome

The app now starts from a reading Home rather than a stack of unrelated controls. One lazy scroll contains the latest document, folder cards, recent history, and Settings/About. Settings is also immediately reachable in the header. The first-use card explains how to add a folder and that originals remain in place. A loading index does not masquerade as an empty collection; failures have Retry. The real Library and Recent state streams remain the only data source, with no invented progress or file counts.

Continue uses the existing document-ID/Reader restore path. Missing/access-lost entries remain visible and open recovery instead of pretending the source is readable. Library long-press removal still confirms. History clearing now confirms and only invokes the existing auxiliary-history operation.

Reading settings show real sp, line-spacing multipliers and dp margins. The live preview uses ReaderStyle's same 16 sp body baseline, 22 sp line baseline, font bands, family allowlist and margin bands. Uniform static selected surfaces replace the asymmetric filled/empty buttons and dot glyphs. Existing preferences and reading defaults are preserved.

All navigation content shares system-bar/cutout padding and a theme background, excluding IME from this new padding policy. System-bar icon contrast follows Standard/system-dark versus forced-light E-Ink. Standard light uses a quiet green reading accent; E-Ink supplies all Material color roles in opaque grayscale while preserving no-motion/no-ripple policy.

## Work packages and boundaries

| Task | Deliverable | State |
| --- | --- | --- |
| PD-01 Home and return-to-reading | One lazy Home, actual metadata, continuation, first-use/loading/retry, confirmed history clearing | Implemented; page execution pending |
| PD-02 Reading controls | Uniform selectable targets, resolved units, immediate preview, existing settings persistence | Implemented; page execution pending |
| PD-03 Shared shell | System-bar/cutout bounds, icon contrast, complete E-Ink palette | Implemented; device measurement pending |
| PD-04 Proof and handoff | Resource linking, fresh dual-flavor source/page compilation, JVM policy/state regressions, paired documentation | Scoped checks pass; complete app/device gates pending |

No account, sync, editor, library-wide search, database migration, new dependency, signing material, project license or hardware-refresh control was introduced. This wave does not declare all of UX-01/02/03/04/10 complete: external-open intents, reader search-highlight cleanup and global locale parity retain their original scope. Unrelated uncommitted reader/browse/review work is preserved.

## Implementation

| Area | Files and changes |
| --- | --- |
| Home | New `ui/library/LibraryHome.kt`; existing `LibraryListScreen` API moved from `LibraryScreens.kt`; flat stable-key Library/Recent rows, header/footer entry points and confirmation dialogs |
| List lifecycle | `LibraryViewModel.kt`: first-load/failure flags and retry; one tracked scan-state subscription per Library, removed/retried observers are cancelled; status failure does not cancel the folder list |
| Recent rows | `RecentSection.kt`: shared production row/subtitle binding; static localized relative times; missing/access-lost cues retained |
| Settings | New `ui/ReadingSettings.kt`; existing `SettingsScreen` API moved from `LeafletNav.kt`; preview and controls use existing SettingsViewModel/DataStore keys |
| Shell/palette | `LeafletNav.kt`, `MainActivity.kt`, `ui/theme/Theme.kt`: bounds, icon contrast and centralized ColorScheme |
| Resources/tests | English/Chinese strings, `DailyReadingPageTest` (6 methods), `LibraryHomeLoadTest` (3 methods), `ColorSchemeTest` (1 method) |

No original-file write path was added. No progress percentage is shown because this UI seam does not expose one; folder counts use actual Library rows. Removing history does not remove reading positions.

## Actual verification

macOS ARM64, JDK 17.0.9, frozen Kotlin/Compose compiler 2.0.21 and cached resolved dependencies. AAPT2 from build-tools 35.0.0 freshly compiles changed resources and 48 cached dependency resource sets, links them against existing per-flavor merged resources/manifests, and generates actual updated R symbols. This is resource-link validation, not a rebuilt distributable APK.

| Check | Result |
| --- | --- |
| New loading/palette plus existing settings/metadata contracts | PASS — 9 executed: Home load 3, grayscale scheme 1, reading defaults/persistence 3, recent metadata publication 2 |
| Existing navigation/cancel/selection regressions and independent probes | PASS — 24 + 2; 35 focused JVM executions total across the two runs |
| Changed resource compile/link and generated Java R, Full/Eink | PASS — both variants |
| Fresh affected Kotlin/Compose sources and page suite, Full/Eink | PASS — both main and androidTest component compilations; actual dependencies/generated symbols, not resource stubs |
| Changed-file ktlint 1.0.1 | PASS — retains the app's Compose function-naming exception |
| Six production-content page cases | Compiled; NOT_RUN on Android |
| Whole Gradle suite/lint/APK assembly, Android screens, real E-Ink/TalkBack | NOT_RUN — current sandbox blocks Gradle/ADB sockets; no new full-app/device acceptance claim |

The page cases cover a working empty-state action, no false first-use guide while loading, 20 Libraries/30 Recent entries with reachable footer and continuation, history-clear confirmation/cancel, recovery handoff, and preview reacting to a real SettingsViewModel. Controlled metadata drives the production content; a test-only copy of the UI is not used.

Initial manual resource linking lacked dependency drawables; adding the real dependency resource sets resolved it. The initial standalone page compile lacked Kotlin friend paths for internal production content; this was corrected in the checker. The first preview draft used the font-size baseline for line height; repository comparison corrected it to the Reader's 22 sp baseline before final proof. These were not hidden or counted as passing gates.

## Review and next gate

This delivers an actual Home/settings/shell code slice; it is not a release or a claim that the entire product is finished. UI readability, large-font layout, dialog interaction, system-bar behavior, tap targets and E-Ink ghosting still need actual device evidence. Existing partial locale suppression is not removed or presented as full bilingual coverage.

When sockets are permitted, use JDK 17 and run:

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.DailyReadingPageTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.DailyReadingPageTest
```

Temporary compiler/resource outputs, extracted resource sets, formatter config and logs were created only in `/tmp/leaflet-product-wave1.KM2uPO` and removed after evidence capture. Retained outcomes/source hashes are intentional delivery evidence at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-product-wave1/`. No screenshot, rebuilt APK, commit or release is claimed. Earlier acceptance/self-report documents are not overwritten by this wave.
