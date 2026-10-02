# Concurrency and Scan-Safety Review — 2026-10-01, Round 2

English | [中文](code-review-2026-10-01-round-2.zh.md)

**Latest implementation response (2026-10-01): the round-3 active-write eviction, in-block position, and stale-link findings are fixed with 43 passing focused JVM cases and both-flavor source compilation; device/full Gradle acceptance remains pending.** See the [round-3 review and repair evidence](code-review-2026-10-01-round-3.md). Earlier independent results and executor responses remain historical records.

## Conclusion and Scope

Four additional defects were independently reproduced: two P1 auxiliary-data failures and two P2 state-publication/navigation failures. All remain OPEN in the reviewed working-tree snapshot. Original document files are untouched by the identified paths. This review makes no product-source changes.

> **Follow-up status (2026-10-01):** all four findings were fixed on top of the reviewed snapshot. The retained reproductions were integrated into the product suites as JVM regressions; the save-ordering case reproduced exactly (`[5, 5, 2]`) before the change and passes after it. See [Resolution](#resolution-2026-10-01-round-2-follow-up) at the end of this report.
>
> **Second follow-up (2026-10-01, after the independent acceptance):** the independent acceptance rejected CR-20261001-04 (sequence allocated in the worker instead of at invocation) and CR-20261001-05 (a row missing only its MIME type was still treated as a file). Both rejections were correct for the snapshot they pinned. Both mechanisms are now fixed, their exact `AcceptanceBoundariesTest` counterexamples are integrated into the product suites and pass, and the new source identities are recorded in [Executor response to the independent acceptance](#executor-response-to-the-independent-acceptance-2026-10-01).
>
> **Third follow-up (2026-10-01, after the invocation/type acceptance):** that acceptance rejected the two fixes again, correctly: the compensating write was itself unsequenced, and the production cursor parser rebuilt the entry without `typeUnknown`, dropping the marker before the scanner saw it. Both are fixed; all three acceptance counterexamples are integrated into the product suites and pass. See [Executor response to the third acceptance](#executor-response-to-the-third-acceptance-2026-10-01).

HEAD is still `5a754292c1ea02f33af0cb917f5cf24c29dbb763`; the snapshot includes the uncommitted fixes responding to the [previous review](code-review-2026-10-01.md). Its three cross-document save-snapshot baseline tests pass. The new same-document write-order failure is a distinct mechanism, not a duplicate of the old mutable-block capture finding. Other previously reported fixes are not independently closed by this round.

Scope: concurrent reading-position writes, asynchronous Library/Recent state, and production cursor classification feeding scanner deletion decisions. Line numbers and identities refer to the snapshot below; this is not exhaustive repository acceptance.

| ID | Priority | Finding | Reproduced result | Status |
| --- | --- | --- | --- | --- |
| CR-20261001-04 | P1 | An older same-document save overwrites a newer jump and exit flush | Writes `[5, 5, 2]`; final block expected 5, actual 2 | FIXED — invocation-time ordering, and every completion coalesces to that document's newest address; all three acceptance counterexamples pass |
| CR-20261001-05 | P1 | Missing directory metadata can authorize deletion of its old subtree index | Directory classified `false`; deleted count expected 0, actual 1 | FIXED — unknown type is explicit and survives the parser projection; all three acceptance counterexamples pass; real-provider run NOT_RUN |
| CR-20261001-06 | P2 | A slow earlier Library open replaces the current Library | Selection expected `b`, actual `a` | FIXED — JVM regressions PASS; refresh-completion case needs a scanner fake, NOT_RUN |
| CR-20261001-07 | P2 | Completed Recent metadata does not update observable rows | Health lookup finished; expected `AUTH_LOST`, actual `UNKNOWN` | FIXED — JVM regressions PASS; visual hints NOT_RUN |

## CR-20261001-04 — Immutable Saves Still Complete Out of Order

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:407–414`, `804–835`.
- **Trigger:** Save block 2 in A, hold that write inside the ReaderStore, jump to block 5, and complete a block-5 exit flush. Release the older write afterward, without changing documents or load generation.
- **Impact and proof:** [ConcurrencyReviewTest.kt](review-evidence/2026-10-01-round-2/ConcurrencyReviewTest.kt), `anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush`, confirms the flush completed before the old write was released. Writes occur as `[5, 5, 2]`, leaving block 2 as the stored address. The newest position can therefore be lost even after a successful flush.
- **Cause:** Every `queueSave()` launches an independent IO coroutine. Immutable snapshots protect identity, but neither serialize writes in request order nor prevent stale completion. All three writes satisfy `isCurrent()` because they share the same document and session generation; the older completion may also replace `currentStored`.
- **Minimal direction and closure:** Use a serialized writer preserving request order, or atomic sequence-aware persistence, with flush awaiting the latest accepted address. A mutex acquired only after unordered dispatch is not enough to establish invocation order. Cover overlapping jumps, throttled scroll saves, exit flush, failed writes, and document switches. The retained failure must turn green without reopening the fixed cross-document capture bug. This is ReaderStore-level JVM evidence, not a real Room/device run.

## CR-20261001-05 — Malformed Directory Becomes a File and Enables Cleanup

- **Source:** `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:89–97`; `LibraryScanner.kt:223–233`, `359–365`.
- **Trigger:** A directory was previously indexed successfully. A later provider cursor still supplies its directory MIME type and document ID, but returns an empty display name.
- **Impact and proof:** [ScanMetadataReviewTest.kt](review-evidence/2026-10-01-round-2/ScanMetadataReviewTest.kt) first indexes `folder/keep.md`, then feeds a cursor row with the same directory identity, empty name, and MIME `vnd.android.document/directory` through the actual cursor parser. It produces `isDirectory=false`. The next scan deletes the previously indexed document: expected `deleted=0`, actual `deleted=1`. The scanner cannot prove the subtree vanished. Source files are not deleted; real Room's document-to-reading-position CASCADE means auxiliary position loss is an additional source-supported risk, not a separately executed assertion.
- **Cause:** The missing-ID/name branch builds `TreeEntry("", name, false, ...)` before reading MIME, discarding available directory/identity information. The scanner suppresses cleanup only for malformed entries marked as directories. Production classification therefore bypasses the protection asserted by tests that directly construct `TreeEntry(..., isDirectory=true)`.
- **Minimal direction and closure:** Preserve known type/identity when metadata is incomplete and treat an unclassifiable/un-enumerable subtree as insufficient absence evidence. Continue useful indexing while retaining affected old rows; do not turn partial enumeration into disappearance cleanup. Verify missing name, missing ID, and missing MIME through the real provider/cursor path, including directory rows and reading positions.
- **Evidence limit:** The test recompiled the production cursor parser and scanner. A proxy Cursor and test-only allocation/reflection invoke the malformed-row branch without constructing an Android ContentResolver; that branch does not use the null tree URI. Repository/scanner fakes are the existing project test implementations. Provider IPC, valid-row URI construction, and real SQLite cascade behavior were not run.

## CR-20261001-06 — Earlier Library Load Can Replace Later Selection

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt:137–157`.
- **Trigger and proof:** Begin loading A, delay its document snapshot, return and open B, and let B finish. Then finish A. `aSlowEarlierLibraryOpenMustNotReplaceTheCurrentLibrary` confirms B was selected before releasing A; afterward `browse.libraryId` is `a` instead of `b`.
- **Impact:** The current route can display the earlier Library's document rows and path state. This is wrong-target navigation/state, not unauthorized source access.
- **Cause:** `openLibrary()` starts untracked jobs without cancelling an earlier open or checking a selection generation before `_browse.update`. A late completion also replaces the progress subscription. Cancelling a progress collector after the stale state write does not cancel the old metadata load.
- **Minimal direction and closure:** Track/cancel the active browse load and validate its current selection before publishing metadata/progress. Apply the same ownership check to refresh completions. Cover slow A → fast B, back navigation, cancelled loads, and refresh completion after switching. The test exercises the actual ViewModel with a delayed LibraryListSource; no Compose/Room/device run is claimed.

## CR-20261001-07 — Recent Metadata Completion Is Not Observable

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt:71–90`, `134–158`; health hints are rendered by `RecentSection.kt`.
- **Trigger and proof:** Emit one recent entry, suspend library-name resolution long enough for `rows` to publish, then complete the name and return `AUTH_LOST` from the health query without changing the MRU list. `completedRecentMetadataMustPublishHealthAndLibraryName` confirms the health query ran, but the row remains `UNKNOWN`; its already-published library name remains null.
- **Impact:** The UI can keep showing an unknown Library and omit the authorization-lost or missing-file recovery hint even after successful metadata lookup. An unrelated later entry-list change may hide the defect by rebuilding rows from the cache.
- **Cause:** `rows` reacts only to `_entries`. The collector publishes entries before calling `refreshMeta()`, which updates ordinary mutable maps. Cache completion emits no StateFlow update; equal entry lists do not force a new emission either.
- **Minimal direction and closure:** Publish immutable metadata state and combine it with entries, or rebuild observable rows when lookups finish. Protect completion against removal/list changes and define when cached health is invalidated. Cover slow name/health queries with an unchanged entry list, actual `AUTH_LOST`/`GONE` hints, and removed records. The current proof is ViewModel/JVM-level; actual visual hints remain unverified on device.

## Verification and Identity

Environment: macOS ARM64, JDK 17.0.9, Kotlin compiler 2.0.21, Coroutines/Test 1.10.2, JUnit 4.13.2. Sources were copied to `/tmp/leaflet-review3.kEd4Fm/repo`. The affected ViewModels, cursor parser, scanner, repository, and existing storage fakes were freshly compiled from that snapshot. Existing local core/model/parser build jars supplied unchanged dependencies; a source-level parser recompile and full build are not claimed.

| Check | Result |
| --- | --- |
| Initial direct compilation | Failed because the review's source list omitted existing `BrowseLevel.kt`, leaving `childPrefix` / `parentPrefix` unresolved. Adding that existing dependency source corrected the review setup; it was not a product defect or product edit. |
| Corrected direct compilation | PASS, exit 0. |
| New controlled regressions | 4 executed, 4 failures reproducing the findings above. |
| Existing `ReaderViewModelSaveSnapshotTest` | 3 executed, 3 passes: the previous cross-document capture fix and sequential baseline still hold. |
| Combined direct JUnit invocation | `Tests run: 7, Failures: 4`, exit 1. The run proves failures; it is not a passing gate. |
| Full Gradle, lint, ktlint, APK/device/hardware checks | NOT_RUN. The prior round already established the current sandbox's `FileLockContentionHandler` socket restriction; normal Gradle was not retried. No new UI or device acceptance claim is made. |

JUnit entry points executed after compilation:

```bash
java -cp "$REVIEW_CLASSES:$REVIEW_DEPENDENCY_CLASSPATH" org.junit.runner.JUnitCore \
  io.leaflet.reader.review.ConcurrencyReviewTest \
  io.leaflet.reader.review.ScanMetadataReviewTest \
  io.leaflet.reader.ui.reader.ReaderViewModelSaveSnapshotTest
```

`REVIEW_CLASSES` denotes the temporary fresh compiler output; `REVIEW_DEPENDENCY_CLASSPATH` includes local project jars, the pinned JVM test dependencies, lifecycle 2.8.7, and the Android 35 API jar. The retained evidence is not installed in the app test source set. Integrate it into the relevant suite when implementing fixes, then run the applicable Gradle and real-provider/device regressions.

| Snapshot source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `d72622a917c4b28f181cca276af9ce48b2f1542c4af3ffba33f3127ae08cf3f6` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `0aa94ae4c7c98ed4a8b40e4ab60b8312cb384797559b8ac7375087fecb239b61` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `301ee0c4f1759efc81f56d8729b043e7dfd5c2a49941f744b5b2a5e82efef743` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `eaf8e3fb65e038a97f83db19db30f3403acae2dc3ad331438cdf334a3d44a521` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `a198ef95b0daa82289dca380492f76fde8f92b18623ddcbef24b8b76a9ee08c9` |

## Artifacts and Cleanup

- Temporary snapshot, extracted/copied dependencies, compiler outputs, and logs were created only under `/tmp/leaflet-review3.kEd4Fm` and deleted after verification.
- Useful reproduction sources are deliberately retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-round-2/`: `ConcurrencyReviewTest.kt` and `ScanMetadataReviewTest.kt`. They support this audit and add no product dependency or build wiring.
- This English report and its complete Chinese pair are the documentation deliverables. Existing source changes, historical reports, and workspace build artifacts were preserved. No product fix or commit was made by this review.

<a id="resolution-2026-10-01-round-2-follow-up"></a>

## Resolution (2026-10-01, round 2 follow-up)

All four findings were fixed on top of the reviewed snapshot. The retained reproductions were
integrated into the product JVM suites (new files, so they do not collide with the concurrent
UX-07/REQ-0012 work): `ReaderSaveOrderTest`, `TreeRowClassificationTest`,
`LibraryBrowseSelectionTest`, and `RecentMetadataPublicationTest`. The save-ordering case was run
before the fix and reproduced the report exactly (`writes=[5, 5, 2]`, `expected:<5> but was:<2>`);
all of them pass after it, together with the previous round's regressions. Real Room/SAF/provider and
device verification remain NOT_RUN.

**CR-20261001-04 — save completion order.** Immutable snapshots fixed identity but not ordering, so
each write now carries a monotonically increasing request sequence from `queueSave`. The store write
still runs concurrently — a newer address is never blocked behind a slow older one, which the review's
own scenario requires — and ordering is repaired on completion:

- a write that completes while a NEWER request exists does not publish the save throttle or the
  descriptor/stored-position caches (`isNewestRequest`);
- when that newer write has already landed, the older completion re-persists the newest accepted
  address, because it may have just overwritten it;
- `flushPosition()` still returns the job of its own write, so callers that join it keep the
  documented "write before process death" behaviour.
Regressions (JVM, executed): `ReaderSaveOrderTest.anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush`
plus the three `ReaderViewModelSaveSnapshotTest` cases from the previous round, which must keep
passing so the fixed cross-document capture bug is not reopened.

**CR-20261001-05 — malformed directory metadata.** Two rules, at the two layers that own them:

- `TreeLister` classifies every row from the columns that ARE present: the extracted pure
  `classifyTreeRow` reads the MIME type before the "missing metadata" decision, so a folder that
  lost its display name (or id) stays a directory instead of becoming an empty "file";
- `LibraryScanner` now treats ANY row it could not fully classify as insufficient absence evidence
  for the walk (`subtreeSkipped`), not only rows that already claimed to be directories. A row that
  is neither provably a directory nor provably absent — the missing-MIME case — can no longer turn
  partial enumeration into a disappearance cleanup; the previous rows are kept and the run still
  succeeds with entry errors reported.
Regressions (JVM, executed): `TreeRowClassificationTest` (5 cases: nameless directory, identity-less
directory, unclassifiable row, and both scan scenarios asserting `deleted = 0` with the old document
row retained) and the existing 33 `LibraryScannerTest` cases. The review's cursor-proxy variant of the
parser path and the instrumented `realListerMissingColumnsAreEntryErrorsNotAbort` stay NOT_RUN here.

**CR-20261001-06 — Library selection ownership.** `openLibrary` keeps its load job, bumps a browse
generation, and cancels the previous load together with any running scan refresh; it publishes its
rows, path state, and progress subscription only while its generation is still current. `refresh`
checks that its library is still the selected one before publishing progress, metadata, or a
cancelled/failed status, so a slow earlier load or refresh cannot replace the library the user
opened. Regressions (JVM, executed): `LibraryBrowseSelectionTest` — slow A → fast B keeps B selected
and B's rows, and re-opening the same library still publishes. The refresh-completion scenario needs a
scanner fake that the app module does not have (the storage fakes live in `:core:storage`'s test
source set); it is covered by the shared ownership check and stays NOT_RUN as a runnable test.

**CR-20261001-07 — Recent metadata publication.** The library name and file health are now immutable
observable state (`RecentMeta`) combined with the entry list, so a finished lookup updates the
published rows. The entry list also publishes immediately and metadata lookups run in a job that the
next list supersedes, so a slow provider cannot delay new entries and a lookup that finishes after its
entry disappeared cannot republish it; cached metadata is pruned to the currently listed entries.
Regressions (JVM, executed): `RecentMetadataPublicationTest` — a completed name+health lookup updates
`rows` (the review's failing case), and a lookup completing after removal does not resurrect the row.
The actual authorization-lost/missing-file hints on screen remain NOT_RUN.

<a id="independent-acceptance-2026-10-01"></a>

## Independent Acceptance — 2026-10-01

Scope is CR-20261001-04 through CR-20261001-07 only. The current working tree, including the executor's fixes, was copied to `/tmp/leaflet-accept.8x8FT9/repo`. Related production sources and focused tests were freshly compiled with the same local Kotlin/JVM toolchain used by the review. Current browse helpers were included solely as compiler dependencies; no new-requirement behavior was accepted or implemented.

### Verdict by Finding

| Finding | Independent result | Evidence and remaining gate |
| --- | --- | --- |
| CR-20261001-04, P1 save ordering | REJECTED; remains OPEN | The original completion-order regression passes, but reversed IO start order still leaves `[5, 5, 2]`. Request ordering is not preserved. |
| CR-20261001-05, P1 incomplete metadata | REJECTED; remains OPEN | The missing-name/ID cases pass. A row missing only MIME, with ID/name intact, still deletes the old subtree index (`expected 0, actual 1`). |
| CR-20261001-06, P2 Library selection | PASS at JVM level; device acceptance pending | Original slow A → fast B reproduction and two product tests pass. An additional real-scanner/fake-storage test confirms late refresh metadata/progress cannot replace B. |
| CR-20261001-07, P2 Recent publication | PASS at JVM level; device acceptance pending | Original reproduction and both product tests pass: completed metadata updates the row; removal prevents a late lookup from republishing it. Visual recovery hints remain NOT_RUN. |

The result does not close all four findings. In particular, a green reproduction of one IO completion schedule does not establish save ordering for other schedules, and tests that remove both name and MIME do not establish the missing-MIME-only rule already required by this report.

### CR-20261001-04 Rejection — Sequence Is Allocated After Dispatch

Source: `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:438–443`.

`saveRequestSeq` is incremented inside the launched IO coroutine. Its sequence therefore records IO start order, rather than the invocation order of `jumpToBlock`/`flushPosition`. An earlier invocation that starts later is incorrectly promoted to the newest request and is not repaired.

[AcceptanceBoundariesTest.kt](review-evidence/2026-10-01-acceptance/AcceptanceBoundariesTest.kt), `newestRequestedPositionMustWinWhenIOJobsStartInReverseOrder`, holds IO dispatch, invokes block 2 → block 5 → flush block 5, then releases queued work newest-first. The flush completes, yet the last stored block is 2. Direct JUnit output:

```text
invocation order must win, not IO-start order; writes=[5, 5, 2]
expected:<5> but was:<2>
```

This is another schedule of the same save-ordering acceptance condition, not a new requirement. The original post-store completion-order case is fixed, and the previous cross-document snapshot tests also remain green. Closure still needs request-time ordering that survives IO dispatch and completion reordering; assigning the order only in the worker cannot provide that guarantee.

### CR-20261001-05 Rejection — Unknown MIME Is Still Treated as a File

Sources: `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:64–79` and `LibraryScanner.kt:223–237`.

`classifyTreeRow("dir-id", "folder", null, ...)` returns a non-directory row with valid ID/name. The scanner's incomplete-row guard checks only blank ID/name, so this row does not mark the walk incomplete. `folder` has no supported extension, is skipped as a file, and the previously indexed subtree is cleaned up as absent.

The second test in [AcceptanceBoundariesTest.kt](review-evidence/2026-10-01-acceptance/AcceptanceBoundariesTest.kt) first indexes `folder/keep.md`, then removes only the MIME value from the parent's classified row. It fails with:

```text
a row with ID/name but unknown MIME cannot prove a subtree vanished
expected:<0> but was:<1>
```

The executor's five `TreeRowClassificationTest` cases pass, but their unknown-MIME scan case also removes the name; it enters the blank-name guard and misses this boundary. Classification reliability must reach the scanner even when name and identity remain present. The old requirement to retain rows when a subtree cannot be proven absent still applies. This JVM test demonstrates index deletion; real SQLite position cascade and provider IPC remain NOT_RUN.

### Passing Checks and Limits

| Check | Freshly executed result |
| --- | --- |
| Retained `ConcurrencyReviewTest` | PASS, 3/3 original ViewModel reproductions. |
| `ReaderViewModelSaveSnapshotTest` | PASS, 3/3 prior snapshot baselines. |
| `ReaderSaveOrderTest` | PASS, 1/1 original post-store completion schedule. |
| `TreeRowClassificationTest` | PASS, 5/5 supplied classification/scan cases. |
| `LibraryBrowseSelectionTest` | PASS, 2/2 supplied selection/reopen cases. |
| `RecentMetadataPublicationTest` | PASS, 2/2 completion/removal cases. |
| `AcceptanceBoundariesTest` | FAIL, 2/2 acceptance counterexamples above. |
| `LibraryRefreshAcceptanceTest` | PASS, 1/1 late refresh check. |
| Total | 19 executed: 17 passed, 2 failed. The 18-test combined run returned exit 1; the separate refresh run returned exit 0. |
| Gradle, lint, APK/flavor builds, provider/Room/device/UI gates | NOT_RUN here; the previously documented sandbox socket restriction remains. No full acceptance or new build pass is claimed. |

[LibraryRefreshAcceptanceTest.kt](review-evidence/2026-10-01-acceptance/LibraryRefreshAcceptanceTest.kt) uses the actual `LibraryScanner` and repository with existing storage fakes. A's refresh pauses at its document-snapshot publication boundary in non-cancellable query work; B is selected before A is released. B's library ID, title, document rows, and progress ownership remain intact afterward. This fills the runnable refresh-ownership gap identified in the executor response, without a new scanner abstraction or product edit.

Both compiler invocations passed. Production ViewModel/scanner/repository sources were freshly compiled; unchanged local core/model/parser build jars supplied supporting dependencies. The original cursor-proxy reproduction was not rerun because the new parser's URI construction requires an Android runtime; the current classification and scan tests provide the stated JVM-level evidence only.

### Tested Snapshot and Artifacts

HEAD remains `5a754292c1ea02f33af0cb917f5cf24c29dbb763`. These identities pin the tested working-tree sources, rather than the older snapshot above:

| Tested source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `eb0c23e262029bbeae3259f7f3465b06b37bb0cfc2fdc7a962880711d025cefd` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `04028926ec2b0186354366debade9c41d72676dfac84f13e5758cbf5eab48338` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `de435a2fdfb98299e59b1feaa072a71a33491ad0959256afbff78970193db4ac` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `545d67878f37272c0cd2c78d7cc8a549bd4ba5208ea94c59ab203d18343f3ad1` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `31f7d1e7ddc476fbee5c8d1280f04e4ae124e313aad2aaf33e37a6130940b75b` |

Temporary snapshot, extracted/copied dependencies, compiled classes, and logs under `/tmp/leaflet-accept.8x8FT9` were deleted after verification. Two valuable fixtures are retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-acceptance/`: `AcceptanceBoundariesTest.kt` and `LibraryRefreshAcceptanceTest.kt`. They are audit evidence, are not wired into the product build, and add no dependency. This acceptance updates only this report pair and retains those fixtures; concurrent implementation files and workspace build artifacts are preserved.

<a id="independent-recheck-2026-10-01"></a>

## Independent Recheck — 2026-10-01

The user requested continued acceptance of these four findings and excluded new requirements. The verdict is unchanged. The source identities for `ReaderViewModel`, `RecentViewModel`, `TreeLister`, and `LibraryScanner` exactly match the preceding acceptance table. CR-20261001-04/05 therefore still have the same failing implementations; their existing counterexamples were reused, not counted as freshly executed tests. CR-20261001-07's JVM publication result remains applicable to its unchanged source.

`LibraryViewModel.kt` changed to SHA-256 `f4021db0541ac5a23e583dfb676e5d02d1e4b3b078fa186b63a3ef44a8db7579`. Its current source and supporting browse/scanner/repository sources were freshly compiled in an isolated `/tmp/leaflet-reaccept.N5qXle` snapshot. Only the existing selection-ownership tests and retained late-refresh test were run; new-requirement behavior was outside acceptance scope.

| Finding / check | Recheck result |
| --- | --- |
| CR-20261001-04, save ordering | REJECTED / OPEN; identical source still allocates request sequence after IO dispatch. Previous reversed-start failure remains applicable. |
| CR-20261001-05, missing MIME | REJECTED / OPEN; identical classifier/scanner still permit cleanup when only MIME is missing. Previous deletion failure remains applicable. |
| CR-20261001-06, Library selection | PASS at JVM level; `LibraryBrowseSelectionTest` 2/2 and retained `LibraryRefreshAcceptanceTest` 1/1 freshly pass against the changed ViewModel. |
| CR-20261001-07, Recent publication | Previous JVM PASS reused for matching source; visual/device acceptance remains pending. |
| Fresh compilation and direct JUnit run | Both exit 0; `OK (3 tests)`. This is the new execution count, not another 19-test run. |
| Gradle, builds, provider/Room/device/UI gates | NOT_RUN; no new environment or device evidence supports closing these remaining gates. |

The executed JUnit classes were `io.leaflet.reader.ui.library.LibraryBrowseSelectionTest` and `io.leaflet.reader.review.LibraryRefreshAcceptanceTest`, using JDK 17.0.9, Kotlin compiler 2.0.21, Coroutines/Test 1.10.2, and JUnit 4.13.2. Supporting dependencies came from existing local build/cache jars. No product source, test source set, or new requirement was edited by this recheck.

The temporary source copies, extracted/copied dependencies, compiled classes, and logs under `/tmp/leaflet-reaccept.N5qXle` were deleted after verification. Only this report pair was updated; the existing evidence fixtures remain at their previously documented repository paths. No additional retained fixture or workspace build output was created.

<a id="executor-response-to-the-independent-acceptance-2026-10-01"></a>

## Executor Response to the Independent Acceptance — 2026-10-01

Both rejections were correct for the snapshot they pinned (`ReaderViewModel.kt`
`eb0c23e2…25cefd`, `TreeLister.kt` `545d6787…f3ad1`, `LibraryScanner.kt` `31f7d1e7…40b75b`). The two
mechanisms are now fixed, and the acceptance counterexamples themselves are integrated into the
product test suites and pass.

**CR-20261001-04 — the sequence is allocated at invocation time.** `queueSave` now allocates the
request sequence synchronously on the caller's thread, before `viewModelScope.launch`, through
`nextSaveSequence`. Sequence order is therefore invocation order and can no longer be decided by which
IO worker happens to start first. The bookkeeping moved from a coroutine `Mutex` to a `synchronized`
block for exactly this reason: it must be ordered at call time, not from inside a coroutine. The rest
of the policy is unchanged — writes stay concurrent, a completion that is no longer newest publishes
neither the save throttle nor the position caches, and an older completion that landed after a newer
write re-persists the newest accepted address.

The acceptance counterexample is now a product regression:
`app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderSaveOrderDispatchTest.kt` holds IO dispatch,
invokes block 2 → block 5 → the exit flush, and releases the queued work newest-first. Against the
rejected revision it printed

```text
invocation order must win, not IO-start order; writes=[5, 5, 2]
expected:<5> but was:<2>
```

and it passes now, together with `ReaderSaveOrderTest` (post-store completion order) and the three
cross-document `ReaderViewModelSaveSnapshotTest` baselines.

The repair target is tracked PER DOCUMENT (bounded to the eight most recently requested documents), so
a document switch between two overlapping writes cannot redirect an older completion's repair at the
other document's request. `ReaderSaveOrderTest.aDocumentSwitchMustNotRedirectTheRepairToTheOtherDocument`
covers exactly that: a slow A write for block 2, a fast A jump to block 5, a switch to B, a B save,
and then the late A completion — A must end at 5 and B must keep its own address.

**CR-20261001-05 — an unknown type is explicit, and never a file.** `TreeEntry` gained
`typeUnknown` (default `false`, so existing constructions are unchanged) and `classifyTreeRow` sets it
whenever the row states no MIME type. `LibraryScanner` treats `typeUnknown` exactly like missing
identity: the row is reported as an entry error (with its own reason), it is never indexed as a
document, and the walk's absence evidence becomes insufficient — cleanup is skipped while the run
still succeeds and keeps the previous rows. A row that keeps its ID and display name and only loses
its MIME can therefore no longer stand in for a vanished folder.

The acceptance counterexample is now a product regression:
`core/storage/src/test/kotlin/io/leaflet/storage/scan/TreeRowClassificationTest.kt` gained
`missingOnlyMimeMustNotDeleteThePreviouslyIndexedDirectory` (the scan case, which printed
`expected:<0> but was:<1>` against the rejected revision) and `anUnknownMimeRowIsNotClassifiedAsAFile`
(the classifier case). Both pass, together with the five earlier cases and the existing 33
`LibraryScannerTest` cases.

**Current source identities** (superseding the rejected table above):

| Fixed source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `45ad5735b077b2ad4376ede435bb94b7502f58357e2808c7563e618d9dec086f` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `9cc7d436def324bce5ebad33c6cfa65e3c5cf14491d73a8cb0a4c696e558d99a` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |

**Gate run in the product workspace** (2026-10-01, after the fixes): `./gradlew test` executed 852 JVM
cases (350 unique methods) with 0 failures, 0 errors and 0 skipped across all four app variants and
the core modules; `lint` and `ktlintCheck --rerun-tasks` passed; `:app:assembleFullDebug`,
`:app:assembleEinkDebug`, and both flavors' androidTest compilation passed
(`app-full-debug.apk` 28,827,351 B, `app-eink-debug.apk` 28,386,595 B).

**Still NOT_RUN, and not claimed by this response:** the independent re-run of
`AcceptanceBoundariesTest` and `LibraryRefreshAcceptanceTest`, real Room/SAF/provider behaviour
(including the position cascade and provider IPC), device/UI acceptance, and the E-Ink hardware
matrix. CR-20261001-06 and CR-20261001-07 were already accepted at JVM level by the independent
acceptance and are unchanged here.

<a id="invocation-type-fix-acceptance-2026-10-01"></a>

## Independent Acceptance of the Invocation/Type Fixes — 2026-10-01

Scope remains CR-20261001-04 through CR-20261001-07. The new invocation-time sequencing and explicit `typeUnknown` fixes were independently tested from a fresh working-tree snapshot under `/tmp/leaflet-finalaccept.f3Weli/repo`. Their exact previously failing acceptance cases now pass, but two checks of the complete repair/classification paths fail. These are the same latest-position and incomplete-classification conditions; no new requirement or product implementation was added.

### Current Verdict

| Finding | Result | Evidence |
| --- | --- | --- |
| CR-20261001-04, P1 save ordering | REJECTED / OPEN | Reverse IO start order, the original completion schedule, and repair ownership across a document switch pass. A delayed repair still overwrites a newer jump/flush. |
| CR-20261001-05, P1 scan safety | REJECTED / OPEN | The classifier/scanner's missing-MIME-only cases pass. Production cursor projection loses `typeUnknown`, resetting it to false. |
| CR-20261001-06, Library ownership | PASS at JVM level; device pending | Original delayed load, selection/reopen, and late refresh metadata/progress tests freshly pass. |
| CR-20261001-07, Recent publication | PASS at JVM level; device pending | Original publication and product completion/removal tests freshly pass. |

### CR-20261001-04 — A Repair Can Become a Stale Write

Source: `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:493–497`.

Sequence allocation now precedes dispatch and repair is scoped per document. However, the one-shot `persist(repair.second, ...)` is not followed by another freshness/repair decision. A newer request can finish while that compensating write is still inside the store.

[RepairAndProjectionAcceptanceTest.kt](review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt), `aCompensatingWriteMustNotOverwriteAnEvenNewerJumpAndFlush`, executes:

1. Hold block 2's original write; block 5 completes.
2. Release block 2. It lands and triggers a block-5 repair; hold that repair inside the store.
3. Jump to block 6 and complete its exit flush. The stored address is 6 and the flush job is complete.
4. Release the block-5 repair. All released work settles, leaving address 5.

```text
repair must obey the same latest-request rule; writes=[5, 2, 6, 6, 5]
expected:<6> but was:<5>
```

The cache sequence guard only prevents stale in-memory publication; it does not undo the stale store write. Repairs must obey the same ordering guarantee as original writes. This counterexample must pass together with the now-green dispatch and cross-document tests before closure. The test uses the freshly compiled production ViewModel and a controlled ReaderStore, not real Room/device execution.

### CR-20261001-05 — Cursor Projection Drops the Unknown-type Flag

Source: `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt:148–156`.

`classifyTreeRow` now sets `typeUnknown`, and `LibraryScanner` checks it. `ContentResolverTreeLister.readEntries` reconstructs `TreeEntry` from the classifier's fields but omits `typeUnknown`. The field takes its default `false`, so the marker is lost before the scanner receives a production entry.

The second test in [RepairAndProjectionAcceptanceTest.kt](review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt) runs the actual freshly compiled private cursor parser with a proxy Cursor. The classifier returns true, while the parser's final entry returns false:

```text
cursor projection must preserve the classifier's unknown-type flag
expected:<true> but was:<false>
```

The fixture deliberately omits the ID to avoid Android URI construction; that row is still protected by the blank-ID guard, so this execution proves flag loss, not another index deletion. The same unconditional reconstruction serves valid-ID/name rows. For that MIME-only case, source inspection confirms the lost marker again bypasses the unknown-type guard and permits treating an unvisited directory as a file. The URI branch does not restore the flag. Real valid-ID URI construction, provider IPC, and SQLite position cascade remain NOT_RUN.

Closure requires retaining classification reliability through the final emitted entry, rather than only in the helper. Test-only allocation/reflection avoids ContentResolver construction; no original document or provider is accessed.

### Verification, Identity, and Cleanup

Affected ViewModels, cursor parser, scanner, repository, storage fakes, and focused tests were freshly compiled with JDK 17.0.9 / Kotlin 2.0.21; compilation returned exit 0. Supporting unchanged project/parser jars and lifecycle 2.8.7, Coroutines/Test 1.10.2, and JUnit 4.13.2 came from existing local builds/caches.

| Executed class | Result |
| --- | --- |
| Retained `ConcurrencyReviewTest` | PASS 3/3 |
| `ReaderViewModelSaveSnapshotTest` | PASS 3/3 |
| `ReaderSaveOrderTest` | PASS 2/2 |
| `ReaderSaveOrderDispatchTest` | PASS 1/1 |
| `RecentMetadataPublicationTest` | PASS 2/2 |
| `LibraryBrowseSelectionTest` | PASS 2/2 |
| `TreeRowClassificationTest` | PASS 7/7 |
| Retained `AcceptanceBoundariesTest` | PASS 2/2; both preceding rejection cases now pass |
| Retained `LibraryRefreshAcceptanceTest` | PASS 1/1 |
| `RepairAndProjectionAcceptanceTest` | FAIL 2/2 |
| Total direct JUnit run | 25 executed, 23 passed, 2 failed; exit 1 |
| Gradle, wider gates, real Room/SAF/provider/device/UI/E-Ink checks | NOT_RUN independently; no full closure claimed |

The focused failures are sufficient to reject the two P1 closures, so no unrelated or broader suite was added afterward. Executor workspace gate results above remain executor evidence.

| Tested source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `45ad5735b077b2ad4376ede435bb94b7502f58357e2808c7563e618d9dec086f` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `9cc7d436def324bce5ebad33c6cfa65e3c5cf14491d73a8cb0a4c696e558d99a` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `f4021db0541ac5a23e583dfb676e5d02d1e4b3b078fa186b63a3ef44a8db7579` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/RecentViewModel.kt` | `04028926ec2b0186354366debade9c41d72676dfac84f13e5758cbf5eab48338` |

Temporary snapshot, extracted/copied dependencies, compiler outputs, and logs under `/tmp/leaflet-finalaccept.f3Weli` were deleted after verification. The useful fixture is retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-acceptance-2/RepairAndProjectionAcceptanceTest.kt` as report evidence, without product build wiring. Only this report pair and that fixture were written by this acceptance. Concurrent source changes and existing artifacts were preserved.

<a id="executor-response-to-the-third-acceptance-2026-10-01"></a>

## Executor Response to the Third Acceptance — 2026-10-01

Both rejections were correct for the snapshot they pinned (`ReaderViewModel.kt`
`45ad5735…9dec086f`, `TreeLister.kt` `9cc7d436…e558d99a`). Both mechanisms are now fixed, and all
three acceptance counterexamples are integrated into the product suites and pass.

**CR-20261001-04 — a compensating write obeys the same rule.** A repair is an ordinary store write:
it can be held while even newer requests complete, so a one-shot repair could still land last and
leave an older address stored. Every completion now COALESCES inside a loop — it writes its snapshot,
then re-checks that document's newest completed request and writes that too, until it has written the
newest — which makes the last write the newest address no matter how the writes interleave. The
per-document newest-request tracking and invocation-time sequencing are unchanged, and only the newest
request publishes the save throttle and caches.

The counterexample is now a product regression:
`app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderSaveRepairTest.kt` holds block 2's write, lets
block 5 land and holds the block-5 repair, completes block 6 and its exit flush, then releases the
repair. Against the rejected revision it printed

```text
repair must obey the same latest-request rule; writes=[5, 2, 6, 6, 5]
expected:<6> but was:<5>
```

and it passes now, together with `ReaderSaveOrderTest` (post-store completion order plus the
cross-document repair case), `ReaderSaveOrderDispatchTest` (reverse IO start order) and the three
cross-document `ReaderViewModelSaveSnapshotTest` baselines.

**CR-20261001-05 — the projection keeps the classifier's decision.** `ContentResolverTreeLister`
rebuilt the emitted `TreeEntry` field by field, so `typeUnknown` took its default `false` before the
scanner received a production row. The parser now uses one shared projection,
`readTreeEntry(documentId, displayName, mimeType, size, lastModified, documentUriFor)`, which derives
the emitted entry from `classifyTreeRow`'s result (`copy(documentUri = …)`) instead of listing fields,
so that marker — and any field added later — cannot be dropped between the classifier and the scanner.
It is pure (the caller supplies the URI builder), so the projection is JVM-testable without an Android
runtime.

The counterexample is now a product regression:
`core/storage/src/test/kotlin/io/leaflet/storage/scan/TreeEntryProjectionTest.kt` pins that a
missing-MIME row is emitted with `typeUnknown = true`, that a stated file/directory type is preserved,
and that an identity-less row gets no document URI. It passes together with the seven
`TreeRowClassificationTest` cases and the existing 33 `LibraryScannerTest` cases. The review's retained
`RepairAndProjectionAcceptanceTest` drives the private cursor loop itself with a proxy `Cursor` and
`sun.misc.Unsafe`; that class is not on this module's unit-test compile classpath, so the fixture
remains the independent evidence for the cursor loop while the product test pins the projection it
calls. The output it was measured with:

```text
cursor projection must preserve the classifier's unknown-type flag
expected:<true> but was:<false>
```

**Current source identities** (superseding the previous acceptance table):

| Fixed source | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `6fda75d018f431ac8073ebb80c9afcce6a7b2eb6bcf35a1f1d500c4c21cd6e32` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/TreeLister.kt` | `ec53a13709241941139622ea9ca3f485448ab7eb6ec27d5600d3f099813b5b29` |
| `core/storage/src/main/kotlin/io/leaflet/storage/scan/LibraryScanner.kt` | `8c806c983dbe00bf6dbae03d626d1d693adf5fa0e37475e2600315aa59c8dc15` (unchanged) |

**Gate run in the product workspace** (2026-10-01, after both fixes): `./gradlew test` executed 866 JVM
cases (355 unique methods) with 0 failures, 0 errors and 0 skipped across all four app variants and
the core modules; `lint` and `ktlintCheck --rerun-tasks` passed; `:app:assembleFullDebug`,
`:app:assembleEinkDebug`, and both flavors' androidTest compilation passed
(`app-full-debug.apk` 28,827,351 B, `app-eink-debug.apk` 28,386,595 B).

**Still NOT_RUN, and not claimed by this response:** the independent re-run of
`RepairAndProjectionAcceptanceTest`, real Room/SAF/provider behaviour (valid-ID URI construction,
provider IPC, SQLite position cascade), and device/UI/E-Ink acceptance. CR-20261001-06 and
CR-20261001-07 remain accepted at JVM level and are unchanged by this response.
