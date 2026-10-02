# Reading-Position and Link Review — 2026-10-01, Round 3

English | [中文](code-review-2026-10-01-round-3.zh.md)

**Implementation response (2026-10-01): the user authorized direct fixes. All three retained failures now pass; 43 focused JVM cases pass. Both flavors' main and androidTest Kotlin/Compose sources compile through the local compiler, and changed-file ktlint passes. Gradle/APK/device acceptance remains pending.** See [Implementation response](#implementation-response-2026-10-01). Original findings below describe the reviewed pre-fix snapshot.

## Conclusion and Scope

Two new P2 defects were independently reproduced: an unchanged document's nonzero in-block position is erased by a no-movement flush, and a delayed relative-link resolution still emits navigation after switching documents. The existing P1 save-ordering finding CR-20261001-04 also remains OPEN: eviction removes ordering state for a document with an unfinished write. This is an additional counterexample under the same finding, not a duplicate defect ID.

The latest fixes to repair coalescing and cursor projection pass the previously retained counterexamples and their product regressions. CR-20261001-05 now passes the focused JVM evidence; real provider/Room/device closure remains pending. This review covers existing reader/storage/navigation behavior only. No new requirement or product implementation was reviewed or added.

HEAD remains `5a754292c1ea02f33af0cb917f5cf24c29dbb763`; tests used a working-tree snapshot under `/tmp/leaflet-review4.jkop4e/repo`. Locations below refer to that snapshot, not a committed revision. See the [preceding review and acceptance record](code-review-2026-10-01-round-2.md).

| ID | Priority | Finding | Evidence | Status |
| --- | --- | --- | --- | --- |
| CR-20261001-04, pending-write eviction | P1 | Eight subsequent documents evict an unfinished document's repair target | A's latest block 5 is overwritten by its old block 2 | OPEN |
| CR-20261001-08 | P2 | No-movement flush erases a stored in-block offset | Expected 300, actual 0 | OPEN |
| CR-20261001-09 | P2 | Old document's delayed link resolution emits navigation after a switch | Expected no callback, actual `[target]` | OPEN |

## CR-20261001-04 — Pending Writes Must Survive Bookkeeping Eviction

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:435–447`, `463–475`; `MAX_TRACKED_SAVE_DOCUMENTS` is 8.
- **Trigger and proof:** Hold A's block-2 write, let A's newer block 5 finish, then open and save eight other documents. Release A's old write. `pendingOlderWritesMustNotLoseTheirRepairTargetAfterEightOtherDocuments` in [ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) confirms A still contains 5 before release, then fails with `expected:<5> but was:<2>` afterward.
- **Cause:** The bounded `newestRequestByDocument` map removes A and its `newestCompletedByDocument` entry without checking for unfinished saves. The late write can still mutate A's stored row, but `repairTargetFor()` now has no newer snapshot to repair it with. The coalescing loop therefore ends after writing the old address.
- **Impact:** Auxiliary reading progress can still move backward after rapid multi-document navigation while a save is delayed. Original documents are untouched.
- **Minimal direction and closure:** Bound idle bookkeeping rather than evicting state owned by active writes, or use a persistence policy that does not depend on retained repair targets. Keep ordering state until all relevant completions are resolved. The retained eviction regression must pass with dispatch, overlapping repair, cross-document snapshot, and normal save baselines. The new case remains part of CR-20261001-04.

## CR-20261001-08 — In-block Position Is Erased on Flush

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:852–862`, `918–925`; `ReaderScreen.kt:225–227`, `255–260`.
- **Trigger and proof:** Seed a version-1 position for unchanged content at block 2, offset 300; open it and immediately flush without reporting any movement. The parser fixture has eight paragraphs of more than 700 characters each, so the seeded character offset fits the block. `aNoMovementFlushMustPreserveTheStoredInBlockOffset` in [ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) confirms block 2 is restored and offset 300 remains in the store before flushing; after the flush it is 0.
- **Cause:** `PositionRestore` returns the saved offset and the throttle is seeded with it, but current state/save snapshots retain only the block index. `persist()` always writes `blockOffset = 0`. The screen similarly restores only `restoreBlock` and observes only `firstVisibleItemIndex`, so movement within a long block has no durable in-block address.
- **Impact:** Existing nonzero positions are lost even without a user scroll. The source path also prevents precise restoration inside blocks taller than a screen. Pixel-level device displacement was not measured in this review.
- **Requirement:** This is the existing [REQ-0003](../requirements/REQ-0003-reading-position.md) FR-1/FR-3 and AC-1 block/offset contract, not a new pagination or reader feature.
- **Minimal direction and closure:** Preserve the complete logical position through load, UI observation, snapshot capture, and persistence. Use the existing position mapping for offset interpretation; do not substitute raw global `scrollY` as the durable contract. Verify no-movement flush, reading/page turns within a long block, reopen/process recreation, and typography fallback. The retained model/store regression must turn green; full UI acceptance remains required.

## CR-20261001-09 — A Delayed Link Can Override a Later Document

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:646–669`.
- **Trigger and proof:** In A, follow a relative document link and suspend `resolveRelative`. Open B fully, then release A's resolution. `aLinkResolvedAfterADocumentSwitchMustNotNavigateFromTheOldDocument` in [ReaderContinuationReviewTest.kt](review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt) confirms B is current before release, but receives `onOpenDocument(target)` afterward: `expected:<[]> but was:<[target]>`.
- **Cause:** The link task is launched in the shared ViewModel scope without retaining its job or revalidating document/load/navigation ownership after the IO resolution. `openJob?.cancel()` concerns the load job, not this task. Successful resolution and fragment handoff use the old callback even after the user has changed documents.
- **Impact:** The production navigation callback can open A's target over the document the user subsequently selected. The test proves the stale callback, not an actual NavController transition or unauthorized access.
- **Requirement:** The existing reader contract requires stale background results not to replace newer document state, and [REQ-0009](../requirements/REQ-0009-local-resources-links.md) defines relative-link navigation from its owning reader/document.
- **Minimal direction and closure:** Bind link resolution, notices, and pending fragments to current document/generation and navigation ownership. Cancel superseded link work and reject stale completion, including route exit. Cover document switching, back navigation, rapid link requests, and delayed failure notices. Preserve legitimate in-tree links and fragment navigation; do not add a library-wide link checker.

## Previous Fixes and Verification

Fresh compilation succeeded with JDK 17.0.9, Kotlin 2.0.21, lifecycle 2.8.7, Coroutines/Test 1.10.2, and JUnit 4.13.2. Affected ViewModels, cursor parser, scanner, repository, storage fakes, and focused tests were compiled from the snapshot; unchanged local project/parser jars supplied dependencies. The direct combined JUnit run returned exit 1: `Tests run: 32, Failures: 3`.

| Check group | Result |
| --- | --- |
| Previously retained reproductions and acceptance tests | PASS 8/8: `ConcurrencyReviewTest` 3, `AcceptanceBoundariesTest` 2, `LibraryRefreshAcceptanceTest` 1, `RepairAndProjectionAcceptanceTest` 2 |
| Current product save regressions | PASS 7/7: `ReaderViewModelSaveSnapshotTest` 3, `ReaderSaveOrderTest` 2, `ReaderSaveOrderDispatchTest` 1, `ReaderSaveRepairTest` 1 |
| Current product metadata/selection regressions | PASS 14/14: `RecentMetadataPublicationTest` 2, `LibraryBrowseSelectionTest` 2, `TreeRowClassificationTest` 7, `TreeEntryProjectionTest` 3 |
| New `ReaderContinuationReviewTest` | FAIL 3/3: offset erasure, stale link callback, active-write eviction |
| Total | 32 executed; 29 passed; 3 failed |
| Gradle, wider gates, Room/SAF/provider/device/UI/E-Ink validation | NOT_RUN independently; no whole-repository or device acceptance claim |

The prior cursor-proxy projection test now passes through the actual freshly compiled cursor loop (its missing-ID branch avoids URI construction). The MIME-only classifier/scanner case also passes. This supports CR-20261001-05's JVM fix; valid-ID Android URI construction, provider IPC, and SQLite cascades remain unverified. CR-20261001-06/07 remain accepted at JVM level. CR-20261001-04 remains rejected because the pending-write case still fails.

## Source Identity and Artifacts

| Reviewed source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `6fda75d018f431ac8073ebb80c9afcce6a7b2eb6bcf35a1f1d500c4c21cd6e32` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `ee0bed9b95a88856c278ba936cfa9cf4bc4f2e580a3f75afc5d2fe66ffcab1a6` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `ec53a13709241941139622ea9ca3f485448ab7eb6ec27d5600d3f099813b5b29` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |

- Temporary snapshot, extracted/copied dependencies, compiler outputs, and logs were created only under `/tmp/leaflet-review4.jkop4e` and deleted after verification.
- Useful reproduction source is intentionally retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-round-3/ReaderContinuationReviewTest.kt`. It is audit evidence, not product test/build wiring; no dependency is added.
- This English report, its complete Chinese pair, and the previous report pair's latest-status link are documentation deliverables. No product fix or new-requirement implementation was made; concurrent work and existing build artifacts were preserved.

<a id="implementation-response-2026-10-01"></a>

## Implementation Response — 2026-10-01

The user's direct-fix request authorized implementation of CR-20261001-04's active-write eviction subcase, CR-20261001-08, and CR-20261001-09. The original failure suite was rerun before editing: 32 tests, 29 passes, 3 failures, reproducing the recorded 300 → 0 offset, delayed `target` navigation, and block 5 → 2 overwrite. The targeted fixes retain the existing position version, database format, flavors, dependencies, and original-file safety.

### Changes

- **Save ownership:** pending requests are counted before IO dispatch. Cache trimming skips documents with unfinished writes and runs again from job completion, including cancellation before the coroutine starts. The eight-document limit applies to idle records; active writers retain their newest snapshot/completion state until all their jobs settle. Existing invocation sequencing and coalescing repairs are preserved.
- **Logical position:** ViewModel load/current state/save snapshots/flushes now carry block index plus character offset. Same-block legacy observations preserve an existing offset; real UI movement reports both coordinates. Observations are bound to their document. `readingLocationFor` exposes the latest address inside the throttle interval for remounts. Explicit block/anchor jumps reset the offset to zero.
- **UI projection:** `BlockPositionGeometry` uses current Compose/native text line starts and relative text origins, including labels/padding. The list measures the target block before immediately restoring its character address, then suppresses that restore echo until real movement. The list is scoped to its document and keeps its state across reading-mode changes. Composite entries without one text layout use a bounded character-length/height fallback; exact composite/media visual accuracy is still a device gate.
- **Navigation ownership:** link jobs are retained, cancelled when superseded/opened/reloaded/exited, and guarded by document/load/navigation ownership before success or notice publication. Pending fragments record their destination; unrelated readers cannot consume them. Source disposal can precede destination opening without discarding an accepted fragment. Fragment landing takes precedence over saved-offset restoration in one initial-scroll effect.

### Changed Files and Regression Proof

| File | Task-relevant change |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | Pending-save ownership, full logical address, link cancellation/ownership, destination-bound fragments |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | Measured position capture/restore, initial echo guard, document-scoped list, Reader-exit cancellation |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometry.kt` | Pure line/character mapping and bounded composite fallback |
| `app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderContinuationFixTest.kt` | 7 regressions: retained failures, full address updates, old-document observations, exit cancellation, fragment handoff |
| `app/src/test/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometryTest.kt` | 4 mapping regressions: font layout, text origins, fallback bounds, disposed metrics |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderLogicalPositionPageTest.kt` | 2 device scenarios: actual long-paragraph visible-line reopen and TXT in-block page turns; compiled, NOT_RUN |

Existing unrelated workspace work is preserved. CHANGELOG, reader architecture, and REQ-0003/0009 follow-up evidence are updated in both languages; requirement status is not advanced to DONE.

### Verification

| Check | Actual result |
| --- | --- |
| Pre-fix reproduction | FAIL as expected: 32 executed, 3 retained failures |
| Final direct JVM regression run | PASS: 43 executed, 0 failures; retained audit cases plus existing product regressions and the 11 new JVM tests |
| Changed-file ktlint 1.0.1 | PASS; function-naming uses the app's existing Compose exception |
| Full/Eink main Kotlin/Compose compilation | PASS with Kotlin 2.0.21 and the pinned Compose compiler plugin, existing resolved lint-model dependency jars/R jars and per-flavor generated BuildConfig |
| Full/Eink androidTest Kotlin compilation | PASS; scenarios are not claimed executed |
| Standard Gradle gate | Could not start: `FileLockContentionHandler` → `java.net.SocketException: Operation not permitted` in the current sandbox |
| APK assembly, Gradle lint/full suite, device/UI/E-Ink hardware | NOT_RUN; no APK/device pass or full acceptance claimed |

The first changed-file format run reported two test-source line-length violations; those were corrected and the final format/check returned exit 0. Existing deprecation/opt-in/image-loader warnings in other source remain. Supporting unchanged core/model/parser jars came from prior local builds; affected ViewModels/UI/helper sources were freshly compiled. Compiler outputs are temporary verification artifacts, not distributable APKs.

The attempted standard gate was:

```bash
./gradlew --offline --no-daemon \
  --project-cache-dir /tmp/leaflet-fix.tAUbh0/gate-cache \
  :app:testFullDebugUnitTest :app:testEinkDebugUnitTest \
  :app:assembleFullDebug :app:assembleEinkDebug lint ktlintCheck
```

`JAVA_HOME` pointed to JDK 17, and `GRADLE_USER_HOME` / Android user state were under the isolated temporary directory. Follow-up device commands after the environment allows them:

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLogicalPositionPageTest
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderLogicalPositionPageTest
```

The three findings are fixed with JVM/compile proof, pending device acceptance. This is an implementation response, not a separate independent reviewer closure or a product release. No commit was made.

Final source identities:

| Fixed source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `fb97c8e1b24065b4af376cdbc80b0658f609ac4af668c1716534320531428631` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `a44d2ba7a42cef979b3188f491e990bc296b3ac6528a1ffa8f88b1ddd470748a` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/BlockPositionGeometry.kt` | `cdc8de530b5a2184c190abd7c1ccc5aae1e943a8c0fa62e366b2043501272cb7` |

### Artifacts and Cleanup

Temporary baseline copies, compiler/formatter scripts, copied/extracted dependencies, Gradle wrapper/user-home files, logs, and compiled classes were created only under `/tmp/leaflet-fix.tAUbh0` and deleted at completion. The new helper and three product test files are intentional repository deliverables at the paths listed above. Existing report evidence, screenshots, workspace changes, and prior build artifacts were preserved; no unrelated file was deleted.
