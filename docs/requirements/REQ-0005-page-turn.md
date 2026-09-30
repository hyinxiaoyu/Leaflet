# REQ-0005 — Viewport Page Turns, Gesture Arbitration, and Reading Modes

English | [中文](REQ-0005-page-turn.zh.md)

Status: READY
Priority: P0
Milestone: M1 (depends on T07; delivered in T08 for navigation and interaction arbitration)

Source: `docs/archive/init.md` §§16, 17, 26; implementation-plan T08; overlap and clamp lessons from KOReader/Pilcrow in `docs/research/eink-readers.md` §5.

## Problem

E-Ink is poorly suited to long smooth-scroll gestures: repeated refreshes during dragging cause flashing and ghosting, and users have trouble stopping at a clean reading boundary. Viewport page turns should jump immediately by visible height minus a small overlap, preserving context. Standard users still need continuous scrolling. Tap zones must not conflict with links or horizontal table/code gestures; without explicit arbitration, accidental turns are frequent.

## User Stories

- As an E-Ink reader, I want right/left tap zones to move one screen forward/back with a small overlap so that long documents remain continuous without drag flicker.
- As a phone user, I want natural continuous scrolling for short or browse-style reading.
- As a reader, I want links, horizontal tables, and long-press actions not to trigger page turns.

## Scope

- Use `scrollBy ±(viewportHeight − overlap)`. Default overlap is 5%; permitted range is 3–8%. It may be a fixed constant or a limited setting, but the value must be testable.
- Divide the page into left/center/right tap zones: previous page, menu toggle, next page. Default to thirds; future user adjustment is reserved, but no zone editor is in this REQ.
- Provide `CONTINUOUS_SCROLL` and `PAGE_TURN`; E-Ink defaults to PAGE_TURN and Standard to continuous scroll. Mode switches preserve position.
- Give links, buttons, code-copy buttons, and horizontal table/code gestures priority over page-turn zones. Long-press never turns a page.
- Clamp at document start/end. Account for system Insets, menu occlusion, rotation, and rapid repeated turns. E-Ink uses instant scrolling only.
- Show static fine-grained reading progress, primarily a percentage, with throttled updates.

## Non-goals

No editor, accounts/cloud sync, AI/RAG, PDF/EPUB, full annotation, plugins, library-wide FTS, vendor SDK, two-column layout, complex animation, true paginated layout engine, configurable tap-zone editor/full gesture system, pinch zoom, or auto-scroll. This is viewport scrolling, not typeset pagination.

## User Experience

1. In PAGE_TURN, tapping the right zone jumps immediately forward with 5% overlap; tapping left goes back. At either boundary it does nothing or shows a subtle static boundary notice.
2. Tapping the center toggles the reading menu. Menu occlusion is included in visible height so turns remain accurate.
3. In CONTINUOUS mode, finger scrolling feels natural. Standard may use platform smooth behavior; E-Ink still disables smooth motion through REQ-0004.
4. Links/buttons run their own action; wide tables/code scroll horizontally; long-press selects or opens a future menu, but never turns the page.
5. Mode switch lives in reading menu or Settings and preserves the same reading block (REQ-0003 position semantics).

## Functional Requirements

- **FR-1 Algorithm:** `next: scrollBy(viewportHeight − overlap)` and `prev: scrollBy(−(viewportHeight − overlap))`; default overlap is 5% of viewport, bounded to 3–8%.
- **FR-2 Visible height:** Use actual Reader viewport after system Insets and menu/progress obstruction. Recompute after rotation, menu visibility, or window changes.
- **FR-3 Tap zones:** Left/center/right map to previous/menu/next. Default to equal thirds; record the actual implementation widths (currently unverified) and allow future adjustment.
- **FR-4 Two modes:** Switch between continuous scroll and page turn. Defaults: EINK → PAGE_TURN, STANDARD → CONTINUOUS_SCROLL. Preserve the same ReadingPosition block/anchor.
- **FR-5 Arbitration:** Links/buttons/code-copy/horizontally scrollable tables/code consume their touches first. Horizontal scroll never turns the page; long-press at or above the platform threshold never turns it.
- **FR-6 E-Ink scrolling:** Page turns and restoration are immediate (`scrollToPositionWithOffset`-like); no `smoothScroll` family calls.
- **FR-7 Boundaries:** Clamp at top and bottom without blank jitter. Repeated rapid turns remain within document bounds. Account for end-of-list clamp behavior (for example, Pilcrow spacer/headroom lessons) and record the chosen equivalent.
- **FR-8 Progress:** Show static percentage plus a fine progress bar. Throttle updates using position-save cadence or at least one second; do not redraw frequently.

## Acceptance Criteria

- **AC-1:** In PAGE_TURN, measure five consecutive forward turns: each moves `viewportHeight − overlap` within 2 px; overlap content appears on both adjacent screens; measured overlap is 3–8%, default 5% ±0.5%.
- **AC-2:** At top, left tap leaves position unchanged without blank overscroll. At bottom, right tap remains at end. Ten rapid forward taps end at document bottom without overshoot or crash.
- **AC-3:** Link/button/code-copy taps run their action without turning. Horizontal drag on a wide table scrolls only the table. A ≥500 ms long press in any zone does not turn.
- **AC-4:** Rotation recalculates distance from new visible height and still clamps. With menu open, landing error is no more than overlap.
- **AC-5:** Switching PAGE_TURN ↔ CONTINUOUS keeps the same block/anchor (`blockIndex` unchanged), not the document top.
- **AC-6:** E-Ink invokes no smooth-scroll APIs and shows no smooth-position animation.
- **AC-7:** Record measured distances, ratios, counts, and tolerances; no vague “supports X.”

## Edge Cases

A document fitting one screen has no-op page turns and 100% progress. If menu/progress covers half the viewport, calculate using the actual visible height and do not skip obscured content. A turn during rotation uses the latest layout, not stale height. Giant-block fallback uses the same viewport semantics. TalkBack/keyboard turns, if supported, use FR-1 distance; accessibility details remain unverified.

## E-Ink Considerations

Use instant jumps, static progress, and throttled updates. Call `EInkController.onPageTurn`; without vendor SDK use NoOp/system defaults and make no partial-refresh claims. Disable smooth scroll, overscroll, ripple, and crossfade according to REQ-0004.

## Standard Android Considerations

Continuous scroll is default. Normal scroll physics are allowed through DisplayProfile/MotionPolicy. The same three tap zones and center-menu behavior apply.

## Performance Considerations

A page turn is an O(1) scroll command and must not reparse. Serialize rapid turns with viewport recomputation to avoid races. Cache viewport height and invalidate/recompute it on layout changes.

## Storage / Data Impact

Persist current mode (`CONTINUOUS/PAGE_TURN`) and optional overlap in DataStore. Reading position remains REQ-0003. No Room change.

## Test Plan

JVM tests cover the distance formula, overlap bounds, clamping, and arbitration decision table. Instrumented tests cover zones, horizontal scroll, long-press, rotation, position-preserving mode switch, and rapid turns. Walk through the E-Ink no-smooth/static-progress behavior.

## Dependencies

Requires REQ-0003 position semantics, REQ-0004 display policy, REQ-0002 visible block height, and REQ-0006 TOC positioning. T12 depends on this for mode integration acceptance.

## R3 evidence (2026-09-28)

The R3-05 acceptance suite measures the turn distance on the real composed viewport at 3% and 8% overlap and drives five consecutive turns, the head/tail clamps, the centre menu, long-press, link taps, and horizontal table/code scrolls. It found and fixed DEF-R3-05-01 (the tap handler captured a stale `overlap`, so a changed overlap had no effect until a mode/rotation change). See [r3-05-delivery.md](../plans/r3-05-delivery.md). Hardware ghosting/refresh remains NOT_RUN.

## UX-06 evidence (2026-09-30)

The reader chrome (title, notices, search bar, reading menu, progress footer) moved from the body's
in-flow `Column` into an overlay layer over a full-height list (FR-2 becomes a real calculation: the
measured top/bottom chrome is subtracted through `PageTurn.visibleHeightPx`, where it was previously
always zero). The progress footer is now an anchored static bar (FR-8), the document title is shown
only while the reading menu is open, and a one-time opaque tap-zone onboarding illustrates the three
FR-3 zones with a mode-aware subtitle; its dismissal is persisted and the decision is the pure
`TapZones.shouldShowHint`. Position semantics, the FR-1 distance formula, and the FR-6 no-smooth rule
are unchanged. See [ux-06-delivery.md](../plans/ux-06-delivery.md); the instrumented acceptance tests
compile but are NOT_RUN without a device, and E-Ink hardware remains NOT_RUN.

## Open Questions

- Zone widths are unverified. KOReader grid is only a reference; T08 records equal thirds (`TapZones`), and mis-tap rate remains for T12/T13 walkthrough.
- Whether overlap should be a user slider or fixed 5% was unverified. T08 wired geometry and DataStore (`PageTurn.coerceOverlap` clamps 3–8%, `SettingsStore.pageOverlap` defaults to 5%, shared with `PageTurnBehavior`); slider UI is deferred to M2.
- TalkBack focus and keyboard Space behavior remain unverified and are not frozen by this REQ.
