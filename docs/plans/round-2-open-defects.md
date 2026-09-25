# R2 Acceptance Defects — Open/Closed Ledger

English | [中文](round-2-open-defects.zh.md)

Recorded from 2026-09-18. Current summary last updated 2026-09-20. This ledger supplements the [R2 acceptance verdict](round-2-acceptance-verdict.md), records claims not yet covered or conflicting with source, and does not rewrite historical test results. Fixes follow [R2 stabilization plan](round-2-stabilization-plan.md) and existing REQs; do not create duplicate product requirements.

Evidence level: original findings were mostly static review; runtime evidence for fixes/reviews is recorded in dated appendices. This document itself does not mean a fix was implemented. Suggested reproduction is not a test already run.

## Current Summary and Order

Code review on 2026-09-20 added DEF-012/013, both later reproduced/fixed on device. As of the latest handoff, DEF-010/011 have the required added evidence and are CLOSED; DEF-008 remains FIXED_PENDING_VERIFICATION. Full dual-flavor connected suites intermittently fail Activity startup under two-emulator/high-load conditions (`No compose hierarchies found`); targeted classes pass. Rerun full suites when environment recovers.

Priority: P1 blocks the associated MVP acceptance; P2 blocks trustworthy completion/delivery claims. Status path: OPEN → IN_PROGRESS → FIXED_PENDING_VERIFICATION → CLOSED. Review failures may reopen an item with a dated note. Close only after evidence is complete.

| ID | Priority | Defect | Task / REQ | Current status |
| --- | --- | --- | --- | --- |
| DEF-001 | P1 | TXT/fallback content parsed again as Markdown | R2-03 / REQ-0002 | CLOSED, 2026-09-18 |
| DEF-002 | P1 | Existing TextViews do not receive typography updates | R2-03 / REQ-0007/0010 | CLOSED, 2026-09-18 |
| DEF-003 | P1 | Duplicate table cells both marked as current match | R2-06 / REQ-0008 | CLOSED, positional cell fix 2026-09-19 |
| DEF-004 | P1 | AST reconstruction changes Markdown/fence/copy semantics | R2-02 / REQ-0002/0007 | CLOSED, second round 2026-09-18 |
| DEF-005 | P1 | Reopen checks index but not real reachability | R2-05 / REQ-0001/0003 | CLOSED, second round 2026-09-18 |
| DEF-006 | P1 | Page assertions miss production display regressions | R2-07 / MVP REQs | CLOSED, coverage added 2026-09-19 |
| DEF-007 | P2 | Verdict claims exceeded evidence | R2-08 / REQ-0011 | CLOSED, verdict re-summarized 2026-09-19 |
| DEF-008 | P1 | Compose out-of-bounds crash on Activity destruction | R2-07 / REQ-0011 | FIXED_PENDING_VERIFICATION; thread/test discipline fixed, root-cause attribution not independently proven |
| DEF-009 | P1 | Task-list block has empty source slice and production row | R3-03 / REQ-0002/0007 | CLOSED, found/fixed 2026-09-19 |
| DEF-010 | P1 | Lifecycle test did not prove exit flush/recreated page | R3-01 / REQ-0003 | CLOSED with discriminating evidence 2026-09-20; new-instance page mount delegated to DEF-011 |
| DEF-011 | P1 | Disk test did not prove production-page cross-process restore; skip could mask failure | R3-04 / REQ-0003/0007/0010 | CLOSED with added evidence 2026-09-20; real MainActivity route/SAF provider still not covered |
| DEF-012 | P1 | During document load, flush mixed new ID and old content | Review 2026-09-20 / REQ-0003 | CLOSED, reproduced/fixed on device 2026-09-20 |
| DEF-013 | P1 | Same-document freshness recheck restored open-time position | Review 2026-09-20 / REQ-0003 | CLOSED, reproduced/fixed on device 2026-09-20 |

Recommended order completed for table work: DEF-003 positional mapping → DEF-006 production cell assertion → DEF-007 verdict correction. Keep DEF-008's residual lifecycle risk visible; do not reimplement closed DEF-001/002/004/005 because another item was open. Shared rendering/position work should be serial and minimal.

## DEF-010 — Exit Flush and Activity Recreation Assertions Were Not Discriminating

Found 2026-09-20; initially OPEN; test-evidence gap, not confirmed product corruption.

- `ReaderLifecycleTest` called `jumpToBlock(2)`, which already persists, then used `savedPositions.contains(documentId)` to prove exit/destroy flush. The old write could satisfy the assertion.
- Recreation test did not remount the Reader composition in the new Activity; it checked only a test-held ViewModel.
- Minimum evidence: record prior write, create a new position still inside throttle window, exit/destroy, assert an additional write and final position; test must fail if exit flush is disabled. For real recreation, inspect visible position in the new production page, not an old model variable.
- Closure: discriminating production-path assertions for exit flush and recreation, both flavors, reports bound to source identity.

Closed on 2026-09-20; details below in the R3 remediation record.

## DEF-011 — Cross-process Persistence Did Not Prove Production Page Restore

Found 2026-09-20; initially OPEN; acceptance coverage gap, not reproduced data loss.

- `ReaderPersistenceTest` used real-disk `r3-persist.db` and `r3-persist.preferences_pb`, while production uses `leaflet.db` and DataStore name `settings`. Two phases wrote/read Store/Repository directly; no production Reader/Settings page opened after restart.
- Tests prove storage serialization/persistence, not user flow “change setting/read → kill → production page restores.”
- `assumeTrue(seeded == FONT_SIZE)` used the tested value to decide whether to skip. If the setting were lost, phase 2 could skip instead of fail. Use an independent seed marker; mandatory two-phase acceptance must fail if marker is absent.
- A reported test-file SHA differed from current source; historical adb evidence must not be bound to current source without explanation.
- Closure: retain lower-level disk tests and add production wiring/settings/page restore after process death with process/data identity; necessary paths for both flavors, and skipped phase is not a pass.

Closed with added evidence on 2026-09-20; real MainActivity route and actual SAF provider remain explicitly uncovered.

## DEF-001 — TXT / Fallback Parsed as Markdown Again (Closed)

`DocumentParser.parseTxt` made Paragraph blocks; ReaderScreen unconditionally passed ordinary entries to `renderSpanned`, including `PLAIN_FALLBACK`. This reinterpreted literal headings/asterisks/links and made search differ from visible plain text. Fixed with `RenderBlock.literal` / `ParsedDocument.literalText` to render TXT/fallback literally. Acceptance: actual TextView shows `#`, `**`, and `[label](target)` unchanged; equivalent Markdown still renders rich text; search/copy match visible text.

## DEF-002 — Typography Did Not Update Existing TextViews (Closed)

AndroidView factory applied font/size/line spacing, but update only changed text, so reused views retained stale style. Fixed with shared `applyTypography` in factory/update, including px conversion for line spacing. Acceptance checks existing and newly bound rows agree, immediate updates, persistence, and context. Production-page regressions added in the R2-DEF appendix.

## DEF-003 — Rich-text Search Highlight Misaligned (Closed)

Source slices and rendered text differ because Markdown markers disappear. Initial mapping used source string `indexOf`, so bold/headings shifted spans and repeated terms mapped to first occurrence. Fixed with order-preserving kth-occurrence mapping, discard unlocatable match rather than guessing, and whole-block `blockFallback` for current match.

A second table-cell defect lacked per-cell position; identical cells were both marked current. Fixed by `tableCellSpans(blockVisible)`, intersecting block ranges with cell ranges and converting to local coordinates. If grid does not match, legacy string fallback remains. Tests cover 2/3 identical cells, cross-row repeats, inline markers, out-of-range; real Compose semantics assert exactly one of four identical cells has current style and that next moves it. Five JVM + one device case failed before fix. See 2026-09-19 table-fix record.

## DEF-004 — AST Reconstruction Changed Markdown Semantics (Closed)

Rebuilding source from AST lost escapes, strike nodes, block quote/list structure, and inner fence content. Fixed by preserving code body as data, selecting a fence longer than body backtick runs, restoring safe inline escaping/strike/list/quote semantics, and using AST traversal for visible text. Tests include escaped stars, strike, inline code, nested lists, quotes, tilde/four-backtick fences, and character-exact copy. See R2-02 and R3-03 reports.

## DEF-005 — Same-document Reopen Skipped Refresh/Permission Checks (Closed)

ViewModel returned cached Content whenever `currentId` matched, masking external modification, deletion, or revoked permission. Added index/fingerprint recheck and `ReaderStore.probeAccess` that opens/closes a real SAF stream. Reuse only if nonempty fingerprint matches and source is reachable; changed source reloads, missing/revoked source enters explicit recovery state. Unchanged reopen/rotation reuses parse. Page regressions cover external edit, deletion, permission loss, unchanged refresh.

## DEF-006 — Page Tests Did Not Catch Production Display Regressions (Closed)

Original tests inspected model `blockTexts`, called `open()` instead of rotating, called arbitration helpers instead of touch input, and large-file tests used `createForJvmTests()`. Added `PageViews` assertions over real view tree/TextView text/spans/style, actual rotation, typography changes, TOC tap, real search input/highlight, TXT literal behavior, and reopen after external edit/delete/permission loss. Production large-file instrumentation now uses Markwon. Both flavor suites grew from 12 to 21, then 29/30 through subsequent rounds. Closed only after DEF-001–005 regressions fail pre-fix/pass post-fix.

## DEF-007 — Verdict Exceeded Evidence (Closed)

Re-reviewed `round-2-acceptance-verdict.md`: narrowed unsupported PASS claims to PARTIAL/NOT_RUN, mapped ACs to production tests/commands/manual evidence, documented emulator limits and residual gaps, and corrected stale references. Do not mark a criterion PASS without coverage.

## Second-round Fix Record — 2026-09-18

Order: DEF-008 → 004 → 003 → 005 → fill DEF-006 → review DEF-007. DEF-001/002 had no new counterevidence and were not reimplemented. Gate: JVM 552 variant/264 unique; Full/E-Ink pages 29/29; Markdown 5/5; storage 16/16; test/lint/ktlint and four builds passed on emulator-5554, Android 15/API 35. First E-Ink release assemble hit D8 OOM; after restarting Gradle daemon both releases passed. Emulator is not E-Ink hardware.

### DEF-008 — Compose Crash on Activity Destroy (Root Cause Located; Pending Verification)

ReaderViewModel wrote Compose-observed `ui`/`search` StateFlow from `Dispatchers.IO`. Snapshot apply notifications can drive recomposition on writer thread. Observed stack: `AndroidPrefetchScheduler` → `Choreographer.getInstance()` → “current thread must have a looper”; during destroy, SlotTable mutation surfaced as `SlotWriter.removeGroup` AIOOBE → “Unable to destroy activity.”

Fix: open/freshness/search/in-library link state writes run in `viewModelScope` (Main); blocking/CPU work uses `withContext(ioDispatcher)`. `recordRecent` also moved. Position throttle/persistence remains IO and does not touch Compose-observed state. `PageViews` wraps view-tree reads and orientation changes in `onMain`, with `rotateTo()` for request-on-main/wait-off-main.

A permanent `Snapshot.registerApplyObserver` regression saw 3 off-main applies before and 0 after; it failed deterministically before fix. After fix: Full/E-Ink 29/29, combined Full+E-Ink+Markdown+storage two rounds green. Original AIOOBE did not recur before or after across several runs; call-stack evidence supports, but does not prove, causal link. Compose runtime issue not excluded. Keep FIXED_PENDING_VERIFICATION; do not mark closed solely due non-reproduction.

### DEF-009 — Task-list Production Row Was Empty (Closed)

Found in R3-03. Markwon 4.6.2 task `TaskListItem` extends `CustomBlock`, not CommonMark `ListItem`; `filterIsInstance<ListItem>()` removed it, leaving `kind=LIST text=[] src=[]` and blank TextView. JVM shim used ListItem child, so only device path failed.

Fix in MarkdownRenderer: iterate arbitrary children; identify Markwon task item by class name (avoid Android class in JVM), reflect `isDone()` to add `[ ] `/`[x] ` when available, never guess. Keep shim `TaskMarker` path separate. Before fix two JVM and one device test failed; afterward core Markdown JVM and ReaderSyntaxPageTest 5/5 passed. Checkbox drawing/nested tasks/multi-paragraph tasks were not separately judged.

## DEF-010/011 Remediation and Process Evidence — 2026-09-20

R3 report added a discriminating position-4 exit-flush test that first proves address 4 has not been saved, then exits and requires the new write. Temporarily removing `onDispose { flushPosition() }` makes it fail. Activity recreation test is separate because `createComposeRule` composes only the first Activity; it verifies destroy-time flush/ViewModel state, not new-page remount.

`ReaderProductionPersistenceTest` uses production `leaflet.db` and `files/datastore/settings.preferences_pb`, real `ReaderScreen` + `SettingsViewModel`, and asserts visible font size and block-4 `Delta` content after actual force-stop/restart. Both flavors ran `adb install -r` → seed instrumentation → force-stop → restore instrumentation, 1 test each phase. Independent marker replaces font-value skip; `-e leaflet.requireSeed true` makes missing seed fail. Production test suite is opt-in (`leaflet.productionStores=true`) because it deletes real app storage and would corrupt concurrent tests.

Uncovered: actual MainActivity route and real SAF provider; content bytes come from disk through `ContentAccess` test seam. Under two-emulator/high-load conditions full suite intermittently fails before tests start with `No compose hierarchies found`; targeted classes 14/14 passed. Rerun full dual-flavor suite when environment is healthy.

## DEF-012 — Flush During Document Switch Mixed ID and Old Content (Closed)

Confirmed on device. `startOpen(B)` changed `currentId` but left A's `currentDoc/currentDescriptor/lastVisibleBlock`; background flush could write B ID with A position/anchor/fingerprint. Delayed save could also read B's global descriptor after capturing A.

Fix: bind descriptor to `currentDescriptorId`; clear old snapshot on actual document switch; flush only when Content matches current ID; persist uses matching descriptor and only updates current cache if still same document. Pre-fix `ReaderStateConsistencyTest` failed (“B must not be written while it loads: [5]”); post-fix passed. Real SAF slow-loading combination not walked; tests used FakePageStore delays.

## DEF-013 — Freshness Recheck Restored Open-time Position (Closed)

Confirmed on device: same-document check emitted Loading, unmounted ReaderContent/remembered list state, then `LaunchedEffect(docId)` restored stale `restoreBlock` captured at initial load. A slow check could move a reader from block 20 back to block 2.

Fix: same-document check does not emit Loading; content stays mounted. Persist synchronizes `Content.restoreBlock` on actual throttled save or TOC landing, not every scroll tick. Pre-fix `sameDocumentRecheckDoesNotRemountTheReader` captured a different TextView after recheck; post-fix passes. Real long document/slow provider and E-Ink hardware appearance remain unverified.

## Test Environment (affects full-run reliability)

Two emulators ran concurrently (emulator-5554 project AVD `dev_arm64_api35`, plus emulator-5556), load average ~13.5–14.5. Large merged runs intermittently fail Activity startup with `No compose hierarchies found` before test bodies. Isolated/small groups passed: ReaderDisplay 6/6, ReaderRecovery 14/14, ReaderStorageBoundary 3/3, ZzActivityRecreation 1/1, ReaderStateConsistency 2/2, earlier targeted 14/14. Full dual-flavor suite still needs rerun after host load recovers; do not call targeted green full-suite acceptance.
