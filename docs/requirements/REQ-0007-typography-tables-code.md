# REQ-0007 — Typography, Tables, and Code Blocks

English | [中文](REQ-0007-typography-tables-code.zh.md)

Status: READY
Priority: P0
Milestone: M1

## Problem

AI-generated Markdown is often long and contains wide tables and long code lines. On a 6-inch E-Ink screen, fixed typography makes reading difficult: small text is illegible, large text causes frequent turns, table columns disappear, and long code lines are clipped. Users need persistent typography controls and table/code behavior that keeps all content reachable on a small screen.

Source: `docs/archive/init.md` §§15, 23, 24, 26; implementation-plan T09; renderer limitations in `docs/research/markdown-renderers.md` §§4–5 (R3 giant blocks, R4 tables).

## User Story

As a user reading AI material on E-Ink, I want to adjust and retain font size/family, line spacing, and margins, toggle code soft-wrap, and horizontally scroll wide tables, so that long documents, tables, and code remain readable on a small screen.

## Scope

- Four body-typography controls: font size, system font family, line spacing, and page margin.
- Persist all four settings in DataStore across restart and process recreation.
- Preserve reading context after reflow according to REQ-0003; exact pixel preservation is not required, but never jump to document start.
- Code blocks use monospace, show a language label, expose one-tap copy, and provide a soft-wrap toggle. E-Ink defaults to ON; when OFF, code scrolls horizontally.
- Tables use only `TableRenderMode.NORMAL`: place the table in a horizontal-scroll container so every column is reachable on narrow screens.
- Maintain legible text/code/table contrast in dark/grayscale themes without adding a syntax-highlighting engine.

## Non-goals

No bundled fonts (use system fonts only), `TableRenderMode.WRAP` or `CARD` (deferred to M2+, with no visible unavailable switch), mandatory syntax highlighting, full LaTeX/Mermaid/footnote/complex-HTML rendering, tap-zone editor/full gesture system, user-imported fonts, or per-level weight/letter-spacing controls.

## User Experience

Expose typography controls in the Reader menu or Settings: size slider/steps, system font choices, line-spacing steps, and margin steps. Changes preview immediately without restart and keep the current reading context. Code blocks show the fence language when known, and have a copy button that copies source without fence markers with a static success notice. The soft-wrap toggle is in code-block actions or typography settings and defaults ON in E-Ink. Wide tables show a horizontal-scroll affordance; horizontal movement within a table never triggers page turn (REQ-0005).

## Functional Requirements

- **FR-1:** At least five visibly distinct font-size levels; persist the selected level.
- **FR-2:** Offer at least two system font choices including the default and a non-monospace body font. If the system exposes only one, show one honestly. Persist selection.
- **FR-3:** At least three line-spacing levels, persisted.
- **FR-4:** At least three page-margin levels, persisted.
- **FR-5:** Render code with monospace. Show the fence language label; never invent a label when no language is specified.
- **FR-6:** Copy exact code-block body without fence markers; output must be testable.
- **FR-7:** Provide a persistent soft-wrap control. E-Ink defaults ON. When OFF, horizontal scroll must reach the end of a long line.
- **FR-8:** Use NORMAL tables in a horizontal container; no column may be permanently clipped.
- **FR-9:** Do not show WRAP/CARD controls in UI.
- **FR-10:** Read/write all four typography values through a shared DataStore settings layer. Reader receives style parameters and must not read storage directly.

## Acceptance Criteria

- **AC-1:** Change size, font, spacing, and margin; force-stop/restart; all four retain their new values.
- **AC-2:** For H1–H3, paragraph, and list samples, changing each size/spacing/margin level keeps text complete, without clipping or crash.
- **AC-3:** For `kotlin` and unlabeled fences, labels are correct; copy output matches fence body character-for-character.
- **AC-4:** New E-Ink session starts with soft-wrap ON. Turn it off for a long single-line sample and horizontally reach the final character.
- **AC-5:** On a table wider than a 6-inch viewport, horizontal scrolling reaches the final column with correct content; it does not turn the page.
- **AC-6:** Search UI/settings contain no WRAP/CARD/table-card mode switch.
- **AC-7:** Grayscale/dark-theme body, code, borders, and table text remain distinguishable; retain manual confirmation/screenshots.

## Edge Cases

Empty code, missing/unknown language tags, and oversized single-line code must render without failure; copy returns empty or exact body and never invents a label. Giant blocks may use explained plain-text fallback, without ANR/OOM. Very wide tables and long cells remain horizontally reachable; automatic cell wrapping is not required. Do not show fake font options on single-font systems. Typography also applies to TXT, while code/table features apply only to Markdown.

## E-Ink Considerations

Default code soft-wrap ON. Reflow is immediate with no smooth animation or crossfade. Table/code horizontal scroll is instant, with smooth scrolling disabled. Copy notice is static; no ripple, shimmer, or spinner. Syntax coloring is optional and must remain legible in grayscale.

## Standard Android Considerations

Subtle ripple and limited transitions are allowed, but typography does not depend on animation. Dark theme is allowed; dynamic color may be deferred. Standard soft-wrap may default OFF (unverified); the control and persistence are still required.

## Performance Considerations

Typography changes must reuse parsed blocks and only rebind/reflow; do not parse the entire document again. Avoid O(N²) document work. Measure giant-block fallback thresholds in T03/T13 rather than inventing values.

## Storage / Data Impact

Store four typography settings and soft-wrap in DataStore. Do not store font files or write originals. Settings schema changes require migration; clearing the database must not hide lost settings. See REQ-0011 for uninstall/clear invariance.

## Test Plan

Unit-test setting persistence and language-label parsing (known, absent, unknown). Reuse `tables.md`, `code.md`, `long.md`, and `chinese.md`; use T03 giant-block samples for long code. Instrumented tests assert AC-4/5 reachability and horizontal gesture arbitration with REQ-0005. Record E-Ink hardware checks for typography, code, and tables; mark missing devices NOT_RUN.

## Dependencies

Requires REQ-0003 position preservation after reflow, REQ-0005 page/gesture arbitration, REQ-0006 stable TOC anchors after reflow, REQ-0010 Settings entry, and T08 navigation/arbitration before T09.

## Open Questions

- T09 froze size to 5 levels (0–4, default 2, scale `0.85 + 0.15 × step`), line spacing to 3 levels (0–2, default 1, multipliers 1.00/1.25/1.50), and margin to 3 levels (0–2, default 1; 8/16/24 dp). See `ReaderStyle`.
- When unset, E-Ink soft-wrap is ON; Standard uses the stored value (`CODE_SOFT_WRAP_DEFAULT=true` is the storage fallback). Standard's first-use display default remains unverified.
- T09 chose a fixed system-font allowlist (`system/sans-serif/serif`; monospace only for code). OEM family queries differ and remain unverified. Android `Typeface` constants resolve all three. Single-font devices must not show fake choices.
- Markwon's default table limits (`markdown-renderers.md` S5) are bypassed by the app's native cell grid. UX-07 now synchronizes widths from actual styled cells; NORMAL horizontal reachability remains covered. Real-device readability and link taps remain unverified until T13.

## E-Ink typography and layout policy (2026-10-02)

Typography, margins, measure and justification now come from the active display profile (`DisplayProfile.readingLayout`; values in [eink.md](../architecture/eink.md) §6) instead of fixed Reader constants. The persisted steps, their counts (5 / 3 / 3) and their ranges are unchanged, and Standard resolves to the same numbers as before.

- **FR-13:** Body size, line height and page margin for each persisted step are resolved through the profile's reading-layout policy; the Reader and the Settings preview use the same resolver. Every step stays visibly distinct (FR-1/FR-3/FR-4) under each profile, and a line height never falls below the font size.
- **FR-14:** Under the E-Ink profile, flowing text is capped at 36 em (centred), so wide panels do not run 100+ characters per line; the cap scales with the font size. Code, tables and images keep the full width.
- **FR-15:** Under the E-Ink profile, Markdown paragraphs are inter-word justified with hyphenation and high-quality line breaking; literal TXT / fallback blocks are not justified. Standard keeps the platform's wrapping defaults, including after a switch back from E-Ink. Body text uses the theme's text colour (pure black on E-Ink).
- **AC-10:** On a 6-inch E-Ink device, default steps show the E-Ink numbers (18 sp base, 1.60 line height, 28 dp margins); on a 10.3-inch device the line stays within about 36 em. **NOT_RUN** — no device and no toolchain on the authoring host.
- **AC-11:** A Standard-mode reader still shows 16 sp, the legacy line spacing and the platform's wrapping defaults. Covered by `ReadingLayoutTest` and `ReaderEinkTypographyTest`; **NOT_RUN** here. Restoring those defaults after a runtime E-Ink → Standard switch is implemented (`ReaderTextView`) but has no automated test yet.
- Legibility, contrast, ghosting and page-turn comfort of these defaults are unverified until the E-Ink device matrix (T13) has run; the numbers are first-principles defaults to be confirmed or retuned there.

## UX-07 acceptance clarification (2026-09-30)

- **FR-11 / AC-8:** All rows share column widths measured from styled cell content, including headers and inline formatting. Empty cells retain their grid slot; rows share cell height. Draw 1 dp grid dividers and a 2 dp divider below the header. Wide tables remain horizontally reachable without page turns.
- **FR-12 / AC-9:** Code-copy feedback appears in the existing action button for two seconds after the latest tap, then returns to the copy action. It must not move the code body or following blocks, and the copied payload remains the exact fence-free body.
- Verification and screenshots are recorded in [ux-07-delivery.md](../plans/ux-07-delivery.md). This clarifies the existing READY requirement; hardware contrast remains unverified.
