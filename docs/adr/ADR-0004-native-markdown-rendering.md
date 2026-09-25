# ADR-0004 — Native Markdown Rendering

English | [中文](ADR-0004-native-markdown-rendering.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

The first release must not implement its own complete Markdown parser (`docs/archive/init.md` §9). T00 compared Markwon, WebView + marked, Compose Markdown libraries, and a custom parser; Markwon was selected (`docs/research/markdown-renderers.md` §§2–3).

Research verified that Markwon 4.6.2 (released 2021-02-08, Apache-2.0) is the latest release found; its transitive `commonmark` is 0.13.0 (CommonMark spec 0.29). The `recycler` artifact provides block entries through `MarkwonAdapter` and `SimpleEntry` caching. The built-in table implementation has documented limits (full-width/equal-width columns, no copying, and links need `TableAwareMovementMethod`). GFM task-list check toggles are visual only.

### Decision

1. Use Markwon 4.6.2 with a limited artifact set: `core`, `ext-tables`, `ext-tasklist`, `recycler`, and `recycler-table` for the evolution path. Freeze the version in the version catalog. Do not add image-glide/image-coil/image-picasso or renderer-side okhttp; T10 uses platform decoding and viewport sampling.
2. Use a project-owned block pipeline: SAF/content stream → cancellable background parse (Markwon → commonmark-java 0.13.0 AST) → split top-level blocks → render each block in RecyclerView. Ordinary blocks use TextView entries; fenced code and tables use independent horizontal-scroll entries. Never place an arbitrary full document in one huge TextView.
3. Keep one `MarkdownRenderer` adapter as the only direct Markwon seam in `:core:markdown`. Inject E-Ink policy through DisplayProfile / MotionPolicy / EInkController, not into the renderer library.
4. Accept the transitive commonmark 0.13.0. Do not override it with a newer `org.commonmark` version because it may couple to Markwon internals and compatibility has not been verified.
5. Do not build a parser. Reuse the existing parser's AST only for block splitting. Unsupported syntax such as LaTeX, Mermaid, complex HTML, or footnotes must degrade gracefully rather than failing the whole document.

### Alternatives considered

- **WebView + marked.js:** broad GFM support, but no block recycling/cancellation, pixel-only scroll position, uncontrollable E-Ink refresh, and unsanitized output by default. Rejected.
- **Compose Markdown library:** the mikepenz option inverts Reader's dependency on Compose and brings a Coil network path; the jeziellago option wraps Markwon, uses a private fork and JitPack, and adds supply-chain risk. Rejected.
- **Custom parser/rendering layer:** requires spec compliance, GFM semantics, and large-file tuning while duplicating Markwon. Rejected; only AST-based block splitting remains allowed.

### Consequences

- R1 risk: Markwon 4.6.2 has had no release since 2021-02-08 and its master head was from 2021-03. Mitigate with a minimal artifact set and the `MarkdownRenderer` seam; T02 verifies build compatibility. A future parser replacement requires a new ADR and must preserve the Reader contract.
- M1 tables use NORMAL mode and horizontal scrolling so narrow screens can reach every column. `TableEntry` / `TableEntryPlugin` are an evolution path and must not be mixed with `TablePlugin`. WRAP / CARD are architecture reservations only; do not expose unusable switches.
- Relative images use `ImageDestinationProcessor` and a custom `SchemeHandler` to map to SAF content URIs and reject paths outside the grant. Do not add GIF/SVG decoders in the MVP. Remote images remain static placeholders and do not trigger network access.
- Record Markwon's Apache-2.0 license. Measure APK-size impact in T13.

### Follow-up

T06 must verify the adapter against real samples, including whether `parse/render(Node)` is enough for block rendering. If not, evaluate commonmark AST access (not yet verified). Set block sizes and large-file thresholds only after T03 samples and T13 measurements; do not invent performance numbers.
