# REQ-0014 — Discoverable Reader Controls

English | [中文](REQ-0014-reader-controls.zh.md)

Status: IN_PROGRESS
Priority: P1
Milestone: Current daily-reading product iteration
Source: user request to continue polishing ebook-style reading; current Reader footer/menu and PAGE_TURN detector.

## Problem

The Reader hides its only menu entry behind a center tap and a one-time tutorial. Its action row does not wrap, so narrow windows and enlarged fonts can hide actions. A body gesture can also leave the Reader and still be recognized on release. These are daily-use reliability and discoverability gaps.

## User Stories

- As a reader, I can find the menu and turn screens without remembering hidden tap zones.
- As a reader using large fonts or a narrow device, I can reach every reading action.
- As a reader, I can reopen the gesture instructions, and a tutorial or out-of-bounds gesture never changes my reading position.

## Scope

- One static footer with progress and a visible menu entry; PAGE_TURN also exposes previous/next controls with boundary-disabled states.
- A wrapping action menu capped at 40% of the Reader viewport and scrollable when needed; existing TOC/search/refresh/mode/back callbacks remain in use.
- A repeatable Help entry reuses the existing tutorial without resetting the persisted first-run flag.
- Cancel a gesture permanently when its pointer leaves the Reader; ignore background reading actions while the tutorial is shown or initial position restoration is pending.
- English/Simplified Chinese labels, at least 48 dp control heights, shared Standard/E-Ink implementation and existing no-motion policy.

## Non-goals

No new pagination engine, page-count fiction, progress storage change, physical-key mapping, account/sync/editor, vendor refresh integration, new dependency, or unrelated redesign.

## User Experience

1. A visible menu control is available after opening a document. In page-turn mode, side controls turn through the same instant page-turn path as taps/swipes.
2. At the beginning/end, the corresponding turn control is disabled. Continuous mode keeps its menu entry and natural scrolling.
3. The menu wraps actions and scrolls within its height cap; it does not resize the underlying list.
4. Help opens the existing instructions. Dismissal returns to the same position; touching outside the tutorial cannot operate the Reader behind it.

## Functional Requirements

- FR-1: Footer controls use real LazyListState boundaries, the existing progress output and existing menu state; no parallel navigation state.
- FR-2: Controls have at least 48 dp height, standard enabled/disabled button semantics and stable test tags.
- FR-3: Menu width determines wrapping; maximum menu height is 40% of the fixed body viewport. Every action is reachable through scrolling.
- FR-4: Help uses session state and existing first-run dismissal; persisted reading mode and position are preserved.
- FR-5: Pointer exit cancels the whole gesture even if it re-enters before release. Child gestures retain priority.
- FR-6: Tutorial and pending restore block body/menu/turn actions; all turns remain instant and original files remain read-only.

## Acceptance Criteria

- AC-1: Footer menu opens/closes without moving body text; visible previous/next controls have the same turn distance as body taps and correct boundary states.
- AC-2: At 280 dp width and 200% font scale, menu actions remain reachable and menu height stays within its cap.
- AC-3: Help can be reopened after first-run dismissal; footer/page-margin touches cannot operate the document beneath the tutorial.
- AC-4: Out-of-bounds-and-back gestures produce no page/menu action; in-bounds taps/swipes and child-consumption behavior remain unchanged.
- AC-5: Relevant JVM/resource/component checks pass; actual Android/E-Ink checks are independently recorded and never claimed from compilation alone.

## Edge Cases

A single-screen document, the final line, pending logical-position restoration, rapid input, continuous mode, narrow landscape windows, 200% system font scale, a previously dismissed tutorial and pointer exit/re-entry.

## E-Ink Considerations

Opaque, static controls; no new animation, ripple override or redraw timer. Continue using centralized MotionPolicy and the generic EInkController; the bounded menu has no fling after release when animations are disabled. Hardware refresh/ghosting requires device evidence.

## Standard Android Considerations

The same controls and boundaries apply. Preserve continuous scrolling, system font scale and standard focus/disabled semantics.

## Performance Considerations

Use existing list/menu state; no document reparse for controls or gestures. The bounded menu scrolls independently and never changes list dimensions.

## Storage / Data Impact

No schema or preference-key changes. Help does not clear the existing tutorial-seen flag. Source files are never modified.

## Test Plan

JVM gesture regression tests for pointer exit/re-entry and cancellation; production Reader page cases for footer navigation/menu/help; production control component cases for narrow/large-font menu reachability; dual-flavor fresh component compilation, actual resource linking and changed-file formatting.

## Dependencies

REQ-0003 restoration, REQ-0004 motion policy, REQ-0005 paging and REQ-0010 reading controls. Reuse the current ReaderScreen and tutorial rather than introducing another reader implementation.

## Open Questions

Android execution and real E-Ink acceptance remain constrained by the current environment. This accepted requirement defines the authorized implementation slice, not a DONE or release claim.
