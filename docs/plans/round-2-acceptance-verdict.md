# Leaflet R2 Alpha Acceptance Verdict — 2026-09-18

English | [中文](round-2-acceptance-verdict.zh.md)

> **Re-summarized after the table-cell fix, 2026-09-19:** The duplicate-cell “current match” bug is fixed with positional highlighting (intersect block ranges with cell ranges and translate to local coordinates), not string guessing. Block-level coordinates now use visible block text. Five JVM and one production-device regression were added; all failed before the fix (JVM: 19 tests/5 failures; device: `expected:<1> but was:<0>`). REQ-0008 AC-3 returns to PASS. DEF-003/006/007 are CLOSED; see [table-cell fix round](round-2-open-defects.md). DEF-008 remains FIXED_PENDING_VERIFICATION. Current gate: 576 JVM variant / 270 unique; Full 30/30, E-Ink 30/30, Markdown 5/5, storage 16/16; `test lint ktlintCheck` and all four assemble tasks green on emulator-5554 (emulator, not E-Ink hardware).

> **Historical independent review note, 2026-09-19:** The earlier review passed both debug flavors/JVM/lint/format, with 29/29 page tests per flavor, but marked REQ-0008 AC-3 PARTIAL because identical table cells highlighted together. That PARTIAL is superseded by the fix above. DEF-006/007 were reopened at that time; DEF-008 remained FIXED_PENDING_VERIFICATION. Earlier evidence is retained, but a single rerun does not prove root cause or full acceptance.

> **Second-round revision, 2026-09-18:** Independent review reopened DEF-003–007 and added DEF-008 (Compose out-of-bounds crash during Activity destruction). Root causes/fixes were reviewed with production-path evidence: real Room + SAF error state (DEF-005/006), order-preserving highlight span (DEF-003), fence display/copy (DEF-004), and Compose snapshot main-thread gate (DEF-008). Device suite then had 29 cases/flavor, Markdown 5, storage 16; JVM 552 variant / 264 unique. The original DEF-008 crash did not recur; treat as intermittent with residual risk, not fully closed. Details are in `round-2-open-defects.md`.

> **Earlier revision, 2026-09-18 (DEF-007):** Some PASS claims were based on model assertions or incomplete walkthroughs rather than production paths. Affected ACs were re-reviewed. Confirmed defects became PARTIAL/NOT_RUN until production evidence was added (real TextView span/typography, actual rotation, actual TOC tap, real search input/highlight, production Markwon large-file path). Historical commands remain, but their proof scope was narrowed. Revised rows are marked “R2-DEF revision.” E-Ink ghosting/refresh has no hardware evidence and remains NOT_RUN.

R2-08 delivery: this file is the sole AC-level verdict. Each AC maps to a test/command/manual record and must be exactly PASS, PARTIAL, or NOT_RUN; uncovered ACs cannot pass. Environment: emulator-5554, `sdk_gphone64_arm64`, Android 15/API 35. At the R2-08 snapshot, all four flavor/build packages passed; JVM was 494 variant / 240 unique; app had 12 page tests per flavor, plus storage 16 and Markdown 5. Current counts are in the update above. No E-Ink device: ghosting/refresh remain NOT_RUN.

> **R3 status pointer (2026-09-28):** this file remains the R2-08 snapshot. Later software evidence is in the R3 delivery reports listed in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md): R3-05 page interaction, R3-06 links/images, R3-07 large documents/search, and R3-08 software candidate. R3-09 (real device) and R3-10 (owner release decisions) stay blocked. Current suite counts: 295 unique JVM tests, 75 app instrumented tests per flavor (3 skipped), storage 17, Markdown 5.

**Emulator boundary:** emulator-5554 proves only that code paths execute as designed; it does not prove E-Ink ghosting, refresh waveform, or real grayscale contrast.

## Legend

- PASS: supported by a test/command/record against the current source snapshot.
- PARTIAL: some subcriteria have evidence; missing items are listed.
- NOT_RUN: not executed (often hardware/tooling unavailable); this is not a pass.

## REQ-0001 — Folder-first Library

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 hierarchy, empty folders, no full-text parse | PASS for small sample; PARTIAL for exact 25-row matrix | JVM `discoveryKeepsHierarchySortsCaseInsensitivelyAndFilters`, `emptyAndUnsupportedOnlyTreesSucceedWithZeroDocs`, `extensionMatchingIsCaseInsensitive`; device nested tree/rescan (2 files + uppercase `B.MD` + empty folder); 8-document walkthrough. Exact 20+5+3+1 matrix was not rerun this round; old T05 evidence was not rerun. |
| AC-2 duplicate add | PASS | `duplicateAddStillDedupesViaRepository`, `duplicateAddReturnsExistingWithoutSecondGrant`. |
| AC-3 refresh reflects add/update/delete | PASS | JVM `rescanAddsUpdatesDeletesWithMtimeSizeRule`; device adds `c.txt`, edits `B.MD`, deletes `a.md`; long.md removal walkthrough. |
| AC-4 reliable mtime/size skip and missing-metadata reread | PASS | Same rescan test plus `unreliableMetadataIsNeverDeclaredUnchanged`. |
| AC-5 cancel ≤1 s, old index retained | PASS | `cancelCommitsNothingAndReportsCancelled`; no writes within 1 s, old rows retained, status CANCELLED. |
| AC-6 mid-walk permission error | PASS | `ScannerInstrumentedTest.realListerMidWalkGrantLossKeepsOldIndex`; JVM zero-write/no-cleanup cases. |
| AC-7 remove local rows, preserve bytes, shared grant | PASS on device | `RoomInstrumentedTest.removeLibraryCascadesLocalRowsAndLeavesSourceBytesUntouched`, real DB grant release/retention; JVM shared/unshared/unknown/failing-release cases. |
| AC-8 observable criteria | PASS | This table is the criterion. |

## REQ-0002 — Markdown / TXT Reader

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 basic syntax | PARTIAL, updated by R3-03 | Original model/block assertions did not prove source-slice semantics; DEF-004 exposed escaping/strike/quote/list changes. Fixed with `MarkdownRoundTripTest` and production TextView span test. Second round added fence/copy JVM cases and production test. R3-03 adds H1–H6 spans, inline styles, list/quote/rule, copy/table, unknown/damaged/oversized fallback; it also found/fixed DEF-009, whose task-list slice was empty. Nested-list/task-checkbox appearance remains unreviewed. |
| AC-2 unknown syntax fallback | PASS | `unknownSyntaxStaysReadable`, `malformedDegradesNeverCrashes`. |
| AC-3 title/frontmatter fallback | PASS | `titleRuleFrontmatterH1Filename`, `brokenFrontmatterNeverFailsBody`. |
| AC-4 BOM/empty/multilingual TXT | PASS, revised | JVM BOM/empty/literal/multilingual tests; production page verifies `# Not a heading` and `[not-a-link](nowhere) **literal**` remain literal and TXT creates no Markwon AndroidView (DEF-001 fixed). |
| AC-5 damaged encoding | PASS | `brokenEncodingDegradesWithNotice`, `invalidUtf8SequencesBecomeReplacement`. |
| AC-6 10 MB cancel/fallback/no truncation | PASS, revised | `LargeFileInstrumentedTest` 5/5 now uses production Markwon parser after DEF-006; 10 MB page walkthrough showed Loading at 6 s and clean Back cancellation (reported PSS 260 MB; no ANR). |
| AC-7 stale A→B result and stream closure | PASS | `fastSwitchStaleResultNeverWins`, `staleParseResultDroppedByGeneration`, `cancelReleasesStream`. |
| AC-8 measurable criteria | PASS | This table. |

## REQ-0003 — Reading Position

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 same-block restore including process kill | PASS | JVM exact block/offset restore; page tests persist/reopen block 3; force-stop walkthrough restored 26/56, 45% (`r207-restore.png`). |
| AC-2 2-second throttle + exit flush | PASS | `PositionThrottlerTest` covers continuous-scroll limit, no duplicate writes, restore echo, first observation, and interval. R3 later adds a discriminating production-page flush test. |
| AC-3 external edit fallback/notice | PARTIAL | JVM fingerprint/anchor/progress matrix passes; real-device edit→reopen fallback notice not walked through (walkthrough covered deletion, not edit). |
| AC-4 font change preserves context | PARTIAL | JVM anchor mapping passes; no real-device font-change/reopen landing walkthrough. |
| AC-5 recent order/dedup/remove/clear/cap | PASS | JVM MRU test; walkthrough covers order, relative time, remove, Clear all and confirmation. |
| AC-6 missing/revoked recent item | PASS, second-round evidence | JVM row retention; production page gone/reauthorize prompts; real Room + Reader gateway boundary tests for permission loss/missing item; screenshots `r207-gone3.png`. |
| AC-7 rotation without reparse | PASS, revised | `realRotationKeepsPositionWithoutReparse` triggers actual host Activity configuration change and asserts document identity/restore block; unchanged reopen test; bidirectional portrait/landscape walkthrough (`r207-rot.png`). |
| AC-8 measurable criteria | PASS | This table. |

Same-ViewModel reopen now rereads the index/fingerprint: unchanged source reuses ParsedDocument; changed source reloads; missing/unauthorized source shows FILE_MISSING/reauthorization instead of stale cached Content. Page regressions cover edit, delete, grant loss, and unchanged refresh.

## REQ-0004 — Display Profiles

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 flavor defaults/hot switch/restart | PASS | Both flavors install/start, E-Ink defaults EINK, Full STANDARD; Full switches immediately; DataStore `display_mode=EINK` persisted; profile JVM tests. |
| AC-2 full E-Ink off-list walkthrough | PARTIAL | Static black/white page and policy tests pass; no item-by-item checklist for smooth/overscroll/ripple/crossfade/infinite loading. |
| AC-3 no direct Onyx and centralized policy | PASS | Main source has no Onyx implementation refs; controller lifecycle/capability separation tests. |
| AC-4 no unavailable hardware controls | PASS | Source/resource scan has no `UpdateOption/REGAL/Fast Mode/GC interval` except a negative EInkController comment; About states system redraw semantics. |
| AC-5 both packages/builds/identity | PASS | Both debug builds; badging IDs/names differ. |
| AC-6 measurable criteria | PASS | This table. |

## REQ-0005 — Page Turn

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 five turns, overlap and pixel tolerance | PARTIAL | JVM formula/range/clamp tests pass (`turnDistancePx(1000)=950`); no real-device ruler measurement within 2 px. |
| AC-2 boundaries/ten turns | PARTIAL | JVM clamp passes; TOC-to-end walkthrough did not overshoot; no real-device first-left/end-right/10-turn record. |
| AC-3 gestures do not turn page | PARTIAL | JVM arbitration table and table-scroll screenshot; no real touch/link/horizontal/500-ms long-press record. |
| AC-4 rotation/menu viewport | PARTIAL | JVM visible-height/clamp; position survives rotation; no measured post-rotation distance or menu landing error. |
| AC-5 mode switch keeps block | PASS | ReadingMode/PageTurn and page tests; `keepBlock` clamp. |
| AC-6 no E-Ink smooth scroll | PASS | Production scan finds no smooth-scroll calls; `turnPage` uses instant `scroll { scrollBy }`. |
| AC-7 measurable criteria | PASS | Unmeasured portions are marked PARTIAL. |

## REQ-0006 — TOC

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 H1–H6, hierarchy, frontmatter/code exclusion | PASS | JVM heading, adjacent heading, formatted heading, and substring-heading cases; 25-row drawer walkthrough. |
| AC-2 duplicate anchors and distinct destinations | PASS | Stable `summary`, `summary-1`, `summary-2`; page test taps actual drawer and persists landing. |
| AC-3 visible landing ≤4 px, instant, persisted | PARTIAL | Section 10 walkthrough at 20/56, 34%, with instant `jumpToBlock`; no pixel ruler measurement. |
| AC-4 no-heading/TXT placeholder | PASS | JVM empty TOC; sample.txt walkthrough; literal TXT page test. |
| AC-5 1,000+ headings | NOT_RUN | No 1,000+ sample run; mapping correctness unit test is not performance evidence. |
| AC-6 measurable criteria | PASS | This table. |

## REQ-0007 — Typography, Tables, and Code

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 four settings survive process kill | PARTIAL | JVM defaults/range/coercion tests; no device change-four-settings → kill → restore. Page tests use in-memory DataStore. |
| AC-2 layout changes affect existing views | PARTIAL, revised | DEF-002 showed size/family/spacing applied only to new views. Fixed page tests assert live `TextView.textSize`, `lineSpacingExtra`, uniform body size, and SERIF typeface. All step combinations not walked; remain PARTIAL. |
| AC-3 language label and exact copy | PASS | JVM label and fence-interior copy tests. |
| AC-4 E-Ink wrap default and line-end reachability | PARTIAL | `effectiveCodeSoftWrap` + settings screenshot; no device long-line horizontal reachability check. |
| AC-5 final table column and gesture | PARTIAL | JVM reachability and horizontal-scroll predicate; E-Ink wide-table screenshot; no device last-column/content and arbitration record. |
| AC-6 no WRAP/CARD controls | PASS | Source/resource/UI scan finds only enum/comments, no switch. |
| AC-7 human grayscale contrast/screenshots | NOT_RUN | Black/white screenshot exists but no human contrast verdict. |

## REQ-0008 — In-document Search

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 five Chinese matches and traversal | PASS, revised | JVM `chineseFiveMatchesAcrossBlocks`; production input/debounce page test shows 1/3 → 2/3. |
| AC-2 syntax excluded, visible text included | PASS | JVM `markersNeverMatchVisibleWordsDo`, `anchorsAndSearchAreVisibleText`. |
| AC-3 visible code/table matches and correct highlight | PASS, after table-cell fix | JVM searchable code/table and cell mapping; device asserts actual Background/Foreground spans equal `page-search-needle` and current span moves correctly. Highlight is order-preserving; five tests were red before/fixed. Table-cell mapping now intersects positional ranges then translates per cell; five JVM cases + device assertion were red before/fixed. |
| AC-4 rapid typing cancels old query | PARTIAL | JVM stale-generation/cancel cases; no 200 ms/five-character device profile. |
| AC-5 document switch clears state | PARTIAL | JVM generation guard; no A→B page reset run. |
| AC-6 no-match state | PASS | JVM empty/blank/no-crash tests and Section 10 walkthrough (`r207-search.png`). |
| AC-7 emoji/Japanese/repeats/no cross-block | PASS | JVM multilingual/repeat/case/no-cross-block matrix. |
| AC-8 10 MB/fallback search | PARTIAL | JVM 5,000 cap, sparse completeness, fallback search; no 10 MB device search. |

## REQ-0009 — Local Resources and Links

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 in-tree relative paths incl. spaces/Chinese | PASS | JVM path/fixture/provider resolution; walkthrough rendered space + Chinese path images (`r207-img7.png`). |
| AC-2 reject `../../escape` | PASS | JVM escape/unknown/missing/library tests; parent-climb target correctly shown MISSING. |
| AC-3 cross-document duplicate fragment matches TOC | PARTIAL | JVM fragment normalization/duplicate cases; no real cross-document tap. |
| AC-4 same-document present/missing anchors | PARTIAL | JVM null/normalization tests; no device tap. |
| AC-5 local large image visible and bounded | PARTIAL | Page Ready draw, JVM sample/decode limits, 16/256 PNG walkthrough; no peak-memory measurement on tens-of-MB image; 12 MiB cap unverified. |
| AC-6 missing/damaged/remote placeholders, zero network | PASS | Page asserts zero `http` loader calls and gone placeholder; JVM kind tests; remote/missing walkthrough (`r207-img5.png`). |
| AC-7 HTTPS Intent / unknown scheme | PARTIAL | JVM scheme classification; no real Intent stub run. `onOpenDocument` is wired but lacks stub assertion. |
| AC-8 no external app preserves state | PARTIAL | notice path exists; no device no-handler run. |

## REQ-0010 — Settings / About

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 flavor identity/defaults | PASS | Both flavor installs/badging and mode defaults; capability test. |
| AC-2 immediate switch/persist | PASS | Full→EINK visual change and DataStore persistence; E-Ink page test. |
| AC-3 E-Ink off-list | PARTIAL | See REQ-0004 AC-2; Settings screenshot shows typography/mode controls. |
| AC-4 no hardware-level controls | PASS | Main source/resources scan zero hits. |
| AC-5 license TBD/no false project license | PASS | TBD copy present; no MIT/Apache project claim in main source/resources. |
| AC-6 persisted typography and mode/position | PARTIAL | JVM semantics; no joint device change/restart/position-preservation flow. |
| AC-7 capability matrix in page | PARTIAL | JVM flavor/profile separation tests; no full page-level matrix. |

## REQ-0011 — Performance and Release Verification

| AC | Verdict | Evidence / remaining gap |
| --- | --- | --- |
| AC-1 sample tiers + giant fallback | PASS | Generator verifies 13 deterministic files / 51 MB; JVM giant-code/paragraph bounds; device giant paragraph/code fallback 5/5. |
| AC-2 open four sizes; no OOM/ANR/crash | PASS | Device 5/5; 1 MB production open ~8 s/PSS 122 MB; 10 MB Loading 6 s and cancellable/PSS 260 MB, no ANR. |
| AC-3 three-run repeatable baseline | PARTIAL | Baseline exists; cold start 3–5 runs; Library open/turn/scroll/scan remain NOT_RUN. No invented numbers. |
| AC-4 APK size/composition | PASS | R2-08 APK report: Full 22,566,877 B, E-Ink 22,566,617 B; dex 20.86 MB/96.9%, arsc ~441 KB, .so ~73 KB across 4 ABIs, res ~42 KB, profile ~8 KB; androidx-only notices, no tests, zero user permissions. |
| AC-5 device matrix | PARTIAL | Fields/list exist; emulator evidence for several rows; all four real-device columns NOT_RUN. |
| AC-6 release manifest permission basis | PASS | Both APKs zero user permissions; only AndroidX signature-level receiver permission. |
| AC-7 original-file invariance | PASS | Device Room/SAF checksum probes; 30 `testdata/markdown` files matched before/after walkthrough (`INVARIANT-CLEAN`). |
| AC-8 release builds and layered gates | PASS, re-summarized | Fresh two-release build, test/lint/ktlint green; 576 JVM variant/270 unique, device Full 30 + E-Ink 30 + storage 16 + Markdown 5. Older 240-unique and 12+12+16+5 values remain historical in log below. |

## Remaining Release Blockers

1. Real E-Ink ghosting/refresh/contrast matrix (rows 1–4/12): only hardware can assess ghosting.
2. Owner decisions: project license, formal applicationId, production signing, distribution. No LICENSE/signing config/key was added this round.
3. NOT_RUN: ruler-measured page-turn distance, 1,000+ heading run, very large image peak memory, 10 MB search feel, TalkBack.
4. ~~Same-VM reopen after file deletion showing cached content~~ fixed by DEF-005: same-document reopen checks index fingerprint + real reachability; delete/permission loss/edit enter correct states.

## Accepted Candidate Conditions

Production-page paths are demonstrated by the 30 Full + 30 E-Ink tests for covered flows: MD/TXT, reflow of existing views, real rotation, TOC tap, real search input/highlight, reopen after delete/permission loss/edit, tap zones, and image placeholder/local draw. Uncovered flows remain individually PARTIAL/NOT_RUN; do not claim “everything” passed.

Regression assertions inspect actual view tree/TextView content, span types/ranges, `textSize`, `lineSpacingExtra`, and `typeface`, not only model state. Data safety has read-only originals, local-only Library removal, shared grant handling, and hash evidence. CI has four jobs matching local commands; both releases are clearly unsigned candidates.

## Second-Round Revision Appendix

Revision date 2026-09-18. Emulator-5554 / Android 15 API 35. App Full/E-Ink 29/29 each, Markdown 5/5, storage 16/16; JVM 552 variant / 264 unique; test/lint/ktlint and four assemble tasks passed.

| Defect | Affected AC | Second-round action/evidence |
| --- | --- | --- |
| DEF-009 task-list empty row | REQ-0002 AC-1 / REQ-0007 | Do not filter task item as `ListItem` (Markwon item is `CustomBlock`); recognize by class and add `[ ]`/`[x]`. Two JVM + one device regression failed before fix. |
| DEF-008 Compose bounds crash | REQ-0011 AC-2/8 | Suspected ViewModel IO-thread StateFlow write caused snapshot apply on writer thread. Added `ReaderThreadingTest` (3 off-main applies before, 0 after); two combined device runs green. Original crash did not recur; remains FIXED_PENDING_VERIFICATION with residual risk. |
| DEF-004 fence/copy | REQ-0002 AC-1 / REQ-0007 AC-3 | Treat body as data (`codeBody`) and adapt fence length; four JVM + one page test, failing before fix. |
| DEF-003 repeated-match mapping | REQ-0008 AC-1/3 | Order-preserving mapping; five JVM cases failed before fix + page span assertions. |
| DEF-005 real access permission | REQ-0001 AC-3/6, REQ-0003 AC-7 | Add `probeAccess`; unknown fingerprint is not fresh; two real Room/ReaderBoundary cases failed before fix. |
| DEF-006 test doubles/boundaries | REQ-0002/3/7/8/11 | Complete four fake-boundary modes + real storage boundary suite + threading gate; page tests 21→29. |
| DEF-007 evidence credibility | REQ-0011 AC-8 | This re-review records proof scope; failures/probes/pre-fix reds remain in defect log. |

## DEF-007 Evidence-Scope Revision Appendix

Revision date 2026-09-18, after independent R2 defect review. Environment: emulator-5554 Android 15/API 35; AGP 8.13.2 / Gradle 8.13 / Kotlin 2.0.21.

| Defect | Affected AC | Revision |
| --- | --- | --- |
| DEF-001 TXT/fallback re-parsed as Markdown | REQ-0002 AC-4 | Production page now asserts literal text and no Markwon view; former model-only PASS narrowed. |
| DEF-002 existing TextView not restyled | REQ-0007 AC-2 | Live TextView `textSize`/`lineSpacingExtra`/`typeface` assertions added; all levels still not walked, so PARTIAL. |
| DEF-003 highlight offsets wrong | REQ-0008 AC-1/3 | Real span range assertions; JVM locks order-preserving block mapping, whole-block fallback when unlocatable, and positional table-cell intersection. |
| DEF-004 AST round-trip changes semantics | REQ-0002 AC-1 | Fixed escaping/strike/quote/list/visible-text handling; round-trip tests added; page tests only title/bold, so PARTIAL. |
| DEF-005 same-document reopen skips refresh/access | REQ-0003 AC-6/7 | Four page tests for external edit/delete/permission loss/unchanged reopen; old “known trade-off” note is obsolete. |
| DEF-006 weak page assertions | REQ-0002/3/7/8/11 | App page suite 12→21 per flavor, adding real view tree/rotation/TOC tap/search input+span; large-file test now uses production Markwon. |

Still not closed after revision: E-Ink ghosting/refresh/grayscale hardware; 1,000+ TOC performance; measured page-turn pixels; very large-image peak; 10 MB device search; cross-process typography persistence; TalkBack.

<!-- R2-08 verification log (2026-09-18): test/lint/ktlint green; connected suites app 12/12 each flavor, storage 16/16, Markdown 5/5; JVM 494 variant/240 unique; both release builds succeeded and are unsigned (`apksigner verify` does not verify); APK hashes/sizes in apk-size.md; source scans found no Onyx implementation/smooth-scroll/WRAP/CARD controls; 30 sample files hash-equal before/after walkthrough. -->
