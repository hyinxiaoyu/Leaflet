# E-Ink Readers and Folder-based Reading Research (T00)

English | [中文](eink-readers.zh.md)

- Research date: 2026-09-17. Version dates below are from public repository information; mark anything uncertain as unverified.
- Read-only research. No reference code copied. Record URLs/dates/tags/commits/licenses, useful ideas, and rejection reasons.
- Related plan: T05 scan/browse → T06 basic Reader → T07 position/recent → T08 TOC/page turns → T09 typography/code/tables.
- Constraints: AGENTS.md folder-first/read-only SAF, viewport turn with 5% overlap, static loading, no vendor SDK, generic/NoOp EInkController.

## 1. `pilcrowmd/pilcrow` — Native Markwon Reader (closest product reference)

URL: https://github.com/pilcrowmd/pilcrow. Checked 2026-09-17. Version `v1.0.7` (CHANGELOG says 2026-09-08; release feed agrees; main HEAD SHA unverified). License: GPL-3.0-or-later; root LICENSE contains GPLv3. App assets also include third-party Apache/MIT/LGPL/OFL texts; `LICENSES.md` tracks compatibility. Strong copyleft conflicts with Leaflet's undecided license: **do not copy code**, use design ideas only.

Stack from README/architecture/source: Kotlin 2.3, Compose/Material3 shell, Clean Architecture/MVVM/UDF, manual AppContainer, Coroutines/Flow, native Markwon (no WebView), Prism4j, JLatexMath, Sora editor, DataStore, SAF. minSdk 26; README says JDK 21/API 36.

Architecture and behavior:

- Single Activity Compose shell and `MarkdownViewModel` StateFlow; pure-Kotlin domain (`ParseMarkdownHeadingsUseCase`, `SearchMarkdownUseCase`); frontmatter preserves source offsets. Data layer separates SAF `FileRepository`, DataStore `StorageManager`, `MarkwonRenderer`, and PDF export. UI is passive; colors use design tokens.
- SAF uses `takePersistableUriPermission` to reopen last file. Editor save uses WAL/fsync and pending-save recovery; editor-specific, irrelevant to Leaflet's read-only behavior.
- Position/recent: DataStore stores `last_file_uri`, MRU `recent_files` (cap 8), pixel `scroll_positions`, settings/folder/reopen-last. `distinctUntilChanged` avoids unrelated reloads.
- Search uses block offsets and `SearchHighlighter`; count uses rendered text with math correction.
- TOC shares heading extraction with rendered blocks. Drawer taps navigate. Footnotes use `FootnotePlugin` + bottom spacer in `ReaderScroll.kt` (ItemDecoration headroom of one viewport, not padding) and 1200 ms landing highlight.
- Large files: ~5,000 mixed blocks pass Markwon test. v1.0.4 fixed a 1.3 MB stall caused by main-thread end-of-line regex, replacing it with a linear pass. JLatexMath is asynchronous on `ForkJoinPool.commonPool()` with source fallback; block binding catches failure and shows a placeholder. No library-wide FTS/background monitoring.
- Markwon usage: lazy `MarkwonRenderer.markwon`; test builder uses same chain without prewarm instance because InlineProcessor is stateful. Order: Core → Frontmatter → Footnote → table/strike/task → Linkify → inline parser → CeMacro/JLatexMath → Prism → limited HTML (`br/sub/sup/details`). Pipeline parses → `MarkwonAdapter` → RecyclerView entries for prose/fence/table/LaTeX/frontmatter/plain text. Code/table horizontal-scroll; copy button is a fenced-entry overlay.
- Scroll geometry: `scrollToPositionWithOffset(target,0)` is clamped at list end and cannot reach final target (reproduced by footnotes). Use decoration spacer to extend range plus two-step `scrollToDocumentEnd`. Overflow must be measured as `last.bottom - first.top`, not range/coordinates.

Useful ideas for T06–T08/T11: native block RecyclerView, preserve frontmatter offsets, fallback on math/unknown-syntax errors, visible-text block search, TOC from render blocks, block `ScrollAnchor(index,offset)`, stable restore across font scale, DataStore singleton with corrupt-data fallback. Giant block needs bounded split/plain fallback; Pilcrow's background/cancellable parse and placeholder direction is useful, but T03/T13 must set thresholds.

Do not copy editor stack (Sora/TextMate/WAL/PDF/cloud Mermaid), full math stack (JLatexMath/single-dollar handler/CeMacro/font prewarm), recents cap/serialized string, or Compose page shell. Leaflet is read-only, no PDF/cloud/editor, no guaranteed LaTeX/Mermaid, Room/DataStore split, and View/RecyclerView Reader.

## 2. `zero-made/mdreader-android` — SAF Tree + WebView Counterexample

URL: https://github.com/zero-made/mdreader-android; checked 2026-09-17. No release tag verified; main branch snapshot, SHA unverified. MIT (`LICENSE`; bundled marked/highlight.js/KaTeX/Mermaid/github-markdown-css notices MIT/BSD-3-Clause). Permissive license, but architecture is unsuitable for E-Ink MVP; no code copied.

Source stack: one `MainActivity.java` plus `assets/viewer.html/css/js` and offline bundled JS (marked, highlight, KaTeX/fonts, Mermaid), minSdk 26 / target 34, no non-platform dependency, no network permission, R8 release and ignored keystore.

SAF/folder behaviors useful for T05/T07:

- `ACTION_OPEN_DOCUMENT` for a file; `ACTION_OPEN_DOCUMENT_TREE` once with read + persistable grant. Persist with `takePersistableUriPermission`; release on recent removal/clearFolder.
- Recursive BFS over `DocumentsContract` children using `ArrayDeque`; guard 5,000; `MAX_FOLDER_FILES=500`; recurse directories, collect case-insensitive `.md/.markdown/.txt`, then case-insensitive name sort. Failures return empty silently (Leaflet must surface explicit error). List work runs in background and updates App state; filtering is client-side.
- SharedPreferences JSON stores up to 30 MRU `{uri,name,ts}`, pixel scroll, settings, selected folder, and reopen-last. Failed recent open auto-removes dead row + toast + home refresh. `ACTION_VIEW` supports Open-with.
- JS parses marked then assigns heading IDs/TOC; find-in-page count/prev/next; throttled scroll save and reapply after images/math settle; progress, code copy, frontmatter card, and only http/https/mailto/tel to external app; other schemes blocked.
- Math lesson: protect `$` expressions in marked tokenizer as opaque tokens before parser handles `_/*`; post-processing cannot recover LaTeX already consumed as emphasis. Native path also needs parse-time protection.

WebView benefits: full GFM/table/task/highlight/KaTeX/Mermaid and CSS themes, bundled offline. Costs: full `innerHTML` page, no block recycling/cancellation, pixel-only `scrollY` drifts on rotation/font changes, WebView scrolling/compositing is hard to govern through DisplayProfile/MotionPolicy, and JS bridge must marshal threads. Errors often degrade to toast. This conflicts with Reader contracts and E-Ink policy.

Borrow only the tree-grant → recursive metadata enumeration → cap → sort → dead-row recovery workflow; remote images remain offline placeholders and external links require tap. Do not copy WebView/JS bridge, pixel position, JSON state store, swallowed errors, KaTeX/Mermaid bundle, or note/editor features.

## 3. `gsantner/markor` — Folder Workflow and Offline Use

URL: https://github.com/gsantner/markor; checked 2026-09-17. Release feed top `v2.16.1`; publish date unverified. License: Apache-2.0 code + CC0 translations/samples. No code copied.

README-described stack: Java/AndroidX, no NDK, custom EditText editor/highlighting, WebView preview, flexmark-java Markdown parser, custom Zim/todo.txt conversion, offline-first.

- Notebook is a root folder (default Documents or any internal/SD location); subfolders organize plain-text files usable by vim/grep/sync tools. QuickNote/ToDo are text files. It can work with Syncthing but does not implement cloud sync.
- Offline create/read/edit/preview. Privacy says no network except external resources referenced by user content. Existing permissions include legacy external storage, INTERNET, and shortcut install; Leaflet should not inherit them.
- Broad formats (Markdown/todo/Zim/CSV/AsciiDoc/Org), templates, HTML/PDF export, AES256 text encryption are editor/platform capabilities contrary to Leaflet's read-only scope.

Borrow: originals as single source of truth, Library as folder reference, remove database without deleting source, and use external tools to verify. Empty/unsupported folders, hierarchy preservation, manual refresh, offline default, remote placeholder, and tap-only external navigation align with T05/T10.

Do not borrow caret/line-number position, WebView preview, editor search, legacy WRITE_EXTERNAL_STORAGE, favorites/tasks/encryption/multi-format/widgets, or full file-manager UI. Leaflet uses versioned block/anchor/progress positions, SAF read-only, Room/DataStore, and limited metadata browse.

## 4. `koreader/koreader` — E-Ink Interaction Reference (not Android architecture)

URL: https://github.com/koreader/koreader; checked 2026-09-17. Release feed top `v2026.07.2`; release date unverified. AGPL-3.0; Lua/custom cross-device stack. Use interaction ideas only; do not reuse code/architecture. It supports many fixed/reflow formats including PDF/DjVu/EPUB/FB2/Mobi/DOC/TXT/HTML; Leaflet supports only MD/TXT.

Transferable interaction lessons:

- Paged vs continuous modes; viewport turns with overlap, boundary clamp, and rotation/Inset recomputation. Leaflet uses immediate viewport-minus-overlap, default 5%, no full pagination engine (T08).
- Tap-zone grids and visual guides exist; Leaflet takes only left/center/right zones. Content links/buttons/horizontal table/code gestures win; long-press never turns. No configurable zone editor/full gesture system.
- Low-distraction menu, maximum reading area, no animated transition; include menu occlusion in page-turn viewport and disable smooth/overscroll/ripple/crossfade on E-Ink.
- Typography ideas: system fonts, size/line spacing/margins, monospace code, horizontal table/code reachability. Leaflet does not bundle fonts and implements NORMAL tables only.
- Footer guidance: percentage/Page X of Y/fine progress; avoid thick bar + frequent clock because frequent refresh causes flashing/ghosting. Use static fine progress and throttling.
- TOC/bookmarks/history/goto/search/thumbnails are available; Leaflet selects TOC, current-document literal search, and anchor/progress restore only—no bookmarks/annotations/library-wide FTS.

Do not port AGPL/Lua architecture, ebook plugins/dictionaries/OPDS/RSS/FTP/SSH/calibre, full pagination, K2pdfopt/reflow, bundled fonts, configurable zones, plugins, or vendor hardware refresh. Only real device matrix (6/7/10.3-inch) can decide ghosting/refresh.

## 5. Leaflet Decisions (T05–T09)

- **Folder-first (T05):** Markor folder-as-library/original truth + mdreader one-time tree grant/recursive metadata/cap/dead-row cleanup. Leaflet: multiple Libraries, `ACTION_OPEN_DOCUMENT_TREE`, `.md/.markdown/.txt`, hierarchy, mtime/size increment, cleanup only after complete scan, cancellable/serialized refresh, static throttled progress. Initial scan metadata only; no monitor/FTS.
- **Read-only SAF (T04/T05/T10):** Pilcrow `FileRepository` boundary + mdreader persisted-grant lifecycle, tightened to `DocumentSource`: read-only stream by ID, allow in-tree `..`, reject escape, do not concatenate content URI, always close, recover permission/missing states. Clearing/removing/uninstalling never deletes source.
- **Viewport turns (T08):** KOReader overlap + Pilcrow clamp/rotation lessons. Use actual visible height, default 5% (REQ allows 3–8%), Insets/menu/bounds, instant E-Ink; arbitrate content gestures; hierarchical TOC/duplicate anchors; preserve position. No page engine/zone editor.
- **Static loading (T06/T12):** Pilcrow fallback placeholders + KOReader low-distraction progress. Loading/error/degraded states; no infinite loader/animation/overscroll/ripple/crossfade; 100 KB–10 MB cancellable bounded behavior, explained giant-block plain-text fallback; no OOM/ANR/crash/silent truncation.
- **Position/typography (T07/T09):** Pilcrow block `ScrollAnchor(index,offset)`, throttled save/exit flush/versioned fallback block→anchor→progress. Minimal KOReader controls: system fonts/size/spacing/margins, monospace code/wrap/horizontal scroll, NORMAL table scroll. Take only persistence idea from mdreader themes/zoom; no WebView themes.
- **Search/resources (T10/T11):** Pilcrow block search and mdreader tokenizer-time math protection. Search visible text through map, cancellable. Resolve local links/images inside granted tree, sample decode, placeholder missing, never load remote automatically.

| Leaflet task | Primary reference | Contrast | Decision |
| --- | --- | --- | --- |
| T05 scan/browse | Markor folder semantics | mdreader traversal/cap/stale cleanup; not KOReader file manager | Recursive metadata, hierarchy, static progress |
| T06 Reader | Pilcrow block RecyclerView/fallback | mdreader WebView rejected; no Markor flexmark reuse | Blocks, cancellation, explicit states |
| T07 position/recent | Pilcrow ScrollAnchor/DataStore | No pixel scrollY or editor caret | Versioned fallback |
| T08 TOC/page turns | KOReader overlap/zones | Pilcrow spacer/clamp lesson | 5% viewport turns |
| T09 typography/table/code | Minimal KOReader controls | Do not bring full Prism | System fonts + NORMAL table |

## 6. Unverified Items and Risks (close or keep Proposed before T01)

- No commit SHAs were collected for the three repositories (only feeds/source files; no Git fetch). Publish dates for Pilcrow v1.0.7, Markor v2.16.1, and KOReader v2026.07.2 are unverified; add tag dates/SHAs when used in ADR.
- This report does not decide Markwon/AGP/Kotlin/BOOX compatibility; see renderer and Onyx reports. It supports feasibility of native blocks + SAF tree + block anchor + viewport turns only.
- mdreader cap 500 and Pilcrow recents 8 are reference values, not Leaflet defaults; T05/T07 should set limits from T03/T13 baselines.
- No reference source copied; implementation details are paraphrased and point to paths such as `rendering/ReaderScroll.kt`, `repository/LocalFileRepository.kt`, `MainActivity.java:REQ_TREE/listFolderFiles`, `frontend/apps/reader/modules/reader*.lua`.

## Sources

- https://github.com/pilcrowmd/pilcrow (README, docs/ARCHITECTURE.md, v1.0.7 changelog, MarkwonRenderer, ReaderScroll, LocalStorageManager)
- https://github.com/zero-made/mdreader-android (README, MIT LICENSE, MainActivity, viewer.js/html, third-party list)
- https://github.com/gsantner/markor (README, Apache LICENSE, v2.16.1 feed, NEWS)
- https://github.com/koreader/koreader (README, AGPL COPYING, v2026.07.2 feed, reader/filemanager modules)
- Plan/rules: `docs/plans/M0-M1-implementation-plan.md` T05–T09 and AGENTS.md storage/flavor/E-Ink constraints
