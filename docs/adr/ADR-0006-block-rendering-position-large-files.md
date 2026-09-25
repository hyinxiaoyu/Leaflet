# ADR-0006 — Block Rendering, Position and Large Files

English | [中文](ADR-0006-block-rendering-position-large-files.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

Reader is the highest-priority module (`docs/archive/init.md` §15) and must meet three connected needs: stable large-file handling (10 MB without OOM/ANR/crash), durable reading position beyond pixel `scrollY`, and rendered-position mapping for search and TOC navigation (T08/T11). Pilcrow research supports a RecyclerView of blocks, block-level ScrollAnchor, cancellable background parsing, and fallback placeholders. It also highlights geometry issues such as clamping `scrollToPositionWithOffset` at the end.

### Decision

1. Use parse → split into blocks → RecyclerView, recycling one entry per block. TOC jumps, search navigation, and position restoration use block addressing; never assume one file equals one TextView.
2. Version `ReadingPosition` with `documentId`, version, `blockIndex`, `blockOffset`, progress, and optional anchor. Restore by same-version block/offset first; after content, font, or width changes, validate and fall back to anchor/progress. Block indices are not assumed stable across edits. Invalidate cache when the document changes. MVP may start with scroll position + progress, but the fields and fallback semantics are reserved from day one.
3. Do not put an oversized paragraph, code block, or table into one TextView entry. Split a giant block into plain-text entries above a measured threshold and show an explanatory degradation banner. Unsupported syntax follows the same graceful-fallback rule. Never silently truncate or claim the document is fully read.
4. Large-file loading/parsing runs in cancellable background coroutines. A stale result must not overwrite a newly opened document; cancellation releases resources. The 10 MB sample must be cancellable and avoid OOM/ANR/crash; documented plain-text fallback is allowed. Do not replace the reading path by rejecting every large file.
5. Position mapping is part of the contract. Markdown search uses visible text, not syntax markers, and maps through parse/render positions. Never treat source offsets as screen coordinates. Search is cancellable and stale results cannot overwrite newer ones.

### Alternatives considered

- **One TextView for the entire document with pixel `scrollY`:** simplest, but expensive main-thread measurement/layout, unstable position after rotation or font changes, and poor E-Ink fit. Rejected.
- **True paginated layout engine:** best page fidelity, far beyond MVP scope. T08 uses viewport turns with a default 5% overlap; reconsider after M2 if needed.
- **Regex post-processing for search mapping:** syntax such as LaTeX can contaminate results; protect it during parsing rather than regex cleanup. Rejected in favor of parse/render-time mapping.

### Consequences

- `:core:reader` / `:core:markdown` contracts include `ParsedDocument` (title, TOC, renderable content, position mapping) and versioned `ReadingPosition` (`docs/architecture/reader.md`).
- T03 provides both many-small-block and single-giant-block shapes at 100 KB / 1 MB / 5 MB / 10 MB. Set thresholds only from measurement.
- Save position with throttling and flush on exit (T07). Missing, renamed, or unauthorized recent documents need explicit messaging. Recreating the Reader page must not reparse unchanged content.

### Follow-up

Measure block thresholds, refresh cadence, and performance in T03/T13. The “bounded” values may change with evidence without overturning this decision. Library-wide FTS is deferred to M4, but schema and Repository must allow it later. Current T11 search covers only the already loaded document's literal text.
