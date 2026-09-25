# REQ-0006 — Table of Contents and Navigation

English | [中文](REQ-0006-toc.zh.md)

Status: READY
Priority: P1
Milestone: M1 (depends on T07; delivered through T08 with REQ-0005 arbitration)

Source: `docs/archive/init.md` §§15, 26; implementation-plan T06 title extraction / T08 hierarchy, duplicate anchors, and navigation; same-source TOC lesson from Pilcrow in `docs/research/eink-readers.md` §5.

## Problem

AI-generated documents may have hundreds of headings. Without a TOC, readers must search by paging blindly. The TOC must derive from the same render model: separate TOC and render parsing can misalign repeated or special-character headings and frontmatter. Leaflet needs a hierarchical TOC with stable unique anchors that jumps to the corresponding rendered block.

## User Stories

- As a long-document reader, I want an H1–H6 outline so that I can understand the document structure quickly.
- As a study reader, I want tapping a heading to jump to its text.
- As a reader of repeated headings such as “Summary,” I want each entry to navigate independently rather than collide on one anchor.

## Scope

- Derive the TOC from the same `ParsedDocument.blocks` as rendering. Preserve H1–H6 hierarchy/indentation. Documents without headings show a “No section headings” placeholder.
- Give repeated headings stable unique anchors (for example `summary`, `summary-1`, `summary-2`) with a recorded suffix rule.
- Tap an entry to land at the corresponding block top, offset for viewport occlusion. E-Ink jumps instantly. The destination may receive a static indicator (exact style initially unverified).
- Reuse the REQ-0003 position pipeline and save after landing. Runtime mode switches must not invalidate anchors.
- TXT has no TOC unless Markdown headings exist; do not invent sections.

## Non-goals

No editor, AI-generated outline, PDF/EPUB TOC, backlinks, plugins, library-wide FTS, bookmarks/annotations (jump indicator is not a bookmark), thumbnail page map, configurable gestures, two-column layout, complex animation, independent source-offset/line-number TOC parser, or page-number input.

## User Experience

1. Reader menu → TOC. Show a drawer/list with H1–H6 indentation and static current-chapter highlighting updated without flicker.
2. Tap a heading to close or retain the drawer (implementation choice, record it) and jump to the matching block. No smooth-scroll/flicker animation on E-Ink.
3. Repeated headings appear as separate entries and land at distinct locations.
4. Documents without headings show “This document has no section headings.”
5. The destination block is visible below any top-bar obstruction; see AC for tolerance.

## Functional Requirements

- **FR-1 Same source:** Derive TOC from the heading blocks in the same `ParsedDocument`; no standalone regex or second parse.
- **FR-2 Hierarchy:** Preserve six levels for indentation. A heading that skips levels (H1 → H3) retains its actual level; do not rewrite the hierarchy.
- **FR-3 Unique anchors:** Normalize same-text headings using a recorded case/space/punctuation rule; produce deterministic suffixes. With the same content fingerprint, repeated parses yield the same anchor sequence. Navigate by anchor → block index, not by text search.
- **FR-4 Tap navigation:** Scroll to the target block top after subtracting top-bar/sticky occlusion. E-Ink is instant. Save the settled position through REQ-0003.
- **FR-5 Frontmatter safety:** `#` inside frontmatter or code blocks never enters the TOC; malformed frontmatter does not prevent TOC construction.
- **FR-6 TXT:** Show a no-TOC placeholder; do not synthesize chapters.
- **FR-7 Current-section mapping:** Determine current chapter from rendered block ranges, not source offsets treated as screen coordinates.

## Acceptance Criteria

- **AC-1:** For H1–H6 including H1→H3 jumps, TOC row count equals heading-block count and indentation matches actual level. No `#` from frontmatter/code appears.
- **AC-2:** Three “Summary” headings in different sections have distinct anchors stable across two parses. Each tap lands at a different block with matching anchor mapping.
- **AC-3:** Any TOC tap places the target block in the visible viewport within 4 px after occlusion offset; E-Ink has no smooth scroll; saved `blockIndex` equals target.
- **AC-4:** Markdown without headings and TXT show a placeholder, without crash or fabricated entry.
- **AC-5:** A 1,000+ heading document opens the TOC and supports jumps without ANR. The numeric performance threshold remains unverified.
- **AC-6:** Assert row count, anchor sequence, and destination block index; do not use vague support claims.

## Edge Cases

Empty headings, inline formatting in headings, emoji/Chinese/special characters, parser degradation, and targets in the end-of-list clamp region must work. If the whole document falls back to plain text, TOC is empty with a placeholder. End navigation follows REQ-0005 clamping.

## E-Ink Considerations

Jump instantly. Current-section highlighting is static and throttled to at least one second; no flashing/animated indicator. Drawer open/close has no transition animation.

## Standard Android Considerations

Use the same pipeline and landing semantics. Standard may scroll the drawer normally but has no separate TOC parser.

## Performance Considerations

Derive TOC in O(number of headings); create anchors in one linear pass. Recycle rows for 1,000+ headings rather than inflating all rows at once; record implementation evidence.

## Storage / Data Impact

Do not persist a separate TOC; derive it from parsed content. A title summary cache for list speed may be considered and recorded, but never store document bodies.

## Test Plan

Unit tests verify TOC-to-heading source identity, repeatable duplicate anchors, normalization across case/space/punctuation/emoji/Chinese, and frontmatter/code exclusion. Instrumented tests verify tap landing/index, end clamp, 1,000-heading walkthrough, and no-heading/TXT placeholders.

## Dependencies

Requires REQ-0002 block model/title extraction, REQ-0003 position save/anchor, and REQ-0005 navigation scroll semantics. Search reuses the mapping.

## Open Questions

- Slug normalization/suffix format was unverified. T08 uses T06 `Headings.slugify` + `uniqueAnchors` (`base`, `base-1`, `base-2` in first-seen order). Navigation is occurrence-aware: the kth same-name TOC row maps to the kth same-name heading block via `TocNavigation`, not text search. Fragment interoperability is for T10.
- Destination style was unverified. T08 uses a static bold target (`jumpedBlock`), no animation; reconsider in M2.
- Current chapter is initially the nearest heading above the first visible block via `TocNavigation.currentChapter`, updated by existing scroll observation and sharing REQ-0003 throttling. There is no separate timer; tune in T12.
