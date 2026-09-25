# REQ-0002 — Markdown / TXT Reader

English | [中文](REQ-0002-markdown-txt-reader.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0 (T03 samples → T04 storage foundation → T06 basic Reader; M0 exit flow)

Source: `docs/archive/init.md` §§9, 10, 14, 15, 26; implementation-plan T03/T06; static loading and degradation guidance in `docs/research/eink-readers.md` §5.

## Problem

User libraries mix Markdown and plain text: long AI-generated Markdown with tables, code blocks, and task lists coexists with TXT notes. Both must be readable. Markdown follows the required syntax table; TXT remains literal. Unsupported syntax (LaTeX, Mermaid, complex HTML, footnotes, and similar) must not make the whole document unreadable. Large AI-generated documents must open cancellably without freezing or crashing.

## User Stories

- As a reader of AI-generated material, I want `.md` / `.markdown` files to render titles, lists, quotes, code, tables, and other basic structure so that technical documents are readable rather than source dumps.
- As a TXT reader, I want `.txt` files to remain literal, including BOM handling and newlines, so that plain-text notes retain their content.
- As an E-Ink user, I want unsupported syntax to degrade to readable text and large files to explain any fallback instead of hanging, so that every file has a predictable outcome.

## Scope

- Open a read-only stream by Document ID through `DocumentSource`; parse on cancellable background work and expose loading, failure, and degraded states.
- Support `.md`, `.markdown`, and `.txt`, case-insensitively.
- Render required Markdown basics: H1–H6, paragraphs, bold, italic, strike-through, unordered/ordered/task lists, quotes, fenced and inline code, links, image placeholders, horizontal rules, and readable tables. Full local-resource behavior belongs to REQ-0009; table interaction enhancements belong to REQ-0007.
- Degrade unsupported LaTeX, Mermaid, complex HTML, footnotes, math formulas, and PlantUML to visible literal text/code without failing the full document.
- Extract title in order: YAML frontmatter `title` → first H1 → filename without extension. Support only a constrained frontmatter subset; malformed metadata must not fail the body.
- Decode TXT as UTF-8 and strip BOM. If encoding is unknown, show an explicit fallback; do not claim automatic charset detection.
- Produce `ParsedDocument{title, toc, blocks, positionMap}` and render by block. Do not put an arbitrary whole document in one TextView. Bound a giant block by splitting into plain-text chunks or an equivalent strategy; derive thresholds from T03/T13 evidence.
- Keep block-position mapping compatible with REQ-0003 position restore, REQ-0006 TOC, and REQ-0008 search.

## Non-goals

No editing or rewriting originals, AI Chat/RAG/vector database, PDF/EPUB/CBZ, OCR, handwriting, advanced annotation, backlinks, plugins, WebDAV/Git/Syncthing clients, accounts/cloud sync, full tagging, online store, full LaTeX/Mermaid rendering, TABLE WRAP/CARD modes (NORMAL only), mandatory syntax highlighting (grayscale readability is enough), library-wide FTS, two-column large-screen layout, or complex animation.

## User Experience

1. Show static “Opening…” text and item/percentage progress, then render blocks. Back/close cancels parsing, frees resources, and leaves no partial page.
2. Missing, unauthorized, or damaged files show an explicit error page with reason, Back, and Retry; no blank screen or transient-only toast.
3. Unsupported syntax shows its source in a code-style block with an optional notice. A giant-block fallback shows split plain text and a banner explaining that part of the document is in plain text.
4. The Library row and Reader title bar use the same title rules.
5. Empty files show an explicit empty-file placeholder.
6. When opening documents rapidly, cancel old work and ensure a late old result cannot replace the current document.

## Functional Requirements

- **FR-1 Open and cancel:** Open a read-only stream by ID through `DocumentSource`; parse in a background coroutine. Closing or switching documents cancels old work and closes the stream; stale results cannot replace the current document.
- **FR-2 Required syntax:** Render the §10 syntax set readably on ordinary samples: distinguish H1–H6; separate paragraphs; support bold/italic/strike, two list types and read-only task checkboxes, quotes, code, inline code, tappable links, image placeholders, rules, and readable table cells.
- **FR-3 Graceful fallback:** Render unknown syntax (LaTeX/Mermaid/complex HTML/footnotes/math) as literal text blocks. Catch an individual block parse failure as a fallback block; render all other blocks.
- **FR-4 Title:** `frontmatter.title` (restricted string scalar) → first H1 text → filename without extension. Ignore missing, invalid, or non-scalar metadata without failing the body.
- **FR-5 TXT/BOM:** Decode TXT as UTF-8, strip BOM, preserve line breaks and blank lines, and show an empty-state placeholder for zero bytes.
- **FR-6 Unknown encoding:** If non-UTF-8 data cannot be decoded, show an explicit notice/fallback such as “Encoding could not be identified; some content is shown with replacement characters.” Do not claim detection of a specific encoding; do not crash.
- **FR-7 Bounded blocks:** Do not put the whole document in one TextView. `ParsedDocument.blocks` is the sole source for rendering and navigation. Bound giant paragraphs/code blocks through split plain-text fallback or equivalent incremental rendering; remain cancellable and avoid OOM/ANR/crash.
- **FR-8 State contract:** Expose `Loading / Content(degradedBlocks marked) / Error(reason)` per document. Loading is static (see E-Ink section).
- **FR-9 Languages and malformed syntax:** Mixed Chinese/Japanese/English and emoji render without corruption or missing characters. Malformed Markdown, including unclosed fences or uneven tables, must not fail the entire document.

## Acceptance Criteria

- **AC-1:** A sample covering all required syntax (H1–H6, emphasis, both list types, task list, quote, code, link, image, rule, basic table) shows each form distinctly in a block-type assertion and manual checklist; the full document opens.
- **AC-2:** A sample with LaTeX, Mermaid, complex HTML, and footnotes opens fully; unknown blocks show literal source in code style and other blocks remain unaffected.
- **AC-3:** Frontmatter-title, H1-only, and no-frontmatter/no-H1 documents use the expected title source. Unclosed or non-scalar frontmatter falls back to the next source while the body still displays.
- **AC-4:** BOM TXT, empty file, and mixed Chinese/Japanese/English/emoji TXT strip the BOM, show the empty placeholder, and display text without corruption.
- **AC-5:** Damaged encoding shows an explicit fallback without crashing or asserting a specific detected charset.
- **AC-6:** Both 10 MB sample shapes (many small blocks and one giant block) open cancellably; cancellation stops within 1 second without OOM/ANR/crash. Giant-block fallback shows a notice; no silent truncation or false claim of complete reading.
- **AC-7:** If A is still parsing when the user switches to B, B is ultimately shown, A's stale result is discarded, and A's stream is closed.
- **AC-8:** Every criterion has a sample and assertion or review record; no unmeasurable “supports X” claims.

## Edge Cases

Cover empty files, frontmatter-only files, a single unbroken 1 MB line, unclosed fences, missing table separators, nesting depth ≥6, empty link destinations, missing/damaged images, external deletion or permission loss during parse, and multiple H1s. The first H1 supplies the title; duplicate TOC handling is REQ-0006.

## E-Ink Considerations

Use static text with throttled percentage/count progress. Do not use infinite spinners, shimmer, or crossfade. Code soft-wrap defaults ON; do not depend on colored syntax highlighting. Tables use NORMAL mode and horizontal scrolling. Avoid transparency, shadows, gradients, blur, and frequent UI updates.

## Standard Android Considerations

Subtle ripple and normal scrolling are allowed. Images and links share the same block model across modes; only visual intensity may vary through DisplayProfile. Do not build a heavy renderer only for Standard.

## Performance Considerations

Parse in background threads. A single whole-document TextView is prohibited. Measure giant-block limits with T03/T13 samples at 100 KB / 1 MB / 5 MB / 10 MB. Switching and cancellation must release streams and intermediate ASTs. Record peak memory with the T03 template.

## Storage / Data Impact

Do not store document bodies in the database. Parsed output is in memory; any cache must be invalidated when the source changes (see REQ-0003). The database may store title/TOC summaries only if T06 records the decision. Parsing never writes back or copies originals.

## Test Plan

- Use the complete T03 fixture matrix: required syntax, empty files, large TXT, duplicate headings, BOM, damaged encoding, giant paragraph/code block, nested lists, and Chinese/Japanese/English/emoji.
- JVM tests cover title/frontmatter matrices, fallback block types, and stale-result protection.
- Instrumented tests open and cancel both 10 MB shapes and rapidly switch documents.
- Record a manual readability checklist. Report only measured performance numbers.

## Dependencies

Requires T03 fixtures, T04 read-only `DocumentSource`, and REQ-0001 Document metadata. Provides the position map used by REQ-0003, same-source TOC in REQ-0006, and visible-text search.

## Open Questions

- Parser choice was initially open pending T00. T06 selected Markwon 4.6.2 (Apache-2.0), `core + ext-strikethrough + ext-tables + ext-tasklist + recycler` (with `recycler-table` reserved for evolution; do not mix with `TableEntryPlugin`). Accept transitive `com.atlassian.commonmark` 0.13.0 without override. All direct use is behind `MarkdownRenderer` (ADR-0004); no image network library (T10 uses platform decoding).
- Giant-block threshold was initially unmeasured. T06 uses `BlockLimits.MAX_BLOCK_CHARS = 32 KiB`, splitting oversized blocks into bounded plain-text entries with a notice. `ReaderSession.MAX_READ_BYTES` is 16 MiB, covering the 10 MB tier. T03/T13 may revise thresholds with evidence without overturning ADR-0006.
- T06 limits frontmatter to optional `title` as a string scalar (single/double quotes allowed). Ignore `date`/`tags`; unclosed fence or non-scalar title falls back to the next title source without affecting the body.
