# Reader Follow-up Code Review — 2026-10-01

English | [中文](code-review-2026-10-01.zh.md)

## Conclusion and Scope

Three additional findings were identified: one P1 unsafe table-measurement path and two P2 reader/navigation regressions. The deferred-save subcase of historical DEF-012 was also independently reproduced as a failing JVM regression; it is an unresolved P1, not a new duplicate defect. No product fix was made by this review.

> **Follow-up status (2026-10-01):** all four findings were fixed; the two deferred-save cases were reproduced failing and now pass on the JVM, the table gate and parser combination has JVM regressions, and the two UI findings have device regressions that are written but NOT_RUN (no device). See [Resolution](#resolution-2026-10-01-follow-up) at the end of this report.

HEAD remains `5a754292c1ea02f33af0cb917f5cf24c29dbb763`. The review covers a snapshot of the uncommitted UX-07 implementation and the previous review's fixes, plus related committed navigation/reading-state code. Line numbers below refer to that snapshot. Relevant workspace sources still matched the snapshot at the final source check. This is a focused review, not whole-project acceptance.

| ID | Priority | Finding | Evidence | Status |
| --- | --- | --- | --- | --- |
| DEF-012, deferred-save subcase | P1 | A queued save reads a later document's mutable block address | Production ViewModel recompiled; deterministic JVM regression fails: expected 5, actual 0 | FIXED — JVM regressions PASS (both cases failed before the change) |
| CR-20261001-01 | P1 | Wide table cells can exceed Compose's representable fixed constraints | Production parser admits the sample; pinned framework rejects oversized constraints; full UI scenario NOT_RUN | FIXED — JVM gate/parser regressions PASS; device regression written, NOT_RUN |
| CR-20261001-02 | P2 | Navigation retains default 700 ms fades in E-Ink mode | App call and pinned dependency bytecode checked; device transition capture NOT_RUN | FIXED — explicit instant transitions wired; device capture NOT_RUN |
| CR-20261001-03 | P2 | Closing the menu at the document tail can move the body | Static scroll-boundary counterexample; device regression NOT_RUN | FIXED — device regression written, NOT_RUN |

## DEF-012 — Deferred Save Still Reads Mutable Position

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt:394–397` and `419–421`.
- **Trigger and impact:** Read A, request `jumpToBlock(5)`, leave its IO save queued, and call `open(B)`. Switching documents resets `lastVisibleBlock` to 0. When A's save starts, it writes A at block 0 rather than the requested block 5, overwriting A's auxiliary reading position. Source documents are untouched.
- **Cause:** The coroutine captures A's ID/document but reads `lastVisibleBlock` only when it executes. `onVisibleBlock()` has the same mutable read, including the completion-time throttle update. Capturing `blockIndex` for the explicit flush does not fix the other save paths.
- **Evidence:** [ReaderSaveReviewTest.kt](review-evidence/2026-10-01/ReaderSaveReviewTest.kt) runs the actual recompiled ViewModel with separate Main/IO test schedulers and an in-memory ReaderStore. A is fully loaded before the save is queued; B remains Loading when IO is released. JUnit reports `Tests run: 1, Failures: 1`, with `expected:<5> but was:<0>`.
- **Minimal direction and closure:** Capture an immutable save snapshot before dispatch and bind completion/throttle updates to its document/generation. The retained regression must pass; also cover delayed scroll saves and completion after a document switch. Real Room/SAF/device verification remains required for production acceptance.

The [2026-09-20 independent review](code-review-2026-09-20.md) already identified this subcase. Its recorded ViewModel SHA-256 equals the current source. The historical ledger's broader CLOSED label does not prove this ordering safe; this report preserves the same defect identity and adds executable evidence.

## CR-20261001-01 — TableGrid Uses Unbounded Fixed Constraints

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/TableGrid.kt:58–74`, called by `ReaderScreen.kt:1019–1033`; parser guard in `core/markdown/src/main/kotlin/io/leaflet/markdown/DocumentParser.kt:165`.
- **Trigger and impact:** A valid table has a long unwrapped cell whose intrinsic physical-pixel width exceeds Compose's constraint representation. The new grid passes that width directly to `Constraints.fixed`, which throws during layout instead of producing the promised readable/fallback state. Higher density or larger typography increases exposure.
- **Cause:** The grid's widths are the maxima of `maxIntrinsicWidth(Constraints.Infinity)` with only a lower bound. Compose constraints do not support arbitrary integer dimensions. The existing 32 Ki-character source limit does not impose a physical-pixel limit on a cell or the whole grid.
- **Evidence:** [WideTableProbe.kt](review-evidence/2026-10-01/WideTableProbe.kt) generates a 10,039-character table containing 10,000 CJK characters in one cell. The production JVM parser reports `degraded=false, kinds=[TABLE], blockSourceChars=[10039]`. [ConstraintsProbe.java](review-evidence/2026-10-01/ConstraintsProbe.java) invokes the pinned `ui-unit-android:1.7.5` factory: `(100000, 80)` is accepted; `(262143, 80)` and `(300000, 80)` throw `IllegalArgumentException: Can't represent a width ... in Constraints`. Actual Android text intrinsic width and the end-to-end crash have not been measured here.
- **Minimal direction and closure:** Validate cell and total-grid dimensions before fixed measurement and reuse an explained, bounded fallback for pathological content. Merely clamping the width while leaving unwrapped text clipped is insufficient. Exercise the sample through the real reader at high density/large font settings; require no crash and complete reachable content, while preserving normal column alignment and search mapping.

## CR-20261001-02 — NavHost Still Uses Default Fades

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/LeafletNav.kt:74–77`; fixed version in `gradle/libs.versions.toml` is `navigationCompose = "2.7.7"`.
- **Trigger and impact:** Navigate between Library, Settings, Browse, and Reader in E-Ink mode. NavHost's default enter/exit animations remain enabled, bypassing the no-crossfade rule in REQ-0004 FR-2/FR-4. Back navigation inherits transitions too.
- **Cause:** NavHost receives no explicit transition policy. The comment claiming that version 2.7.7 swaps routes instantly by default is incorrect; disabling ripple/overscroll in the theme does not configure NavHost transitions.
- **Evidence:** The locally cached 2.7.7 dependency was extracted and inspected with JDK 17 `javap -c`. `NavHostKt$NavHost$3.invoke` calls `fadeIn(tween(700))`; `$4.invoke` calls `fadeOut(tween(700))`. The existing app bytecode calls the transition-capable NavHost overload with default mask `508`, including the enter/exit/pop defaults. This is dependency/call-path evidence, not a device animation recording.
- **Minimal direction and closure:** Explicitly supply `EnterTransition.None` / `ExitTransition.None` for enter, exit, pop-enter, and pop-exit through the centralized motion policy; the M0/M1 Standard requirement also specifies no page transitions. Verify actual route swaps and back navigation in both flavors, including hot display-mode changes.

## CR-20261001-03 — Menu-dependent Tail Padding Moves Content at the Tail

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:466–470`, bound as `LazyColumn.contentPadding` at line 491; menu changes bottom-chrome height at lines 713/733.
- **Trigger and impact:** Open the reading menu, turn to the actual document tail, then close the menu by a center tap. The bottom padding shrinks from `bottomChromePx + 8 dp` to the smaller footer reservation. The current scroll offset can exceed the new maximum, so the list must clamp backward and move the body. Menu toggling therefore does not preserve body item positions at this boundary.
- **Cause:** With body extent `C`, viewport height `H`, and tail padding `P`, the scrollable end is `C + P - H` for a sufficiently long document. At the open-menu end `P_open`, reducing padding to `P_closed` makes the current offset exceed the new end by `P_open - P_closed`. Keeping the list's outer rectangle fixed does not prevent that content movement or the measurement pass caused by changing content padding.
- **Evidence boundary:** This is a static geometry counterexample, not a measured pixel displacement on device. The added tail test covers closed → open → further turns, but never closes the menu again at the open-menu tail. The overlay-geometry test checks the list rectangle, not body positions or layout-pass counts.
- **Minimal direction and closure:** Keep sufficient tail headroom independent of menu visibility, or adopt a policy that preserves the actual body position through tail obstruction changes. Add open → tail → close/reopen checks over native text bounds and scroll offset. Do not mark the UX-06 no-content-jump criterion accepted based solely on unchanged list bounds.

## Previous Review Follow-up

The [2026-09-30 report](code-review-2026-09-30.md) records fixes in the working tree. This review checked their relevant source changes:

- CR-20260930-01/02/03 have new tail-boundary and revealed-jump/headroom logic. Full device closure was not rerun here; CR-20261001-03 identifies a new boundary regression in the tail-padding solution.
- CR-20260930-04's mandatory `resolveActivity()` preflight is removed. Its query-invisible-handler device scenario remains unverified by this review.
- CR-20260930-05's three `SettingsStoreReadingModeTest` cases were freshly compiled from snapshot sources and run directly with JUnit: `OK (3 tests)`. This confirms the configured fresh-install defaults and explicit-choice behavior, not every MainActivity/device startup frame.

Historical executor test counts and UX-07 screenshots were not treated as newly executed review checks.

## Verification, Source Identity, and Limits

Environment: macOS ARM64, JDK 17.0.9, Kotlin compiler 2.0.21, Coroutines/Test 1.10.2, JUnit 4.13.2; dependencies were read from existing local caches. A snapshot and copied caches were kept under `/tmp/leaflet-review2.V7aBMq`. Product sources were not changed.

| Check | Result |
| --- | --- |
| Targeted Gradle JVM command | Could not start: `FileLockContentionHandler` failed with `java.net.SocketException: Operation not permitted` under the current sandbox. No Gradle test pass is claimed. |
| Deferred-save regression | Fresh compilation of snapshot `ReaderViewModel.kt` plus the retained fixture succeeded; direct JUnit run failed as expected, proving the incorrect saved block. Other core/model classes used existing local build jars. |
| Reading-mode regressions | Fresh compilation of snapshot `SettingsStore.kt`, `ReaderSettings.kt`, and existing tests succeeded; direct JUnit run passed 3 tests. |
| Wide-table parser probe | Admitted the generated input as a non-degraded TABLE using existing production parser build jars. |
| Fixed-constraint probe | Pinned framework factory reproduced the representation exceptions. |
| Navigation dependency inspection | Confirmed 700 ms default fades and the existing app's default-argument call. |
| UI/device regressions, APK builds, lint, ktlint, hardware | NOT_RUN in this review; the current socket restriction also prevents normal local emulator/ADB verification. |

Attempted Gradle command, from the snapshot:

```bash
./gradlew --offline --no-daemon --no-build-cache \
  :app:testFullDebugUnitTest :app:testEinkDebugUnitTest \
  :core:reader:testDebugUnitTest :core:storage:testDebugUnitTest
```

Selected SHA-256 identities:

| File in snapshot / extracted dependency | SHA-256 |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderViewModel.kt` | `07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `b933b812728dc5a943433b81b667b89ee9595e954d002652dd681663bd801e70` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/TableGrid.kt` | `8a5c6a9aa10da33eb9826c2375aac036a0318cf0fd363e724861a71293525d72` |
| `app/src/main/kotlin/io/leaflet/reader/ui/LeafletNav.kt` | `5f467b386496298ec32ef79a756efb8afbf9d0ad5efe683402445bfcd1cfdedb` |
| `ui-unit-android:1.7.5` extracted `classes.jar` | `b27d9309a3f18a8f9a3b0b948d1efcf6221e722271842c78cee0869fef3383d6` |
| `navigation-compose:2.7.7` extracted `classes.jar` | `1a8e0d9001f090191054361d6d555a1f2b74ed9deaddaf0b2960a9b90c3af68e` |

## Artifacts and Cleanup

- Snapshot, copied caches, extracted dependencies, compiler outputs, and logs under `/tmp/leaflet-review2.V7aBMq` were deleted after checks.
- Three useful reproduction sources are intentionally retained under `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01/`: `ReaderSaveReviewTest.kt`, `ConstraintsProbe.java`, and `WideTableProbe.kt`. They are report evidence, are not wired into the product build, and add no dependency. Fixtures for a future fix should be integrated into the applicable test suite before closure.
- This English report and its complete Chinese pair are the documentation deliverables. Concurrent source/doc changes and pre-existing workspace build artifacts were preserved; this review did not create workspace build outputs.

## Resolution (2026-10-01, follow-up)

All four findings were fixed on top of the reviewed snapshot. The review's retained
`ReaderSaveReviewTest.kt` was integrated into the product test suite as
`app/src/test/kotlin/io/leaflet/reader/ui/reader/ReaderViewModelSaveSnapshotTest.kt` (rewritten to the
same harness, with a second case for the throttled scroll path and a no-switch baseline). Running it
before the fix reproduced the reported failure exactly — `expected:<5> but was:<0>` and
`expected:<4> but was:<0>` — and it passes after it. The remaining UI closures are device
regressions that are written and compile but were NOT_RUN (no device or emulator attached).

**DEF-012, deferred-save subcase.** `ReaderViewModel` now captures an immutable `SaveSnapshot`
(document id, parsed document, block, load generation) at the moment a save is requested and hands it
to `queueSave`/`persist`. The IO coroutine reads only the snapshot, so a queued save can no longer
write the next document's block 0 over the previous document's position. The completion bookkeeping
is bound to the same snapshot: `PositionThrottler.markSaved` and the `currentDescriptor` /
`currentStored` caches are updated only while the snapshot still describes the open document
(`isCurrent` = same id and same session generation), which also closes the unconditional
`currentStored` assignment that a delayed save could use to poison the new document's cache.
`jumpToBlock`, `onVisibleBlock` and `flushPosition` all take this path.
Regressions (JVM, executed): `ReaderViewModelSaveSnapshotTest` — 3 cases, all green; the two
switch cases fail on the previous implementation.

**CR-20261001-01 — table constraints.** The bound is now explicit and validated before measurement:

- `TableGrid` clamps both column widths and row heights to `MAX_CONSTRAINT_DIMENSION_PX`
  (260 000), so no input can hand Compose an unrepresentable dimension.
- `TableBlockEntry` validates the block first: cells render with `softWrap = false`, so every glyph
  advance is bounded by ~2 em and `characters × 2 em + cell padding` is a conservative upper bound on
  the widest unwrapped cell line (`isTableCellWidthRepresentable`). Above the bound the block renders
  through the existing bounded fallback — its own source text in the normal horizontal-scroll
  container — so no character is dropped or clipped and search highlighting still applies. Normal
  tables keep their measured alignment; normal column widths are far below the bound.
- The bound itself was measured, not assumed: a JVM probe against the pinned `ui-unit` 1.7.5 shows
  `Constraints.fixed(262142, …)` accepted and `262143` throwing, which the test asserts as a margin
  (`TableGridBoundsTest.theGateKeepsAMarginBelowTheMeasuredFrameworkLimit`).
Regressions (JVM, executed): `TableGridBoundsTest` — 5 cases, including the review's sample parsed
through the PRODUCTION parser (`degraded = false`, one `TABLE` block, a 10 000-character widest cell)
and rejected by the gate. Device regression
`ReaderReviewFollowUpTest.pathologicalWideTableFallsBackToScrollableTextWithoutCrashing` asserts the
fallback text is composed and the following paragraph is still displayed; NOT_RUN.

**CR-20261001-02 — navigation transitions.** `LeafletTheme` now publishes the resolved
`MotionPolicy` as `LocalMotionPolicy`, and `NavHost` receives explicit
`enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition` values resolved through
that policy (`MotionPolicy.pick`). REQ-0004 FR-2 routes every transition decision through the policy
and §Standard adds no page transitions in M0/M1, so both branches are instant; the previous comment
claiming navigation-compose 2.7.7 defaults to instant swaps was wrong and is removed. Device
transition capture remains NOT_RUN — it needs frame-level observation on hardware.

**CR-20261001-03 — tail padding.** The tail reserve no longer depends on the current menu
visibility: it starts from a floor that already covers the footer plus a menu row (96 dp; the footer
is ~29 dp and a Material 3 `TextButton` row ≥ 40 dp) and only ever GROWS, through a per-composition
high-water mark, when the measured chrome exceeds the floor at large system font scales. Shrinking was
the defect (a smaller reserve lowers the maximum scroll offset, so the list clamps backwards and the
body moves); growing cannot move anything, because the padding sits after the last item and only
extends the scroll range. The last line still clears the footer and an open menu, so
CR-20260930-01 stays closed. Device regression
`ReaderReviewFollowUpTest.togglingTheMenuAtTheTailDoesNotMoveTheBody` turns to the tail with the menu
open and asserts the composed body lines keep their exact positions when the menu closes and
reopens; NOT_RUN.

Verification for this follow-up (2026-10-01, after all four fixes): `./gradlew test --rerun-tasks`
executed 726 JVM cases (315 unique methods) with 0 failures, 0 errors and 0 skipped — including the
new `ReaderViewModelSaveSnapshotTest` (3 cases) and `TableGridBoundsTest` (5 cases) in every app
variant; `lint` and `ktlintCheck --rerun-tasks` passed; `:app:assembleFullDebug`,
`:app:assembleEinkDebug`, and both flavors' androidTest compilation succeeded
(`app-full-debug.apk` 29,215,245 B, `app-eink-debug.apk` 28,493,461 B). The two UI regressions
(`ReaderReviewFollowUpTest`) and every device scenario in this report are NOT_RUN: no device or
emulator is attached (`adb devices` is empty and no AVD is installed).

