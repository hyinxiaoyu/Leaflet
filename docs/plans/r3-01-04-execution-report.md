# R3-01–04 Execution Report

English | [中文](r3-01-04-execution-report.zh.md)

Date: 2026-09-19. Executor role: add tests/minimal fixes. Scope follows [Remaining MVP Tasks](remaining-mvp-tasks.md), which dispatched only R3-01–04; R3-05–10 were not assigned this round.
Status: **Awaiting acceptance**; executor self-report is not independent review.

Environment: emulator-5554 / AVD `dev_arm64_api35` / Android 15 (API 35), an emulator, not E-Ink hardware; JDK 17, AGP 8.13.2, Gradle 8.13, Kotlin 2.0.21.

## Gate Summary

All checks below passed in one invocation:

| Check | Result |
| --- | --- |
| `./gradlew test lint ktlintCheck` | Pass |
| `:app:assembleFullDebug :app:assembleEinkDebug` | Pass |
| Full app connected tests | 41 XML tests: 40 executed + 1 designed skip. Use XML counts; never say 41/41 passed. |
| E-Ink app connected tests | 41 XML tests: 40 executed + 1 designed skip |
| JVM unit tests | 588 variant / 276 unique; 0 failures/errors |
| `:core:markdown:connectedDebugAndroidTest` | 5/5 |
| `:core:storage:connectedDebugAndroidTest` | 16/16 |

Under high machine load, `:app:assembleEinkRelease` / `assembleFullRelease` can hit `D8: java.lang.OutOfMemoryError` in the daemon. Retrying with `-Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"` made all four packages green. `gradle.properties` was not changed because project rules prohibit machine-specific values there.

## R3-01 — Independent Review of Latest Defects and Lifecycle Stability

### Reused evidence (no reimplementation)

| Previous result | Review | Outcome |
| --- | --- | --- |
| DEF-003 table-cell fix | Rerun JVM `EntryBindingsTest.identicalCellsOnlyTheSelectedOneIsCurrent` and production-view `ReaderEntryTest.duplicateTableCellOnlyTheSelectedOneIsCurrent`; exactly one of four identical cells is current, then selection moves | PASS |
| DEF-004 fences/copy | `ReaderEntryTest.codeFenceWithInnerFenceShowsWholeBody` plus JVM four-backtick, tilde, indent, character-exact copy cases | PASS |
| DEF-005 real reachability | Three `ReaderStorageBoundaryTest` cases with real Room and reader path for permission loss/missing row | PASS |
| DEF-006/007 | Page tests include real view tree and production-view assertions; re-summarized here and in verdict | PASS |
| DEF-008 | See below | FIXED_PENDING_VERIFICATION |

### Added lifecycle stability tests

`app/src/androidTest/.../ReaderLifecycleTest.kt`, two cases:

1. `leavingTheRouteFlushesPositionAndKeepsTheViewModelUsable`: leaving the Reader composition (equivalent to returning to Library) causes `DisposableEffect` flush to reach the store; reopening the same document in the same ViewModel keeps the same ParsedDocument.
2. `activityRecreationKeepsTheDocumentAndRestoreAddress`: real host Activity `recreate()` preserves ParsedDocument instance and `restoreBlock`; disposing the old composition flushes position.

### DEF-008 residual risk

The original `SlotWriter.removeGroup` AIOOBE did not recur in this round, including rotation, Back, recreation, and combined two-flavor suites (41 XML entries each). Evidence of implemented behavior changes: (a) persistent `ReaderThreadingTest` sees 3 non-main-thread snapshot applies before fix and 0 after; (b) test-side main-thread discipline. These behavior changes are confirmed, but “IO-thread StateFlow write was the original crash root cause” remains an inference from the stack trace. Closure conditions (explained root cause + targeted validation) are not all met, so keep FIXED_PENDING_VERIFICATION.

## R3-02 — Complete Library Scenario Acceptance

Added four cases in `core/storage/src/test/.../LibraryScannerTest.kt`:

- `preciseDiscoveryMatrixCounts`: 5 folders × 4 files, 3 empty folders, and one nested document. Scan succeeds with 21 documents; hierarchy (`folder1/doc1-1..4.md`) is preserved; empty folders add no row; nested file is indexed.
- `multipleLibrariesStayIsolated`: two Libraries with same relative path have separate rows; removing A retains B's row and grant.
- `capsAcceptTheBoundaryAndRejectOnePastIt`: exactly `MAX_INDEXED_DOCUMENTS` (=500) succeeds; 501 explicitly fails with `ScanFailureKind.OVERFLOW` and zero writes. Also pins `MAX_VISITED_ENTRIES` (=5000). Measurement basis is the constants; boundary is covered here and by existing `visitedOverflowFailsExplicitlyWithOldIndexKept`.
- `scanIsMetadataOnlyAndNeverReadsFileContent`: scanning 25 files opens no streams (`ContentAccess` is never called), takes one grant, leaves `contentHash` empty; initial scan indexes metadata only.

Reused evidence: 20+ JVM cases for add/update/delete refresh, mtime/size skip, unreliable metadata, partial-failure no-delete, cancellation, serial concurrency, malformed entries; five device `ScannerInstrumentedTest` cases for real DocumentsContract traversal/permission loss/empty cursor/missing columns/progress; six `SafInstrumentedTest` cases for read/loss/missing/cancel/denial/unknown scheme.

Conclusion: PASS for the matrix and cap rules. Still unverified: real-device 25-row matrix and multi-Library UI walkthrough (this round uses JVM rule coverage plus existing device evidence); cap values are constants, not end-to-end performance evidence on a provider with 500 real documents.

## R3-03 — Full Markdown/TXT Syntax Display Acceptance

Added five `ReaderSyntaxPageTest.kt` production-page tests:

- `headingsAndInlineStylesRenderAsSpansWithoutMarkers`: real view asserts H1–H6 are `HeadingSpan` without `#`; paragraph spans include strong/emphasis/strike/code without source markers; bullet/ordered/quote/rule spans exist; `codeForCopy()` equals `val x = 1`; table cells render individually in Compose semantics.
- `taskListItemsKeepMarkerAndTextOnDevice`: DEF-009 regression (below).
- `unknownSyntaxKeepsTheBodyReadable`: `$$x^2$$`, Mermaid fence, and `[^note]` text remain readable.
- `brokenEncodingDegradesWithNoticeAndKeepsValidText`: damaged encoding gives fallback + notice and retains valid text.
- `oversizedDocumentDegradesWithNoticeAndKeepsTheText`: paragraph >32 KiB becomes `PLAIN_FALLBACK` chunks with static notice.

### New defect found and fixed: DEF-009 (P1)

Symptom: task-list entry was an **empty production row** (`kind=LIST text=[] src=[]`) even though neighboring text was visible.

Cause: `BlockSlicer.listItems` used `filterIsInstance<ListItem>()`. Markwon task item extends `CustomBlock`, so it was filtered out, leaving an empty slice/TextView. JVM shim path was unaffected, which is why only the device path exposed it.

Minimal fix: iterate arbitrary child nodes; identify Markwon task items by class name and reflectively read `isDone()` to add `[ ] `/`[x] ` when available. If reading fails, add no marker; do not guess. Keep the shim `TaskMarker` branch separate and do not double-apply it.

Evidence: before fix, two JVM cases and one device case failed; after fix, all `core:markdown` JVM tests and 5/5 device cases passed. See DEF-009 in [round-2-open-defects.md](round-2-open-defects.md).

Conclusion: PASS for production-view syntax listed in §§10/14/24; TXT/fallback literal display reuses DEF-001. Unverified: manual appearance of syntax highlighting/table wrapping; task checkbox drawable was not screenshot-reviewed.

## R3-04 — Reading Position, Settings, and Mode Persistence

Added `app/src/androidTest/.../ReaderPersistenceTest.kt` in three test classes:

- `ReaderPersistenceSeedTest.seedSettingsAndPosition`: uses real on-disk DataStore (`files/r3-persist-datastore/r3-persist.preferences_pb`) to write font size/line spacing/family/margin/display mode/reading mode/soft-wrap and real Room (`r3-persist.db`) for a Library and position row (block 3 + anchor).
- `ReaderPersistenceAfterRestartTest.settingsAndPositionSurviveProcessKill`: compares each value and position row exactly. If no seed was run in this process, `assumeTrue` skips, making ordinary suite execution deterministic; a skip is not a pass.
- `ReaderExternalEditTest.externalEditThenRefreshShowsNewContentAndReplansPosition`: real Room plus real `DocumentSourceImpl`/`StorageReaderGateway` (only SAF `ContentAccess` is a test seam). Expired position fingerprint falls back to anchor (`restoreBlock == 0`) and shows change notice; after external edit + `refresh()`, new text appears in the page.

### Cross-process evidence (real kill, not same-process recreation)

```text
adb install -r <app-full-debug.apk>; adb install -r <app-full-debug-androidTest.apk>
am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceSeedTest        → OK (1 test)
am force-stop io.leaflet.reader.debug
am instrument -w -e class io.leaflet.reader.ui.ReaderPersistenceAfterRestartTest → OK (1 test)
```

Before force-stop, disk files existed: DataStore preferences file 170 B and Room `r3-persist.db` 65,536 B. **Use `adb install -r`**. Gradle `connectedAndroidTest` uninstalls between runs and clears state; using it for the two phases causes a false failure (observed and recorded).

Reused evidence: permission/missing states in `ReaderStorageBoundaryTest`; recent list and cascade deletion in `RoomInstrumentedTest`; JVM `capabilitiesForFlavorDefaultsStandardModesBothFlavors` and page test `einkModeOpensSameDocument` for defaults/hot mode switch.

Conclusion: PASS for real disk persistence and actual process restart. Still unverified: manual real-device settings change → kill → restore and TalkBack; this round used the automated equivalent path.

## Changed Files and Source Pins for Review

```text
036501d26e2ba4e0893107db9ec5a943e5eaf56b181adc9a4f60d947d2ca07bf  core/markdown/src/main/kotlin/io/leaflet/markdown/MarkdownRenderer.kt
dd8d110d01b4c56bdf503bbc5ca8a0e901348ad76d2ce7a61fbca27aa618d079  core/markdown/src/test/kotlin/io/leaflet/markdown/MarkdownSamplesTest.kt
623e0089b68fe3d3863a4ee67d63dc1521285a5306d99a6dfe8c49d6e5c21ee8  core/storage/src/test/kotlin/io/leaflet/storage/scan/LibraryScannerTest.kt
3cd8dd1ae1603525bbb34a83964b78488148c2326f639c4668428279eaf1f555  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderSyntaxPageTest.kt
```
The syntax-page pin above was later superseded; use the updated pin below. Other initial pins:

```text
c4b34592c88feb7fddee9e4c023baa8fc4163d63de217916071b5db3d8f5c1c1  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLifecycleTest.kt
```

Tests added: R3-01 two lifecycle cases; R3-02 four Library matrix cases; R3-03 five syntax-page cases; R3-04 three persistence/edit classes. Product code change: only minimal DEF-009 fix in `MarkdownRenderer.kt`.

## Stop Conditions and Handoff

- This round stopped after **R3-04**. R3-05–10 were not run/claimed; no commit/release/signing/license change.
- Before independent review status is “awaiting acceptance.” Suggested review order: DEF-009 fix → R3-03 production-view assertions → R3-04 process phases (remember `adb install -r`) → R3-02 caps/matrix → R3-01 lifecycle and DEF-008 risk.
- Hardware/owner matters R3-09/10 remain outside this round.

## Independent Review Remediation (2026-09-20)

Accepted review conclusion: R3-01/02/04 were previously PARTIAL. This follow-up adds minimum evidence for DEF-010/011; R3-03 conclusion is unchanged.

### Corrected two errors in this report

1. **Stale source pin:** `ReaderPersistenceTest.kt = 686b294f…` was captured before splitting seed/after-restart classes. Review's `215dfa87…` value was correct. Pins must be regenerated after final edits; any later edit requires repinning.
2. **Test-count wording:** XML `tests` is executed count; skips are separate. First R3-01–04 run was Full 40 executed + 1 skipped, E-Ink 40 + 1. Do not call this “all 41 passed.”

### DEF-010 — Discriminating Exit-Flush Evidence (Added)

`ReaderLifecycleTest.leavingTheRouteFlushesAPositionThatWasNotSavedYet` creates a position inside the 2-second throttle (`onVisibleBlock(4)`), first proves address 4 is absent from prior writes (only exit flush can create it), then leaves route and requires a new write with final address 4. Temporarily commenting out `ReaderScreen.onDispose { viewModel.flushPosition() }` makes the test fail (`ComposeTimeoutException`, 20-second wait); restored version passes.

An initial bare `Thread.sleep` poll missed writes because test scheduling defers main-thread coroutines until rule frame pumping. Replaced with `compose.waitUntil`; this is test-synchronization behavior, not a product defect.

For recreation, `createComposeRule` does not remount composition in a new Activity; it composes only the first Activity. Move that assertion to `ZzActivityRecreationTest` (runs last to avoid later “No compose hierarchies found” failures). It asserts destruction-time flush and ViewModel state. New-page remount evidence belongs to DEF-011's production process test, not an auxiliary assertion.

### DEF-011 — Production Wiring and Cross-process Page Restore (Added)

`ReaderProductionPersistenceTest.kt` uses production storage IDs: DataStore at `files/datastore/settings.preferences_pb` (`preferencesDataStore(name="settings")`) and Room `leaflet.db`. Phase 2 composes real `ReaderScreen` with `SettingsViewModel` on the same DataStore and asserts page-level restore: real entry `TextView.textSize == 16 × fontScaleForStep(4) × scaledDensity` (60.9 px; default is 48.3 px), and block 4 content (`Delta`) is visibly rendered.

Both persistence suites now gate seeding with an independent marker file instead of the font-size value under test. `-e leaflet.requireSeed true` makes missing phase-1 seed fail in a two-phase run; deleting the marker was verified to fail with `two-phase run requires the seed phase (marker missing)`.

Both flavors ran adb phases `adb install -r → am instrument → am force-stop → am instrument`; Full and E-Ink each reported phase 1 OK (1 test), force-stop, phase 2 OK (1 test).

Production persistence tests skip in ordinary suite runs unless `-e leaflet.productionStores true`, because they delete/change app's real `leaflet.db` and DataStore folders and would corrupt later cases if shared. This gate was added after observed “No compose hierarchies found” cascade failures.

Still not covered: production `MainActivity` route and real SAF tree/provider. Document bytes come from disk via a `ContentAccess` seam.

### Environment and Gate Status

Two emulators were running (`emulator-5554` AVD `dev_arm64_api35` plus emulator-5556); load average ~14.5 and available memory ~80 MB. Under this load, full connected suites intermittently failed Activity startup with `No compose hierarchies found`, before test bodies ran. The same targeted group passed 14/14:

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLifecycleTest,io.leaflet.reader.ui.ZzActivityRecreationTest,io.leaflet.reader.ui.ReaderStorageBoundaryTest,io.leaflet.reader.ui.ReaderThreadingTest,io.leaflet.reader.ui.ReaderSyntaxPageTest,io.leaflet.reader.ui.ReaderPersistenceSeedTest,io.leaflet.reader.ui.ReaderPersistenceAfterRestartTest
```

JVM gate remained green: 588 variant / 276 unique, 0 failures; lint/ktlint green. Rerun full both-flavor connected suites when environment recovers; this report does not turn targeted green into full-suite pass.

### Updated Source Pins (captured after files were final)

```text
c33179bfa125d34fb630a67d570d0b7a6c6a7f08cbff2f83666fb221d72ccd0e  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderPersistenceTest.kt
c092b07a428f1baae0bf7b6681032c6baf149149e73561776cf20164266e1d99  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderProductionPersistenceTest.kt
f98a3717fcf6cac856440d45bc08b2939a61b6dc15fa49aa124f041e4f7f269d  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLifecycleTest.kt
7b99e7e05fb9c1311cdbef57d851e45cf1b486ad8e99818da53d2911f14cc8f5  app/src/androidTest/kotlin/io/leaflet/reader/ui/ZzActivityRecreationTest.kt
9ae2e1315293042b27abf412c4346b675b070f7295ad9ed0815b3e9fed401f91  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt
```

### R3-02 Handoff Correction

Following review, this round does not claim real-provider scale/multi-Library UI or capacity-performance evidence. It proves rule matrix and cap boundary behavior only (500/5000 constants). Performance budget is handed to **R3-08**; R3-02 remains PARTIAL / not accepted.

## Code Review Response (2026-09-20) — DEF-012 / DEF-013

Both P1 static findings in [code-review report](code-review-2026-09-20.md) were reproduced on device and fixed:

| Defect | Failing-before-fix reproduction | Fix |
| --- | --- | --- |
| DEF-012, flush during document switch mixes state | `ReaderStateConsistencyTest…`: “B must not be written while it loads: [5]” | Bind descriptor to `currentDescriptorId`; clear prior snapshot on switch; flush requires Content whose doc matches current ID; persist uses only that document's descriptor. |
| DEF-013, freshness recheck restores initial block | `…sameDocumentRecheckDoesNotRemountTheReader`: captured TextView was no longer the same instance after recheck | Same-document recheck no longer emits Loading, preserving composition; persist updates `Content.restoreBlock` synchronously so a remount uses latest address. |

Both cases failed before and passed after. Two test synchronization points were adjusted (wait for probe instead of state transition; advance address before recording) because observable timing changed; not product defects. Affected existing classes passed individually: ReaderDisplayTest 6/6, ReaderRecoveryTest 14/14, ReaderStorageBoundaryTest 3/3, ZzActivityRecreationTest 1/1.

Additional source pins:

```text
07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf  app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt
9f9587933a2677cce4169ddba54b6a086e4efbc7ab5e0327a2a33ddb59d4eeb4  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderStateConsistencyTest.kt
043dc684845604cd3a564592923e28f2db77ffd7c7942a625e30ad6bc9298fe3  app/src/androidTest/kotlin/io/leaflet/reader/ui/PageHarness.kt
48154defde7f614facd14b13cb54e27670188e330cc5290217f117b650094248  app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderStorageBoundaryTest.kt
eb5e37ab0968f7d30d5fba635a7cab114bafcd1708bd320fe5934c156b758d0e  app/src/androidTest/kotlin/io/leaflet/reader/ui/ZzActivityRecreationTest.kt
```

Environment had two emulators/load ~13.5–14.5. Large merged runs intermittently fail Activity startup (`No compose hierarchies found`); isolated/small batches pass. Full both-flavor gate still needs rerun when environment recovers.
