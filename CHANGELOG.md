# Changelog

English | [中文](CHANGELOG.zh.md)

All notable user-visible changes are recorded here. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Fixed

- Reader now has visible menu and previous/next controls, with page controls disabled at document boundaries. Reading actions wrap and scroll within a bounded menu, Help reopens the gesture instructions, and gestures leaving the Reader or touching behind a tutorial no longer turn or toggle the underlying page.

- Ebook page turns now accept left/right body swipes on release and side taps including page margins, without moving the body during a drag. Unconfigured reading mode defaults to page turns in both editions; explicit continuous-scroll choices remain available and preserved. Link/buttons and horizontal table/code gestures retain priority, and turns read current menu geometry.

- Breadcrumb taps retain the Library and folder prefix from the rendered entry. A queued tap no longer reads newer screen state and moves a different Library or a folder opened since that entry was rendered.

- Delayed reading-position writes retain ordering state across many document switches; only idle bookkeeping is evicted.
- Reading positions retain in-block character offsets. Long text restores through its current line layout, and no-movement flushes preserve saved offsets.
- Switching or leaving the Reader cancels stale relative-link navigation; handed-off fragments belong only to their destination document.

- Markdown tables now share column widths across all rows, with clear grid dividers and a stronger header rule.
- Small local images retain their decoded pixel size; large or tall images fit the viewport and 320 dp height without distorting their aspect ratio.
- Code-copy feedback now resets two seconds after the latest tap, inside the existing action button, so copying never shifts the document layout.

- A link to a section in the same document (`#section`) now scrolls to that section. It previously left the page where it was.
- Opening a link to an external site on a device with no app that can handle it now shows the recoverable "no app can open this link" notice instead of doing nothing.
- Changing the page-turn overlap setting now affects page turns immediately; previously the new overlap only took effect after a rotation or reading-mode change.
- In page-turn mode the last line of a document can now always be reached: the page stops only once the end of the text is clear of the progress footer and of an open reading menu, instead of stopping while the last lines were still covered.
- A search hit in the very first block, and a chapter chosen from the table of contents, are no longer left hidden under the search bar or the menu title. Search hits are stepped to below the search bar, and choosing a chapter closes the reading menu.
- An external link now opens in the browser (or other handler) even when the app cannot see that handler through Android's package-visibility rules; the "no app can open this link" notice appears only when the launch really has no handler.
- A reading position saved just as another document was opened could be written against the wrong block (block 0 of the document being left, or the new document's row). Each save now carries the document, block, and load generation captured when it was requested, and a delayed save can no longer update the current document's caches or save throttle.
- Closing the reading menu at the very end of a document no longer makes the text jump: the trailing scroll space no longer shrinks with the menu, so the position is kept.
- Navigation between Library, Browse, Settings, and Reader is now instant in both display modes. The app previously used the navigation library's default 700 ms fade, which E-Ink must never show.
- A Markdown table with an extremely long single-line cell no longer crashes the reader. Such a table renders as its own horizontally scrollable text, so nothing is clipped or lost.
- The newest reading position can no longer be lost when saves overlap, whatever order the writes start or finish in — including the compensating write that follows a late older save. Each save records its place when it is requested, and the last write to the store is always that document's newest requested address; a stale save no longer updates the save throttle or the cached position.
- A folder whose provider row arrives without a display name, without an id, or without a MIME type is no longer mistaken for a file (the file-scan path keeps that classification all the way from the provider row to the scanner). A scan that cannot fully classify a row keeps the previous index instead of deleting the folder's indexed documents; the scan still completes and reports the affected rows.
- Opening a Library no longer shows the wrong Library when an earlier, slower load finishes later. The current selection, its document rows, its folder path, and its scan progress are kept, and a scan refresh that finishes after switching Libraries cannot overwrite them.
- The Recently-read list now shows the resolved Library name and the "access lost"/"file missing" hint as soon as those checks finish, instead of keeping the unknown state until something else changed the list.
- Folder scanning no longer moves the file list. Progress, Cancel, Retry, and the recoverable failure/cancellation text occupy one reserved status area whose size does not depend on the scan state, so starting, cancelling, or failing a scan leaves the list where it was.
- Pressing Cancel when no folder scan is running no longer leaves the status stuck on "cancelling": cancellation now only applies to a scan that is actually in progress.
- A breadcrumb tap is now ignored when the Library or folder it was shown for is no longer the one you are in, so a stale tap can no longer jump a newly opened Library back to its root.

### Added

- The reader shows a one-time tap-zone hint on first open: an opaque overlay illustrating the previous-page, reading-menu, and next-page thirds with mode-appropriate wording. One tap anywhere, or "Got it", dismisses it, and the dismissal is remembered, so it never returns.
- Folders that hold no Markdown or TXT file now appear while browsing a Library. The scan stores each folder's relative path only; it still never reads a file body or copies an original. A folder shows the existing empty placeholder when opened.
- Browsing a Library now shows a breadcrumb with the Library root and every ancestor folder; tapping one jumps straight to that folder, and the folder you are in is shown as the current segment instead of another button.
- File and folder rows now show a folder or document icon and a readable MD / MARKDOWN / TXT tag, so the type no longer depends on a trailing slash or on colour.

### Changed

- Home now brings continuing a document, folders, reading history and navigation into one lazy scroll. First-use guidance appears only after folder loading completes; failed local-index loading has a retry action, and clearing history asks for confirmation.
- Reading settings show actual typography values and an immediate preview, with uniform selected controls. The shared shell reserves system-bar/cutout space, matches system-bar icon contrast to the display mode, and supplies a complete grayscale E-Ink palette.

- The reader body now fills the whole viewport. The document title, the notices, the search bar, the reading menu, and the reading-progress line float over the text instead of shrinking it, so opening or closing any of them no longer reflows the page (the E-Ink flash source) and the title no longer spends a permanent header. The progress line is a discreet anchored footer, and page turns still advance by the visible height minus the overlap, now measured against the chrome actually on screen. Notices and the search bar reserve their own space above the text, so they never hide it either.
- A fresh E-Ink install now starts in page-turn mode instead of continuous scrolling; Standard installs still start in continuous scrolling. An explicitly chosen reading mode is kept, including after switching display mode.
- Library browse shows one folder level at a time. Subfolders are listed before files, and each group is sorted by name. Back moves up one level.
- Library browse rows are now at least 48 dp tall and long names wrap instead of being cut off, so every file and folder is comfortable to tap. The file list is also a bounded, scrollable area now, which keeps the Back control on screen in large folders.
- Opening a different folder, or switching Library, now shows that folder's list from the top again instead of keeping the previous scroll position.
- Long-press a Library and confirm to remove its local index, indexed documents, indexed folders, reading positions, and recent entries. Original files stay on the device.
- Existing installs upgrade their local index in place to database version 2. The upgrade is additive: no Library, document, reading position, or recent entry is dropped, and folder rows arrive with the next successful scan.

- Library scans no longer stop at 500 documents. Every supported Markdown/TXT file found in the folder is indexed. A scan still stops explicitly, and keeps the previous index, if the folder tree contains more than 5,000 entries.

### Documentation

- Internationalized the project documentation with English-primary Markdown and paired Simplified Chinese translations; kept `AGENTS.md` English-only.

### Fixed — Reading-state consistency (code review, 2026-09-20)

- **Switching documents no longer writes one document's position to another.** DEF-012: if B was still loading when the app backgrounded after A, the exit flush could combine B's ID with A's position/anchor/fingerprint. Descriptors are now bound to their document; switching clears the previous snapshot; flush requires ready Content for the current document; delayed saves cannot mix descriptors.
- **Same-document freshness checks no longer jump back to the initial position.** DEF-013: a recheck previously emitted Loading, unmounted Reader, and restored its open-time position. It now keeps current Content mounted and synchronizes the latest saved position into the restore address.
- Added two `ReaderStateConsistencyTest` regressions. Both reproduced the old behavior before the fix and pass after it. Affected display/recovery/storage/recreation tests pass individually.

### Fixed — Task-list empty row and R3-01–04 acceptance (2026-09-19)

- **Task-list entries render instead of disappearing.** DEF-009: `BlockSlicer.listItems` filtered for `ListItem`, but Markwon task items extend `CustomBlock`, producing empty production rows. It now visits arbitrary list children and reads task state by class name/reflection when available; it does not guess when unavailable. Two JVM and one device regression failed before the fix.
- Added lifecycle tests for route-exit flush and Activity recreation; Library matrix tests for 21 files/folders, multi-Library isolation, 500/5,000 cap behavior, and metadata-only scan; five production syntax-page tests; and persistence tests using disk-backed DataStore + Room plus a two-phase `adb install -r` / `am force-stop` process-restart flow.
- Gates: `test lint ktlintCheck` green; Full/E-Ink page suites each 40 executed + 1 designed skip; Markdown 5/5, storage 16/16; JVM 588 variant / 276 unique. Real E-Ink, TalkBack, and manual device permission-revocation remain unverified.

### Fixed — R2 second-round defects DEF-003–008

- **Compose state updates follow main-thread discipline (DEF-008).** Screen-observed StateFlow writes move to Main; blocking/CPU work stays on IO. `ReaderThreadingTest` guards snapshot-apply threading. The original Activity-destruction crash did not recur; DEF-008 remains FIXED_PENDING_VERIFICATION with residual risk.
- **Code fences/copy preserve the full body (DEF-004).** Store body as `RenderBlock.codeBody`; choose a fence longer than any backtick run in the body, so nested literal fences no longer truncate display/copy.
- **Search highlighting preserves repeated-match identity (DEF-003).** The kth occurrence maps to the kth occurrence in rendered text; drop a match if it cannot be located instead of guessing. Current-match fallback remains whole-block bold.
- **Same-document reopen probes actual access (DEF-005).** Add `ReaderStore.probeAccess`, which opens/closes a real SAF stream. Reuse cached content only when a nonempty fingerprint matches and the file is readable; otherwise show missing/reauthorize or reload.
- Expanded fake-store modes and added a real Room/`DocumentSourceImpl` boundary suite (DEF-006); page tests grew from 21 to 29 per flavor. Re-reviewed verdict evidence (DEF-007).
- Verification: JVM 552 variant / 264 unique; Full/E-Ink pages 29/29 each; Markdown 5/5, storage 16/16; four debug/release builds green. No real E-Ink, manual SAF revocation, or TalkBack verification.

### Fixed — R2 defects DEF-001–007

- TXT and plain-text fallback no longer get parsed as Markdown: `RenderBlock.literal` / `ParsedDocument.literalText` preserve visible `#`, `**`, and link syntax.
- Typography changes now reach existing TextViews through shared `applyTypography` factory/update logic.
- Search highlights use the exact rendered string; spans cover the visible match and preserve Markwon styles.
- AST reconstruction preserves escaping, strike-through, quotes, nested lists, and actual table hierarchy; visible text is traversed from AST.
- Reopening the same document rechecks index fingerprint and real reachability; unchanged content reuses parse, changed/missing/revoked sources reload or show recovery state. Added Reader refresh action for same-metadata overwrites.
- Page tests now inspect real view tree/text/spans/layout, rotation, typography, search input, TOC taps, TXT literal rendering, and reopen after external change/delete/revocation. Large-file instrumentation uses production Markwon.
- Verification: JVM 534 variant / 255 unique; Full/E-Ink page tests 21/21 each; Markdown 5/5, storage 16/16; four flavor builds green. E-Ink hardware, cross-process typography persistence, and TalkBack remain unverified.

### Added — R2-08 status, license, and Alpha candidate report

- Updated README/AGENTS and build/test records to current M1 Alpha-candidate status and added four real screenshots. Added R2-08 APK measurements: Full 22,566,877 B, E-Ink 22,566,617 B; tests are absent from APK and user permissions are zero. Recorded `ui-test-junit4` / `ui-test-manifest` 1.7.5 as instrumented-only dependencies.
- Corrected release docs: packages have no `signingConfig` and are UNSIGNED (`apksigner verify` fails). Confirmed ADR-0007's Apache-2.0 reference is to the Onyx reference repository, not a Leaflet license declaration. Aligned matrix/requires status with evidence.
- Added `docs/plans/round-2-acceptance-verdict.md`, mapping each AC to PASS/PARTIAL/NOT_RUN and evidence; added four walkthrough screenshots and before/after hashes for 30 sample files.
- Candidate only: E-Ink hardware, owner decisions on license/formal ID/signing/channel, and measured turn/1,000-heading/large-image/10 MB search/TalkBack cases still block formal release. No product-code change, signing, commit, or release in that documentation task.

### Added — R2-07 production-page regressions and E-Ink walkthrough

- Added 12 instrumented page cases per flavor across `ReaderEntryTest`, `ReaderRecoveryTest`, `ReaderDisplayTest`, and `ReaderImageTest`. They cover Markdown/table/code/search/TOC, recovery/TXT, mode/rotation/arbitration, and local/remote/broken images. Tests compose real `ReaderScreen`; the emulator image blocks test-process host Activity launch.
- Fixed an empty search-count label by composing `Text(status)`; page regression locks “1 / 3” → next → “2 / 3”.
- Walkthrough on emulator-5554 with production package and SAF grant: add/browse 8 documents; TOC/search and position restore; rotation; delete/reopen recovery; 1 MB/12,149-block open ~8 s, PSS 122 MB; 10 MB showed Loading at 6 s and Back-cancelled cleanly, PSS 260 MB, no ANR; mode persistence; E-Ink default and wide table/TXT.
- Real E-Ink ghosting/refresh, TalkBack, and manual on-device grant revocation remain unverified. Screenshots were captured under `/tmp/r207-*.png` but not committed at that stage.

### Added — R2-06 bounded, cancellable in-document search

- Added `SearchLimits.MAX_SEARCH_MATCHES=5000` and shared `collectBlockMatches`. `BlockPositionMap.locateQuery` and `DocumentSearch.search/searchCapped` share order/semantics; the capped path checks cancellation per block and yields every 512 blocks. Dense 10 MB text returns at most 5,000 matches without OOM.
- Truncation is explicit: `capped` reaches SearchUiState and the bar says “too many results; refine query.” Count/navigation/highlight use the same returned window.
- New query/document switch cancels old work through debounce + generation guard; fallback chunks remain searchable/navigable. Table-cell highlight maps block-visible offsets into stripped cell coordinates, with whole-block bold fallback for ambiguity.
- Added JVM tests for dense cap, sparse completeness, cancellation, blank query, cell mapping, and span composition. Real 10 MB device search/E-Ink ghosting remain NOT_RUN.

### Added — T13 quality gates and E-Ink Alpha candidate

- Historical T13 gates: Full/Eink debug and unsigned release APKs built; JVM 406/406, lint with no errors, and ktlintCheck passed. Storage device tests passed 15/15 (Room/SAF/scanner and original-file SHA-256); Markdown passed 5/5 (10 MB multi-block parsing took about 599 seconds of emulator CPU, giant paragraph/code fallback, 2 MB cooperative cancellation, stale-result guards).
- Parser cooperation: `topLevelBlocks`, `BlockSlicer.slice`, and parsing yield every 512 blocks. The 10 MB readability case uses a normal `@Test`; the cancellation sample on `Dispatchers.Default` is 2 MB because `runTest`'s 60-second virtual budget cannot accommodate over 50 seconds of CPU parsing.
- Historical documentation: `docs/performance/apk-size.md` recorded 22,517,277 / 22,517,017 B (260 B difference); emulator startup/memory/10 MB baselines and the emulator-supported E-Ink matrix were recorded. Real-device rows remained NOT_RUN.
- Status was an unsigned candidate only, with license/application ID TBD and no real E-Ink verdict. T13 did not mark REQs DONE while hardware ghosting, measured thresholds, or full instrumented green gates were still missing.

### Added — T12 display policy and Settings integration

- Centralized mode/profile, MotionPolicy, controller, and flavor capabilities. E-Ink disables ripple/overscroll and navigation transitions; Standard retains platform defaults. Both flavors hot-switch and persist mode; E-Ink defaults EINK.
- Wired Reader enter/exit and page-turn lifecycle to generic/no-op controller. About states version/flavor/temp ID/TBD license/third-party license entry and system redraw semantics; Settings exposes no hardware waveform/GC/Fast Mode.
- Added six `DisplayProfileTest` cases. Real E-Ink ghosting/refresh and TalkBack remain NOT_RUN.

### Added — T11 current-document search

Literal, case-sensitive search over visible text, with count/previous/next/current static highlight; position-map navigation, 200 ms debounce/generation guard/cancellation, state clearing on close/document switch, TXT and plain fallback support, and no reparse per query. Real 10 MB/E-Ink search feel and TalkBack remain unverified.

### Added — T10 relative links and local images

Relative same/cross-document links and fragments resolve inside the authorized Library tree; tree escape/unknown scheme is rejected. External schemes hand to OS only on tap; no handler shows a notice. Local images use viewport sampling, 12 MiB initial decode cap (unverified), cancellable lifecycle, and static fallback. Remote/HTTP images are placeholders with zero loader/network calls; no network/image dependency or WebView added.

### Added — T09 typography, code, and tables

Added persistent font size/family, line spacing, margins, code language/copy/soft-wrap, and NORMAL horizontal table scrolling. E-Ink defaults soft-wrap ON and uses instant reflow. WRAP/CARD, font bundles, and syntax highlighting are deferred. Frozen initial settings: five font steps (default 2), three line-spacing steps (default 1), three margins (8/16/24 dp). Grayscale/device/TalkBack behavior remains unverified.

### Added — T08 TOC, page turns, and gesture arbitration

Wired hierarchical TOC with stable duplicate anchors and instant landing/save; CONTINUOUS/PAGE_TURN modes; left/center/right zones; boundary clamping; static progress; horizontal/link/copy gestures take priority. Default overlap 5%, clamped 3–8%. Added 20 JVM cases. Real E-Ink feel, mis-tap rate, and accessibility remain unverified.

### Added — T07 Reader session, position, and recent documents

Connected storage gateway → ReaderViewModel → real ReaderScreen with cancellable states, instant restore, throttled position tracking and exit flush, MRU cap 30, and recoverable gone/permission hints. Same-fingerprint rebuild reuses parse; changed fingerprint falls back through anchor/progress. M0 flow demo passed both flavors; process-death restore was then unverified.

### Added — T06 basic Markdown/TXT Reader

Added Markwon 4.6.2 behind `MarkdownRenderer`, cancellable block parse, title/TOC/position mapping, UTF-8/BOM/replacement fallback, 32 KiB giant-block fallback notice, 16 MiB read cap, and RecyclerView entries including code/table horizontal scroll. JVM and 10 MB instrumented cases were added.

### Added — T05 scanning and browsing

Added cancellable metadata-only SAF scanner for case-insensitive MD/Markdown/TXT, hierarchy preservation, reliable mtime/size skip, cleanup only after complete scan, static throttled progress, serialized per-Library refresh, and explicit caps (5,000 visited/500 indexed, unverified performance basis). Added Library/browser UI and provider tests.

### Added — T04 SAF and persistence foundation

Added LibraryRepository/DocumentSource SAF skeleton, persistent grants with shared-grant checks, Room entities/DAOs, DataStore typography/mode/reading settings, and source-integrity tests. Original files remain read-only.

### Added — T02 minimal project and flavors

Created Android project with app plus six core modules; `full`/`eink` IDs/names; Library empty state; Settings display-mode switch; About; minimal DisplayProfile/MotionPolicy/AppCapabilities/EInkController (NoOp + Generic); and initial storage/Markdown/Reader contract shapes.
