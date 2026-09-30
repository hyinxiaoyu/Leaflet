# Huge single block drill (small-scale shape demo)

This file shows the SHAPE of the generated `huge-*` fixtures at reviewable
size. The real stress files live in `testdata/generated/` (see README).

## Case A: one giant paragraph

A single paragraph of ~4 KB with no blank lines. The reader must bound it
(split, window, or plain-text fallback) instead of laying out one unbounded
block. Full text elided here; the generated file repeats a sentence pattern
until it passes its target size.

## Case B: one giant fenced code block

A single fenced block of ~4 KB with very long lines. The reader must soft-wrap
or horizontally bound it; E-Ink defaults to wrap. Again, the generated file
scales this shape to 100 KB–10 MB.

## Case C: many small blocks

Thousands of small headings, paragraphs, lists, quotes, tables, and code
blocks. Exercises incremental parsing, scroll stability, TOC length, and scan
time without any single pathological block.

Assertion summary for T06 reader work:

- no OOM / ANR / crash on any generated file;
- a visible loading or fallback notice for the giant-block shapes;
- loading is cancellable and cancellation releases resources;
- content stays readable (no silent truncation).
