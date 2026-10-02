# Library Navigation UX Delivery (NB-00–NB-05)

English | [中文](library-navigation-ux-delivery.zh.md)

Date: 2026-10-01
Package: UX-05 → NB-00–NB-05 ([plan](library-navigation-ux-plan.md), [tasks](library-navigation-ux-tasks.md))
Requirement: [REQ-0012](../requirements/REQ-0012-library-navigation.md) (`READY`, scoped supplement to REQ-0001/REQ-0004)
Baseline: `HEAD = 5a754292c1ea02f33af0cb917f5cf24c29dbb763` plus the uncommitted UX-06/UX-07 and review-follow-up work, preserved unchanged
Status: DELIVERED — SOFTWARE VERIFIED, DEVICE PENDING

## Outcome

Folder browsing can now be navigated by breadcrumb, scan-state changes no longer move the file list, and every file/folder row is a large, typed touch target.

- **Breadcrumb (NB-02, AC-1/AC-2/AC-3).** The browse screen shows the Library root and one entry per path segment of the open prefix in a single bounded-height, horizontally scrollable row. Tapping a non-current entry opens that exact prefix through a validated ViewModel action; the current entry is bold, carries a state description, and has no click action. Full names stay in the accessibility tree even when a label is ellipsized visually.
- **Guarded ancestor state (NB-01).** `Breadcrumbs.kt` generates the entries and validates targets by whole path segment (`a` is not an ancestor of `ab`). A tap travels as an `AncestorRequest` carrying the Library and prefix it was rendered for, and `LibraryViewModel.openAncestor` requires both to still be current before it moves; a sibling, a tree escape, an unrelated prefix, an externally supplied path, a target from another Library, a request rendered at a prefix the user has since left, and the current directory are all ignored without changing state (AC-2, NAV-ACC-01). No SAF query or file read is involved.
- **Reserved scan-status region (NB-03, AC-4).** One region hosts the status text and exactly one action (Refresh / Cancel / Retry) for every state. The text always reserves two lines and the action always exists, so `IDLE → SCANNING → CANCELLING → CANCELLED`, `SCANNING → SUCCEEDED`, and `SCANNING → FAILED → Retry` cannot resize the region — and therefore cannot move the file list under it. Recovery text is carried by `progress` alone; the browse screen no longer renders a second, state-dependent notice block above the list.
- **Rows (NB-04, AC-5).** Every file/folder row is clickable across its full width with at least 48 dp height and grows for wrapped names or a 200% font scale. Folder and document rows carry an in-repo vector glyph and a readable MD / MARKDOWN / TXT tag, so the distinction is not color-only. File taps still hand the same document id to the Reader.
- **Bounded list viewport.** The browse `LazyColumn` is now a `weight(1f)` viewport instead of an unweighted child of the screen `Column`: with a large folder it scrolls and keeps the Back control on screen, and it is the list whose bounds the stability checks measure. A change of Library or prefix scrolls it back to the top, so a scrolled directory never leaks its offset into the next one (FR-6/AC-6).

## Requirement gate (NB-00)

[REQ-0012](../requirements/REQ-0012-library-navigation.md) was created as the READY scoped requirement and carries the plan's AC-N1–AC-N6 as AC-1–AC-6. The plan's [NB-00 readiness record](library-navigation-ux-plan.md#nb-00-readiness-record-2026-10-01) records that UX-01 (window insets), UX-02 (E-Ink palette), and UX-04 (unified Library screen) are **not delivered**, and documents the narrowing that let NB-02/03/04 proceed inside `LibraryBrowseScreen` only. The user explicitly authorized proceeding with those prerequisites unmet on 2026-10-01; UX-01/UX-02/UX-04 stay owned by their own packages and are not claimed here.

## Changed files

| File | Change |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/Breadcrumbs.kt` | NEW — `BreadcrumbSegment`, `breadcrumbSegments`, `isAncestorTarget`, prefix normalization |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/BrowseLevel.kt` | `childPath` is `internal` so the breadcrumb builder reuses the same path joining |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` | `openAncestor` with the Library/prefix re-validation; browse failure/cancel now reported through `progress`; browse `notice` removed |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryScreens.kt` | `BrowseTags`; breadcrumb row; reserved `ScanStatusRegion`; typed `BrowseRow`s with ≥48 dp targets; bounded list viewport |
| `app/src/main/res/values/strings.xml` | New browse labels (breadcrumb root/separator/current, row folder/document, scan progress) |
| `app/src/main/res/values-zh/strings.xml` | NEW — Simplified Chinese counterparts for this slice's labels and the status-region actions |
| `app/src/eink/res/values/strings.xml`, `app/src/full/res/values/strings.xml` | `MissingTranslation` suppressed until UX-10 delivers full parity |
| `app/src/main/res/drawable/ic_folder.xml`, `ic_document.xml` | NEW — original 24 dp glyphs, no icon dependency and no copied third-party path |
| `app/src/test/kotlin/io/leaflet/reader/ui/library/BreadcrumbTest.kt` | NEW — 8 pure mapping/validation methods |
| `app/src/test/kotlin/io/leaflet/reader/ui/library/LibraryAncestorNavigationTest.kt` | NEW — 8 ViewModel transition methods |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` | NEW — 6 page methods on the production screen (real scanner + in-memory Room index) |
| `docs/requirements/REQ-0012-library-navigation.md` / `.zh.md` | NEW — the READY scoped requirement, bilingual pair |
| `docs/requirements/README.md` / `.zh.md` | Index entry for REQ-0012 |
| `docs/plans/library-navigation-ux-plan.md` / `.zh.md` | Status, NB-00 readiness record, dependency narrowing |
| `docs/plans/library-navigation-ux-tasks.md` / `.zh.md` | Delivery status and per-card summary |
| `docs/plans/ui-ux-optimization-tasks.md` / `.zh.md` | UX-05 status |
| `CHANGELOG.md` / `CHANGELOG.zh.md` | User-visible entries |

## Verification (2026-10-01)

macOS ARM64, JDK 17, Gradle 8.13, `--offline` against the existing dependency cache.

| Check | Command | Result |
| --- | --- | --- |
| Full JVM suite, every module and variant, forced re-execution | test-result directories deleted, then `./gradlew --offline --no-build-cache --max-workers=1 :app:testFullDebugUnitTest :app:testEinkDebugUnitTest :app:testFullReleaseUnitTest :app:testEinkReleaseUnitTest :core:model:test :core:storage:testDebugUnitTest :core:storage:testReleaseUnitTest :core:markdown:testDebugUnitTest :core:markdown:testReleaseUnitTest :core:reader:testDebugUnitTest :core:reader:testReleaseUnitTest :core:eink:testDebugUnitTest :core:eink:testReleaseUnitTest` | PASS — 844 test cases, 347 unique methods, 0 failures, 0 errors, 0 skipped |
| New NB-01/NB-03 regressions | included above | `BreadcrumbTest` 8, `LibraryAncestorNavigationTest` 11, and `LibraryScanCancelTest` 3 methods, all green |
| Independent acceptance recheck (their probe, their runner) | `python3 docs/plans/review-evidence/2026-10-01-navigation-acceptance/run_navigation_checks.py --java …/jdk-17/…/java` | PASS — exit 0: 21 existing tests OK and the two `NavigationAcceptanceProbe` cases OK (they failed 2/2 before the fix). See [recheck-after-fix.txt](review-evidence/2026-10-01-navigation-acceptance/recheck-after-fix.txt) |
| Android Lint, all variants | `./gradlew --offline lint` | PASS after the scoped `MissingTranslation` handling described below |
| ktlint | `./gradlew --offline ktlintCheck --continue` | This slice's files pass. The run reports violations only in files owned by a concurrently running review-fix task; see Limitations |
| Both debug flavors | `./gradlew --offline :app:assembleFullDebug :app:assembleEinkDebug` | PASS — `app-full-debug.apk` 28,386,595 B, `app-eink-debug.apk` 28,827,351 B |
| Instrumented compilation, both flavors | `:app:compileFullDebugAndroidTestKotlin`, `:app:compileEinkDebugAndroidTestKotlin` | PASS |
| Instrumented page acceptance | `:app:connectedFullDebugAndroidTest`, `:app:connectedEinkDebugAndroidTest` | **NOT_RUN** — no device or emulator is attached (`adb devices` empty) |
| Real E-Ink, hardware refresh/ghosting, TalkBack | manual | **NOT_RUN** |

The instrumented `LibraryBrowsePageTest` drives the production `LibraryBrowseScreen` with a real `LibraryViewModel` over a real `LibraryScanner` and an in-memory Room index; only SAF metadata comes from a fake, gated `TreeLister`. It covers ancestor taps, the current-segment no-op, ≥48 dp breadcrumb/row measurement, type cues, the document-id hand-off, a 200% font scale, and the full scan-state matrix with list-viewport, scroll-offset, and visible-row measurements. It compiles but has not run.

## Response to the independent acceptance (NAV-ACC-20261001)

The [independent acceptance](library-navigation-ux-acceptance-2026-10-01.md) returned **NOT_ACCEPTED** with two reproduced production defects and four page-readiness findings. Both defects are fixed and every page finding is addressed. The acceptance report itself is left untouched, so its verdict stands until it is re-run.

| Finding | Fix | Evidence |
| --- | --- | --- |
| **NAV-ACC-01** (P1) — a root breadcrumb generated on Library A moved Library B out of `Docs/Notes` | The tap now travels as an `AncestorRequest(target, libraryId, prefix)` built from the state that was rendered, and `openAncestor` rejects it unless that Library **and** that prefix are still current | `LibraryAncestorNavigationTest` adds `aRootTargetFromAReplacedLibraryIsIgnored`, `aTargetFromTheSamePathInAnotherLibraryIsIgnored`, and `aTargetRenderedAtAnOlderPrefixIsIgnored`; the acceptance's own probe now passes |
| **NAV-ACC-02** (P2) — `cancelRefresh()` published `CANCELLING` with no scan left to finish it | Cancellation returns early unless a refresh job is active **and** the progress state is `SCANNING`/`CANCELLING`; a Cancel that arrives after a settled scan keeps the terminal state | `LibraryScanCancelTest` (3 JVM methods) plus the real-scanner page case in `everyScanStateTransitionKeepsTheListGeometryStable` (late Cancel after `SUCCEEDED`) |
| Page finding — ambiguous global queries | Every type-cue assertion is scoped to one row (`hasContentDescription(…) and hasAnyAncestor(hasTestTag(row))`), never a bare query over many matching rows | `LibraryBrowsePageTest.rowsCarryRowScopedTypeCuesAndHandTheDocumentIdToTheReader` |
| Page finding — single-use gate; `CANCELLING` never measured | The lister gate is per-scan (`holdNextScan`) and is entered under `NonCancellable`, so `SCANNING` and `CANCELLING` are deterministic phases measured separately | `everyScanStateTransitionKeepsTheListGeometryStable` |
| Page finding — fixture too thin | The fixture now covers a ten-level path on a 320 dp-wide screen with breadcrumb travel asserted, long Chinese names at 200% font scale, an empty folder, and both `STANDARD`/`EINK` runtime modes | `tenLevelPathKeepsEveryAncestorReachableOnANarrowScreen`, `longChineseNamesAtTwoHundredPercentFontScaleKeepTheRowUsable`, `scanTransitionsInAnEmptyFolderKeepTheStatusRegionStable`, `standardRuntimeModeKeepsTheBrowseControlsUsable`, `einkRuntimeModeKeepsTheBrowseControlsUsable` |
| Page finding — possible retained scroll position across prefixes | A `LazyListState` is hoisted and reset whenever the Library or the prefix changes | `aDifferentPrefixStartsItsListAtTheTop` |

The acceptance's retained probe and runner were adapted to the new request protocol with the stale-origin scenario unchanged and re-run against the fixed tree: `OK (21 tests)` for the existing classes and `OK (2 tests)` for `NavigationAcceptanceProbe`, exit 0 ([recheck-after-fix.txt](review-evidence/2026-10-01-navigation-acceptance/recheck-after-fix.txt)). Both probe cases failed 2/2 before the fix. This is a self-run of their harness, not a new independent review.

## Preserved contracts

Empty folders stay selectable; `browseLevel` ordering (directories before files, case-insensitive name order) is untouched; a different prefix still starts its list at the top; the read-only SAF boundary, scan cancellation, and previous-index retention are unchanged; both flavors share one implementation; and no schema, DAO, migration, or DataStore change was made.

## Limitations and omissions

- Device and hardware evidence is missing: every instrumented case above is NOT_RUN, so the AC-3/AC-4/AC-5 page measurements are written, not executed, and no E-Ink or TalkBack claim is made.
- The independent acceptance still reads `NOT_ACCEPTED`: only its author (or a fresh independent pass) can withdraw that verdict. The repairs above are supported by this slice's regressions and by a self-run of the acceptance's own runner, which is not a new independent review.
- UX-01/UX-02/UX-04 remain undelivered. This slice therefore works inside the current (un-inset, non-unified) screens; when UX-04 restructures `LibraryScreens.kt`, the browse changes may need a rebase.
- `values-zh/strings.xml` covers only this slice's labels, so lint's `MissingTranslation` is suppressed in the three default string files with an explicit comment; UX-10 owns full locale parity and the removal of that suppression.
- `ktlintCheck` is green in this revision. In the first delivery pass it failed only on files owned by the concurrently running review-fix task; that task has since made them compliant and the check now passes for every module.
- The parallel task also added a selection-generation guard to `LibraryViewModel.openLibrary` (CR-20261001-06) in the same file as `openAncestor`; both changes coexist, and this delivery neither included nor modified that fix.
- No commit was made, no release is claimed, and the slice does not start another UX package.

## Temporary data

No temporary fixtures, logs, caches, emulator copies, or screenshots were created for this slice; all work stayed inside the repository working tree and the Gradle build directories. Nothing needs to be cleaned up.
