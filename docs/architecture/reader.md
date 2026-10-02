# Reader Architecture

English | [中文](reader.zh.md)

Date: 2026-09-17 (typography section updated in T09 on 2026-09-18)
Upstream: `overview.md` §3 contracts 4/5/6; ADR-0004/0005/0006.
Requirements: REQ-0002 (rendering), REQ-0003 (position/recent), REQ-0005 (turns/gestures), REQ-0006 (TOC), REQ-0007 (typography/code/tables), REQ-0008 (search).


## English Overview

This document specifies the Reader boundary for ADR-0004/0005/0006 and REQ-0002/0003/0005/0006/0007/0008. The pipeline is cancellable background text read → Markwon/CommonMark parse → project-owned top-level block split → block-based RecyclerView rendering. Ordinary blocks use TextView/Spanned; fenced code and tables have separate horizontal-scroll entries. Reflow reuses parsed blocks. TXT remains literal text, and title fallback is frontmatter title → first H1 → filename. Do not put an arbitrarily large document into one TextView or implement a full pagination engine.

Reader exposes Loading, Content, Degraded, and Failed states. Loading feedback is static; degradation explains giant-block or unsupported-syntax fallback; failures provide a recovery action. Cancelled or stale work releases streams/intermediate ASTs, and an older document load can never overwrite a newer one. For 100 KB through 10 MB samples, the goal is cancellable bounded reading without OOM/ANR/crash; giant blocks may become explained plain-text entries, never silent truncation.

`ParsedDocument` includes blocks, TOC, and a PositionMap. Search uses visible text, not Markdown source offsets; it is literal, cancellable, and protected by a generation token. `ReadingPosition` stores versioned block index/offset plus progress and optional anchor. Restore by block first when versions match, then validate and fall back to anchor/progress after content or layout changes. Same-document freshness checks must verify both metadata and real read access before reusing parsed content.

Relative links/images are classified in the Markdown module and resolved only through `DocumentSource` inside the granted tree. Unknown schemes and tree escapes are rejected; remote images remain static placeholders. Local images use sampled platform bitmap decoding with bounded memory and closed streams. Link/button/code-copy/table-horizontal-scroll gestures take precedence over tap-zone page turning.

Page turning uses visible viewport minus overlap (default 5%, clamped to 3–8%), immediate scrolling, and boundary clamping. Both STANDARD and E-Ink default to page turn when no valid choice is stored; explicit continuous-scroll choices are preserved. Position, TOC navigation, search highlighting, text reflow, and flavor policy all reuse block-level contracts. Reader must not access Room, SAF, ContentResolver, or Onyx directly.

## 1. Pipeline: Parse → Blocks → RecyclerView (T09 Spanned/Typography)

```text
DocumentSource.openStream(documentId)
    ↓ (cancellable background coroutine, IO dispatcher)
Read text (UTF-8, strip BOM; unknown encoding gets explicit fallback)
    ↓
MarkdownRenderer.parse (Markwon → commonmark-java 0.13.0 AST, background)
    ↓
Project block splitter (top-level Heading/Paragraph/List/Quote/Code/Table/Image/Divider)
    ↓
ParsedDocument(blocks + TOC + positionMap), each block has exact source slice + fence language
    ↓ (main thread binds only; reflow reuses blocks)
RecyclerView / Compose block rendering:
    ordinary block → TextView / Markwon Spanned (`renderSpanned`; JVM fallback plain text)
    FencedCodeBlock → horizontal-scroll item + language label + copy button
    TableBlock → horizontal-scroll item (NORMAL; WRAP/CARD are reserved)
```

Typography (`ReaderStyle` in `core/markdown/.../MarkdownContracts.kt`, T09/REQ-0007 FR-1–4/7/10): 5 font steps 0–4 (default 2, scale `0.85 + 0.15 × step`); fixed system family allowlist `system/sans-serif/serif` (OEM family queries vary; unverified); monospace for code only; 3 line-spacing steps 0–2 with multipliers 1.00/1.25/1.50; 3 margins 8/16/24 dp; soft-wrap. Persist through DataStore `SettingsStore` keys `font_size_step/font_family/line_spacing_step/page_margin_step/code_soft_wrap`. Reader receives style parameters and does not read storage. Unset soft-wrap defaults ON for E-Ink; Standard uses stored value.

Display-dependent layout (REQ-0007 FR-13–FR-15, 2026-10-02): the steps above keep their meaning, but what a step resolves to comes from `DisplayProfile.readingLayout` via `ReadingLayoutResolver` (`core/reader/.../ReadingLayout.kt`), the single path shared by the Reader and the Settings preview; see `eink.md` §6 for the table. Standard keeps 16 sp / 8–16–24 dp / additive line spacing and ragged-right text; E-Ink uses 18 sp base, exact 1.45–1.80 line height, 16–28–44 dp margins, a 36 em measure for flowing text, and inter-word justification with hyphenation for Markdown paragraphs. The Markwon `TextView` is a `ReaderTextView` that remembers the platform's own wrapping and link-colour defaults so a mode switch restores them instead of assuming them. Literal TXT / fallback blocks (Compose `Text`) are never justified: they are shown verbatim. Body colour comes from the theme, not from the activity theme's translucent text colour. Hyphenation follows the device locale's rules, so text in another language than the device locale may hyphenate poorly or not at all.

On reflow, preserve context: record first visible block, rebind, instant `scrollToItem` with `keepBlock`, then `jumpToBlock` saves position immediately. No smooth/animated scroll or pixel-perfect promise.

Code blocks (FR-5–7): monospace, lowercase first fence-info word as label; absent/indented code has no language label, unknown name is shown as-is, never guessed. Copy exact fence body via `codeForCopy`, static “Copied” notice. Soft-wrap switch stays available; ON wraps in place, OFF allows horizontal scroll to line end. No syntax-highlighting engine is required; default theme colors must remain grayscale-legible.

Tables (FR-8/9): NORMAL only. Whole table uses horizontal-scroll container so narrow screens can reach every column; inner drag is consumed by table, not page turn. Choose `TablePlugin` or `TableEntryPlugin`, never both. Current implementation uses TablePlugin; TableEntry is evolution path. WRAP/CARD are M2+ reservations only, with no UI switch.

Markwon Spanned path: each text entry reparses its source slice and renders Spanned on background; main thread only assigns it. Includes table/task-list; unknown syntax falls back to literal text while keeping blocks separate. Theme uses default Markwon/TextView colors; no forced syntax coloring.

- Never put the whole document into one giant TextView (ADR-0004).
- TXT shares the pipeline as literal paragraph chunks; title falls back to filename.
- Title order: supported frontmatter title → first H1 → filename without extension.

## 2. Four Reader States: Loading / Content / Degraded / Failed

`ReaderState` is the only external Reader state:

| State | Trigger | UI behavior |
| --- | --- | --- |
| Loading | Reading/parsing/splitting | Static text + percentage/fine progress; no spinner/shimmer/infinite motion on E-Ink |
| Content | Normal completion | Block list, TOC, search |
| Degraded | Giant-block plain-text fallback, unknown syntax, remote image placeholder | Explanatory banner for cause/scope; other blocks remain usable |
| Failed | Permission loss, missing file, stream/parse failure | Explicit cause + recovery action (reauthorize/refresh/back); no crash/blank hang |

Background work is cancellable. A stale result cannot replace a newly opened document (generation token / effect lifecycle). Cancellation closes streams and discards intermediate AST.

## 3. Large-file Policy (10 MB, cancellable and bounded)

- 100 KB/1/5/10 MB samples must remain cancellable and avoid OOM/ANR/crash. Full rich text at 10 MB is not promised; an explained plain-text fallback is allowed (ADR-0006).
- Split oversized paragraph/code/table into plain-text items + notice. No silent truncation, false “complete” claim, or rejection of every large file.
- Avoid obvious O(N²). Parse frontmatter in one linear pass, not repeated main-thread regex.

## 4. PositionMap Contract

```kotlin
interface PositionMap {
    fun blockCount(): Int
    fun visibleTextOf(blockIndex: Int): String
    fun locateAnchor(anchor: String): BlockLocation?
    fun locateQuery(query: String): List<MatchLocation>
}
data class BlockLocation(val blockIndex: Int, val blockOffset: Int)
data class MatchLocation(val blockIndex: Int, val startInBlock: Int, val endInBlock: Int)
```

Search uses visible text, never Markdown markers; it is cancellable and stale results are discarded. Switching documents clears old state; each keystroke does not reparse. Restore uses same-version block/offset first, then validates and falls back to anchor/progress after content/font/width change. Invalidate caches when the source changes; throttle saves and flush on exit.

Same-document freshness check (R2 DEF-005): reread index row and compare fingerprint; **unknown/null fingerprint is not evidence**. Even with equal fingerprint, `ReaderStore.probeAccess` must open and immediately close a real read-only stream. Index metadata does not change when permission is revoked/file disappears; fingerprint-only reuse would hide recovery UI. Reuse parsed content only after confirmed unchanged + reachable; otherwise reopen and show `AUTHORIZATION_LOST` / `FILE_MISSING` correctly.

## 4b. In-document Search (T11 / REQ-0008)

```text
input → ViewModel.onSearchQuery (generation++, cancel old job, 200 ms debounce)
    ↓ (background IO; reuse ParsedDocument, no reparse)
DocumentSearch.searchCapped (case-sensitive literal; empty/blank → zero; shared block scan)
    ↓ generation guard: discard stale result
SearchUiState (matches/currentIndex/jumpToken/capped) → instant scrollToItem + static highlight
```

`:core:reader` `DocumentSearch` defines literal/case-sensitive/empty/cross-block/wrap/version behavior; `SearchUiState` holds query, matches, index, searching, generation, jump token, cap. ViewModel exposes StateFlow and clears on switch/close; empty query starts no job.

R2 DEF-003 mapping uses **the exact string rendered by that entry**: Markwon-rendered text for Markdown (markers consumed); marker-stripped text for plain/fallback/table. Map the kth same-substring occurrence to the kth occurrence in rendered string; only if no location exists, bold whole block (`blockFallback`). Old source-slice mapping shifted headings such as `# Alpha`. Other matches use yellow background; current match black/white bold, all static for E-Ink. Table cell mapping (`:app` `EntryBindings.cellSearchHighlight`) intersects block-visible coordinates with the cell's source span and converts to local offsets; ambiguous ranges use block fallback.

## 4c. Relative Links and Local Images (T10 / REQ-0009)

```text
CommonMark Link/Image nodes → ResourceLinks classification (pure JVM)
    ↓ extractBlockLinks/extractBlockImages; code blocks carry no payload; classify before resolving
RenderBlock.links/images → App tap/placeholder binding → DocumentSource.resolveRelative (inside grant)
```

- `:core:markdown` `ResourceLinks` classifies empty/same-doc anchor/library doc/unsupported media/external/unknown scheme/unreachable; image empty/local/remote (including `//`)/unsupported format (SVG)/unknown scheme/unreachable. Do not pre-judge `..`; storage resolver enforces tree boundary.
- `:core:storage` `DocumentSource` resolves relative to source document directory + normalized in-tree path. Allow in-tree `..`, reject escape; never concatenate content URI; consistent encoding; unknown scheme is not executed. Remote image never enters loader (zero network).
- `FragmentMatch` in `:core:reader`: raw → percent-decode → same slug rule as TOC. Duplicate heading first-match best effort; unknown anchor returns null, static notice, position unchanged (never fake jump to first block).
- Image decoding (`:app` `LibraryImageLoader`, `:core:reader` `ImageSample`): exact-name SAF tree walk → provider-issued URI → BitmapFactory bounds → viewport-width power-of-two `inSampleSize` → 12 MiB cap (`MAX_DECODE_BYTES`, unverified pending REQ-0011) and further sample if needed. Two passes use separate `use {}` streams; cancellation follows composition lifetime and checks between passes; no shared cache. Missing/damaged/unsupported shows alt placeholder.
- E-Ink UI: gray border + alt + size, no animation. Link tap beats page-turn zone. External link hands to OS on tap; no handler gives static notice. Missing/out-of-tree/unsupported shows one static dismissible notice; no crash/blank page.

## 5. Navigation / Interaction Components

| Component | Module | Contract |
| --- | --- | --- |
| `PageTurn` | `:core:reader` | Turn distance = viewport − overlap; default 5%, clamp 3–8%, shared with `PageTurnBehavior`/`SettingsStore.pageOverlap`; visible height subtracts Insets/menu; next/prev clamp at edges |
| `TapZones` / `GestureArbitration` | `:core:reader` | Equal left/center/right zones (boundary belongs to right zone); previous/menu/next. Links/buttons/code copy/horizontal scrollers have priority; long press never turns; only unconsumed plain tap is zoned. `shouldShowHint(seen, dismissed)` is the pure first-run onboarding decision (shows only for a stored `false` that is not yet dismissed) |
| `TocNavigation` | `:core:reader` | Anchor → block occurrence-aware (kth duplicate → kth heading), same `ParsedDocument.toc`, no reparse; unknown anchor tries position ops then null; landing is static bold, no animation |
| `ReadingMode` / `keepBlock` | `:core:reader` | `CONTINUOUS_SCROLL` / `PAGE_TURN`; preserve list state and first visible block; `keepBlock` also preserves location after typography reflow |
| ReaderScreen wiring | `:app` | Restore/TOC uses instant `scrollToItem`; turns use `scroll { scrollBy(±distance) }`; no smooth/animated API. `userScrollEnabled=false` in PAGE_TURN. UX-06: the list is the only measured child of the root `Column` and all chrome — title, notices, search bar, reading menu, progress footer — is an opaque overlay layer inside the wrapping `Box`. The body carries a measured inset: headroom reserving the notices/search bar so they can never hide content, and a tail reserve that starts at a floor covering the footer plus a menu row (96 dp) and only ever grows to the measured bottom chrome, so the last line can always be scrolled clear of the footer and an open menu (CR-20260930-01/03) while closing the menu at the tail cannot shrink the scroll range and move the body (CR-20261001-03). The clamp asks the list for its real scroll boundary (`canScrollForward`/`canScrollBackward`), the turn distance subtracts the measured chrome via `PageTurn.visibleHeightPx`, and a search/anchor/TOC jump lands below the top chrome (`scrollToItem(target, -chrome)`); choosing a chapter closes the reading menu. Static `x / n · p%`; Generic EInkController on E-Ink, NoOp in Standard |
| Persistence | App SettingsStore + storage ReaderSettings | Persist mode/overlap (3–8%, default 5%) and the one-time `tapZonesHintSeen` onboarding flag in DataStore; position uses REQ-0003 (`jumpToBlock` immediate save). An absent reading mode defaults per display mode (`defaultReadingModeFor`: EINK → PAGE_TURN, STANDARD → PAGE_TURN, ebook follow-up 2026-10-01); a stored choice always wins, including across a display-mode switch |

Defaults: E-Ink PAGE_TURN; Standard PAGE_TURN (`ReadingMode.defaultForEink` in the reader, `ReaderSettings.defaultReadingModeFor` where the preference is read; a saved value always wins). Tap zones initially equal thirds; false-tap rate deferred to T12/T13. No pagination engine, zone editor, or complete gesture system.

UX-06 (2026-09-30): the reader chrome is an overlay layer, so the body's `LazyColumn` geometry never depends on `menuVisible`/`searchOpen` (no E-Ink reflow), the document title no longer spends a permanent header, the progress percentage is an anchored static footer, and the tap-zone onboarding is a one-time opaque overlay dismissed by one tap. The body keeps a measured headroom under the notices/search bar and a chrome-aware tail gap, the turn distance subtracts the measured chrome, and jumps reveal their target below the top chrome. See [ux-06-delivery.md](../plans/ux-06-delivery.md).

Position-save ordering (CR-20261001-04): `ReaderViewModel` captures an immutable `SaveSnapshot` (document, block, load generation) per request, allocates a monotonic request sequence at INVOCATION time — on the caller's thread, before any dispatch, so sequence order is never IO-start order — and keeps store writes concurrent. Every write completion then COALESCES to that document's newest completed request: after writing its snapshot it re-checks and writes the newer one, looping until it has written the newest (a compensating write is itself an ordinary store write and can be held while even newer requests complete, so a one-shot repair is not enough). The newest request is tracked per document, so a document switch cannot redirect the repair, and only the newest request publishes the save throttle and position caches. The stored position is therefore the document's newest requested address regardless of start or completion order. `flushPosition()` returns the job of its own write. Cross-document identity is additionally bound by `isCurrent` (same document and generation). See [code-review-2026-10-01-round-2.md](../plans/code-review-2026-10-01-round-2.md).

Recent rows (CR-20261001-07): `RecentViewModel` publishes the lazy library-name and file-health lookups as immutable observable metadata combined with the entry list, so a finished lookup updates the rows; the entry list publishes immediately and the lookups run in a job the next list supersedes, and cached metadata is pruned to the listed entries.


## 5a. Table and code layout (UX-07)

`TableBlockEntry` binds the existing parsed grid and positional search spans to `TableGrid`. A Compose `Layout` queries styled intrinsic widths across all rows, determines the maximum width per column and height per row, then measures each cell once with fixed dimensions. Missing cells occupy empty slots. Dividers use the theme's `onSurface` color; horizontal scrolling and block indices stay in the existing reader path. No second parser, dependency, storage format, or module contract is introduced.

Cells do not wrap, so the grid needs each cell's full single-line width — which Compose constraints cannot always represent (`Constraints.fixed` throws from 262 143 px; measured against the pinned `ui-unit` 1.7.5). `TableBlockEntry` therefore validates the block before measurement: `characters × 2 em + cell padding` is a conservative upper bound on the widest unwrapped cell line, and a block above `MAX_CONSTRAINT_DIMENSION_PX` (260 000) renders through the bounded fallback — its own source text in the normal horizontal-scroll container — instead of the grid, so no character is clipped or lost (CR-20261001-01). `TableGrid` additionally clamps both dimensions as defence in depth. Normal tables keep their measured alignment; search spans keep their positional mapping in both paths.

`ImageEntry` retains a full-width loading container to determine decode width, while only the bitmap uses `boundedImageDimensions`: `scale = min(1, viewportWidth / bitmapWidth, heightCap / bitmapHeight)` in physical pixels. The height cap is 320 dp converted through `LocalDensity`; width and height are converted back to dp for layout. Pixel rounding may differ by one pixel. The loader, cancellation, and remote placeholders keep their existing behavior.

Code-copy feedback reuses the action button, so no feedback row is inserted. A composition-scoped `LaunchedEffect` keyed by the block and copy counter clears feedback after two seconds; another tap restarts it and disposal cancels it. See [ux-07-delivery.md](../plans/ux-07-delivery.md).

## 6. Reader Module Prohibitions

- No direct Room, SAF, ContentResolver, or Onyx SDK access (overview §5).
- No custom full Markdown parser/AST; block layer may reuse parser AST only.
- No true pagination engine, configurable tap-zone editor, or full gesture system (T08 stop condition).

## 7. Reading-state and navigation ownership (round-3 fix)

The version-1 position shape is unchanged. The app carries block index and displayed-text character offset through load, current state, immutable save snapshots, and persistence. `readingLocationFor` returns the current logical address even inside the save interval. `ReaderContent` waits for text/item measurement, projects the address into immediate scrolling, and ignores the initial restore echo. Compose Text and native TextView line starts supply the coordinates; `BlockPositionGeometry` accounts for labels/padding. Composite entries without one text layout use a bounded length/height approximation. Pixels are transient; no global pixel position is persisted.

Save bookkeeping counts pending jobs before dispatch and releases ownership at completion, including cancellation before start. Only idle records are evicted; the eight-entry idle bound cannot discard active repair targets.

Relative-link jobs carry document/load/navigation ownership, are cancelled when superseded or when their route exits, and revalidate before publishing navigation/notices. Handed-off fragments record their destination and survive source disposal before destination loading; unrelated readers cannot consume them. Fragment landing and saved-offset restoration share one initial-scroll effect so they cannot race.

See [round-3 repair evidence](../plans/code-review-2026-10-01-round-3.md). Native/Compose long-text device tests compile but remain NOT_RUN; exact visual restoration of composite/media entries and E-Ink hardware still need device evidence.

## Discoverable controls follow-up (2026-10-01)

REQ-0014 adds a static footer navigation surface and a wrapping action menu. Both delegate to the existing ReaderContent list/menu/turn callbacks. BoxWithConstraints supplies a menu height cap of 40% of the fixed list viewport; the independently scrollable menu cannot resize the list. Measured overlay occlusion and the non-shrinking tail reserve continue to govern page distance and final-line reachability. Help reuses the existing tutorial with session state, while body input is guarded during tutorial display and initial logical-position restoration. PageGesture permanently cancels when the pointer exits the Reader, including exit/re-entry sequences. Storage, parsing, logical-position contracts and centralized motion policy are unchanged. See [reader-controls-product-wave2.md](../plans/reader-controls-product-wave2.md); Android execution is pending.
