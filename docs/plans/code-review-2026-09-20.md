# Reading-State Code Review — 2026-09-20

English | [中文](code-review-2026-09-20.zh.md)

## Conclusion

Two P1 production-code defect paths were found. Reading-state save/restore cannot yet be considered fully accepted. Both were confirmed by static call-chain review; overwrite/jump behavior has not yet been independently reproduced on device.

This is not proof of whole-repository correctness or final acceptance of DEF-010/011, which were being verified in parallel. Other executors updated the defect file during review, so this report preserves the new findings separately.

## DEF-012 — Saving During Document Load Can Mix New ID with Old Content

Status OPEN; P1; R3-04 / REQ-0003.

- `ReaderViewModel.startOpen` (around line 302) changes `currentId` to B but retains A's `currentDoc`, `currentDescriptor`, and `lastVisibleBlock`.
- `flushPosition` (around line 408) has no Loading/Failed or `doc.documentId == currentId` guard. `persist` (around line 730) still reads the fingerprint from global `currentDescriptor`.
- `ReaderScreen` calls `flushPosition` from ON_PAUSE/ON_STOP/onDispose; production navigation shares the ViewModel. If the app backgrounds while B is loading after A, it can write B's ID with A's position/anchor/fingerprint and overwrite B's local progress row. This path does not modify the source document.
- Another ordering risk: a queued save captures A's ID/doc; when it eventually runs, `currentDescriptor` may be B, mixing fingerprints.

Regression: read A to a nonzero block and give B a different saved position. Block B's `loadPosition`/parse, call `open(B)`, then trigger flush. Compare B's row/fingerprint; it must never contain A's data. Also test load failure, rapid switch, and delayed save.

Minimal direction: use one consistent snapshot of documentId/doc/descriptor/block per save; do not flush an incomplete loading snapshot; old-document saves remain owned by the old document. A failing pre-fix regression and real Room post-fix regression are needed before closure.

## DEF-013 — Same-document Freshness Check May Restore an Old Initial Position

Status OPEN; P1; R3-01/04 / REQ-0003.

- `checkFreshnessThenKeep` (`ReaderViewModel`, around lines 214–257) saves old Content, emits Loading, then restores old Content when fingerprint is unchanged.
- `ReaderScreen` removes/recreates ReaderContent by state. The list state is remembered inside ReaderContent. If Loading appears for a frame, the remounted `LaunchedEffect(docId)` positions from `state.restoreBlock`.
- `restoreBlock` is initialized only on load. Later scroll/TOC operations update `lastVisibleBlock`/database but not that Content field.
- Thus a slow same-document probe can move someone who opened at block 2 and read to block 20 back to block 2. Asserting the ParsedDocument instance is unchanged does not catch it.

Regression: scroll a real page away from the initial restore block; delay `describe/probeAccess` until Loading is visible; reopen/recreate and assert actual first-visible block and offset. Permission loss/content change must still work.

Minimal direction: preserve current content and scroll during background freshness checks, or restore from the newest consistent position; do not force a full reparse as a workaround.

## Verification and Boundaries

`./gradlew :app:testFullDebugUnitTest :app:testEinkDebugUnitTest :core:reader:testDebugUnitTest --console=plain -q` passed (exit 0), including incremental reuse. Existing unit tests do not cover these timing/view counterexamples.

No new device scenario was run; no product code/test was changed. Add the above failing regressions, then fix. Do not describe statically identified risk as confirmed user data loss.

---

Executor response (2026-09-20): DEF-012/013 were reproduced on device and fixed. Evidence/source pin are in `r3-01-04-execution-report.md` under “Code Review (2026-09-20) Response” and matching defect entries. This report's original findings/boundaries remain intact.

## Independent Post-fix Review — 2026-09-20

Conclusion: DEF-013's original remount path is fixed. Loading-time flush is blocked for DEF-012, but the asynchronous save-snapshot subcase remains open.

- ReaderViewModel SHA-256: `07e69c8a13311db419cee139dfc46eecd1c8cd055973d64ea4e39c6bbe9b5bbf`, matching the executor report.
- Targeted command: `./gradlew :app:connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderStateConsistencyTest --console=plain -q`, exit 0.
- Device: emulator-5556 (`verdant_t068_sit`, API 35). Full XML: 2 tests, 0 failures/errors/skips, 2026-09-20T05:03:14 UTC. This is not full-suite or E-Ink acceptance.
- Fixed paths: switching documents clears old snapshot; flush requires Content and matching doc ID; same-document probe no longer emits Loading; two targeted tests pass.
- **Remaining P1:** `onVisibleBlock`/`jumpToBlock` (around lines 396/421) reads global `lastVisibleBlock` only inside the IO coroutine. Valid order: A save enqueued → open(B) clears lastVisibleBlock → A save starts and writes A at block 0 rather than its invocation-time position.
- `persist` still reads/queries descriptor during execution and updates `currentStored` after completion; it is not a fully immutable snapshot. The save must capture documentId/doc/descriptor/block together, and completion must not mutate later document state/throttlers.
- New regression waits for A's save to finish before switching to B, so it misses the delayed-save case. Pause save before execution, switch, release it, and assert each document's final position/fingerprint.
- Evidence boundary: remaining issue is a statically confirmed ordering; it has not been independently reproduced on device. Do not reopen DEF-013 for this subcase.

This review only updated the audit record. The executor changed the defect list concurrently; this appended review is the independent acceptance result. Executor should sync DEF-012 status.
