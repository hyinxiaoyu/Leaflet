# REQ-0003 — Reading Position and Recent Documents

English | [中文](REQ-0003-reading-position.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0 (T05 browsing + T06 basic Reader; delivered through T07; M0 exit flow is exit → restore)

Source: `docs/archive/init.md` §§15, 26; implementation-plan T04/T07; position guidance from Pilcrow's `ScrollAnchor(index,offset)` in `docs/research/eink-readers.md` §5. Do not use mdreader-style pixel `scrollY` as the durable position.

## Problem

Reading long documents is interrupted by leaving the app, switching documents, process death, and rotation. Pixel `scrollY` drifts when content, font, or width changes and does not express reading progress. Leaflet needs a versioned position contract that prefers block anchors and falls back safely after content changes, so a reopened document returns to a reasonable place rather than its top or a wrong block. Recent documents must provide a way back to previously opened files.

## User Stories

- As a long-document reader, I want reopening after leaving or process death to restore my previous place, so that I do not have to search again.
- As a user with externally synchronized files, I want a changed file to restore to a reasonable nearby location rather than a wrong block.
- As someone reading several documents, I want recent documents ordered by time so that switching between them is easy.

## Scope

- Persist versioned `ReadingPosition{documentId, version, blockIndex, blockOffset, progress, anchor}`. Block index/offset are primary; progress is 0–1; anchor may contain heading text and TOC path. Pixel values are transient only.
- Throttle writes during reading (initial target: at least 2 seconds between writes and only on meaningful movement), and flush on Reader exit, document switch, and app backgrounding.
- Restore by block/offset when content fingerprint matches. After content/font/width changes, validate and fall back through `anchor → progress`; invalidate caches when a document changes.
- Maintain recent documents ordered by last-opened time with title snapshot, Library, and timestamp. Support single-item removal and clear-all. Missing/unauthorized items show recovery actions instead of silently disappearing or crashing.
- Recreate the Reader page after rotation/theme/reflow without reparsing unchanged content.

## Non-goals

No editing, accounts/cloud sync (positions do not sync), AI/RAG, PDF/EPUB, full annotation or bookmark system (the anchor is only for recovery), plugins, library-wide FTS, synchronized highlight notes, two-column layout, complex animation, durable pixel `scrollY`, cross-device sync, or reading analytics.

## User Experience

1. Restore silently on open; a first-time document starts at the top.
2. If content changes and exact position is lost, restore nearby and show a one-time static notice: “The document changed; moved to the closest available position.”
3. Show recent items on Library/home with title snapshot, Library name, and relative time. Tap to open; allow single removal and clear-all in management mode.
4. If a recent file was moved, deleted, or lost permission, retain the record and show the reason plus Remove Record / Reauthorize. Do not crash.
5. Font, line-spacing, or margin changes preserve the same block/anchor context rather than the same pixel offset.

## Functional Requirements

- **FR-1 Position structure:** Store document ID, contract version/content-fingerprint reference, block index, in-block offset, block-based progress (0–1), and optional heading/TOC-path anchor. Durable storage must not depend only on pixels.
- **FR-2 Throttled save:** Save after meaningful scroll/page-turn movement no more often than the 2-second default. Also flush once on pause, Reader exit, document switch, and backgrounding, so process death loses at most one save interval of visible position.
- **FR-3 Restore priority:** If fingerprint matches, restore block/offset. If it differs or the block is out of range, fall back through `anchor → progress → document top`; retain a reason for UI feedback.
- **FR-4 Cache invalidation:** Include fingerprint (`mtime+size` or a future hash) in parse/render cache keys. A changed fingerprint invalidates cached blocks; never interpret a new position with an old block model.
- **FR-5 Recent list:** On each successful open, de-duplicate and move the document to the front, ordered by last-opened time. Store title snapshot and Library reference; support item removal and clear-all. Default cap (initially 30) remains unverified.
- **FR-6 Stale recent items:** On missing file or permission loss, retain the row and offer reauthorization or record removal. Never clear all user records as a side effect; remove a dead item only after user confirmation.
- **FR-7 No reparse on recreation:** Rotation, reflow, or theme change reuses parsed blocks when fingerprint is unchanged; recompute layout/scroll only.
- **FR-8 Process recreation:** After force-stop/restart, opening the same document restores the last flushed position. Error tolerance is at most one screen, with precise checks in ACs.

## Acceptance Criteria

- **AC-1:** Open, move to block N, exit normally, and reopen: same block and in-block offset within half that block's height. The same holds after process death.
- **AC-2:** During 10 seconds of continuous scroll with a 2-second throttle, write at most 6 times and perform one exit flush, measured with a fake clock/test seam.
- **AC-3:** After an external content change, reopen without crash, restore through anchor/progress, show a change notice, and do not land at a misleading old block index.
- **AC-4:** After changing font size and reopening, return to the same block/anchor and preserve context, not the same pixel.
- **AC-5:** Recent items sort newest first and contain one row per document; remove/clear works; when over the configured cap, evict the oldest and test against the recorded cap.
- **AC-6:** A deleted file shows a notice and Remove Record; removal affects only that row. A revoked grant offers Reauthorize.
- **AC-7:** Rotation preserves position and does not invoke a full reparse, verified by parse-call count.
- **AC-8:** Every criterion has repeatable steps and a threshold; no vague “supports X.”

## Edge Cases

A first open without saved position starts at the top without a notice. If a block was deleted, fall back to anchor/progress. Empty or single-block files must not divide by zero when computing progress. An unreadable old-schema position falls back to top without crash and is migration-tested. If one document is referenced by two Libraries, key position by `documentId`, not Library.

## E-Ink Considerations

Restore by instant scroll (REQ-0005); no smooth-scroll flashes or ghosting. Save/restore has no animation or high-frequency UI updates. Fallback notice is static and does not flash away; show it for at least 3 seconds if auto-dismissed.

## Standard Android Considerations

Use the same position semantics. Standard may use a brief restoration animation, but mode must not change what location is restored. Throttle settings are shared across modes.

## Performance Considerations

Throttle writes to avoid per-frame database work. Exit flush is a lightweight single-row upsert and should be under roughly 100 ms (measurement pending). Position read runs alongside document open and must not delay first content.

## Storage / Data Impact

Room stores `ReadingPosition` keyed by `documentId` and a `RecentReading` table or `lastOpenedAt` column (T04 decides). Position fields are versioned and schema changes require migration tests. DataStore may store throttle/cap preferences, not position records. Removing a Library/clearing the database clears associated local position and recent rows; originals are untouched.

## Test Plan

DAO/Repository tests cover throttle count, exit flush, restore-priority matrix (same/different fingerprint, invalid block, missing anchor), cache keys, recent deduplication/cap/removal. Use a fake clock for throttling, instrumented process recreation for persistence, and parse-call count for rotation. Cover deleted, renamed, and unauthorized provider variants.

## Dependencies

Requires REQ-0001 Library/Document, REQ-0002 block model/PositionMap, and T04 Room/DataStore schema. REQ-0005 mode changes must preserve position; REQ-0006 shares the anchor model.

## Code review follow-up (2026-10-01)

The [2026-10-01 follow-up review](../plans/code-review-2026-10-01.md) reproduced the deferred-save subcase of
DEF-012 as a failing JVM regression: a write queued for document A read the mutable
`lastVisibleBlock` when its IO coroutine finally ran, and opening B resets that field to 0 — so A's
position was overwritten with block 0. `ReaderViewModel` now captures an immutable snapshot
(document id, parsed document, block, load generation) when the save is requested, and both the write
and its completion bookkeeping (save throttle, descriptor/stored-position caches) are bound to that
snapshot, so a delayed save can neither write another document's address nor touch the current
document's caches. Regressions: `ReaderViewModelSaveSnapshotTest` (3 JVM cases; the two switch cases
failed before the change). Room/SAF/device verification of this ordering remains NOT_RUN.

## Code review round-2 follow-up (2026-10-01)

The [round-2 review](../plans/code-review-2026-10-01-round-2.md) found two more defects in this REQ's area.

- **CR-20261001-04 — save completion order (FR-2).** Immutable snapshots fixed *identity* but each
  write still ran in its own coroutine, so a slow write for an older block could complete after a
  newer `jumpToBlock` and the exit flush and leave the older address stored. Each request now
  allocates a monotonic sequence at INVOCATION time, on the caller's thread before any dispatch (so
  sequence order is never IO-start order — the independent acceptance boundary), while store writes
  stay concurrent (a newer address is never blocked behind a slower older one). A write that completes
  while a newer request exists publishes neither the save throttle nor the position caches, and when
  that newer write already landed the newest accepted address for the same document is written again
  (the newest request is tracked per document, so switching documents cannot redirect the repair).
  Every completion then coalesces to that document's newest completed request in a loop, because a
  compensating write is itself an ordinary store write that can be held while even newer requests
  complete (independent acceptance, third round). `flushPosition()` returns the job of its own write, so a caller
  that joins it still gets "the latest accepted address is written". Regression:
  `ReaderSaveOrderTest` (post-store completion order, plus the cross-document repair case) and
  `ReaderSaveOrderDispatchTest` (reverse IO start order — the counterexample the independent acceptance
  used); both reproduced `writes=[5, 5, 2]` on the rejected revision.
- **CR-20261001-07 — recent metadata publication (UX-3/UX-4 hints).** The Recently-read rows derived
  only from the entry list while the library name and file health lived in plain mutable maps, so a
  finished lookup was invisible: the row kept showing an unknown library and omitted the
  access-lost/missing-file hint. `RecentViewModel` now publishes that metadata as immutable
  observable state combined with the entries, publishes the entry list immediately (lookups run in a
  job the next list supersedes), and prunes cached metadata to the listed entries. Regression:
  `RecentMetadataPublicationTest`.

Real Room/SAF/device verification of both orderings remains NOT_RUN.

## Open Questions

- T07 uses a 2-second `PositionThrottler.SAVE_INTERVAL_MS` and recent cap of 30; JVM tests and both-flavor device demos show usability, but values remain unverified until T13 baselines.
- T07 fingerprint is `mtime:size`; a `contentHash` column is reserved but unused. Hash cost is unmeasured.
- T07 anchor is best-effort (nearest parent heading text, first duplicate heading). REQ-0006 may define a more structured anchor; this remains unverified.

## Round-3 bug-fix evidence (2026-10-01)

The existing FR-1/FR-3 address now carries block index plus displayed-text character offset through app state and saves. Restore echo and legacy same-block observations preserve a loaded offset; real in-block movement updates it. Text entries use measured line starts after relayout; composite entries have a bounded approximation. Storage format and position version remain unchanged. Pending writes retain ordering records until completion, so idle-cache limits cannot discard active repair targets.

`ReaderContinuationFixTest` and `BlockPositionGeometryTest` cover the retained no-movement/eviction failures, in-block changes, document ownership, and layout projection. `ReaderLogicalPositionPageTest` covers long-paragraph reopen and TXT in-block page turns; both flavors compile, but it is NOT_RUN. See [round-3 repair evidence](../plans/code-review-2026-10-01-round-3.md). Device/process/visual criteria remain pending; status is not advanced to DONE.
