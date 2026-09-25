# E-Ink Test Matrix

English | [中文](eink-test-matrix.zh.md)

Owner: T03 delivers the matrix shell; T13 fills results on real hardware.
Status convention per row: `PASS` / `FAIL` / `BLOCKED` / `NOT_RUN`.
Rule (REQ-0011 FR-6, `docs/archive/init.md` §49): emulator may assist size-adaptation
checks, but ghosting/refresh verdicts come ONLY from real E-Ink hardware.
Any row without a device stays `NOT_RUN` — never mark it passed.

## Device roster

| ID | Device | Android | Screen | Resolution | Firmware | Leaflet version | Test date | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PHONE-REF | Ordinary phone (generic, TBD) | TBD | TBD | TBD | N/A | TBD | TBD | NOT_RUN |
| EINK-6 | 6" class E-Ink device (TBD) | TBD | 6" | TBD | TBD | TBD | TBD | NOT_RUN |
| EINK-7 | 7" class E-Ink device (TBD) | TBD | 7" | TBD | TBD | TBD | TBD | NOT_RUN |
| EINK-103 | 10.3" class E-Ink device (TBD) | TBD | 10.3" | TBD | TBD | TBD | TBD | NOT_RUN |

Fill one roster row per physical device before recording cases against it.

## Cases (docs/archive/init.md §49 list)

| # | Case | What to check | PHONE-REF | EINK-6 | EINK-7 | EINK-103 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Page-turn ghosting | Turn 20 pages in `testdata/markdown/long.md`; note residual ghosting level | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 2 | Menu show/hide | Toggle reader menu; content reflows without leftover artifacts | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 3 | Rapid consecutive turns | 10 fast turns; no skipped/overlapping pages, no stuck state | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 4 | Long-press | Long-press body text: must NOT trigger page turn; selection/context (if any) behaves | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 5 | Back navigation | Back from reader returns to library position; no blank screen | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 6 | Font size adjust | Change font size; layout keeps reading context, no clipped lines | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 7 | TOC | Open TOC for `long.md`; jump lands on the tapped section | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 8 | Long document | Open `testdata/generated/large-many-1m.md`; scroll end-to-end without ANR | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 9 | Code block | `testdata/markdown/code.md`: wrap default on E-Ink, scroll when wrap off | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 10 | Table | `testdata/markdown/tables.md` wide table: all columns reachable via horizontal scroll | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 11 | Image | `testdata/markdown/relative-images.md`: local renders, remote shows static placeholder, missing shows placeholder | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 12 | Contrast (B/W) | Body/code/table text legible at default and high-contrast settings | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 13 | App resume | Background/foreground mid-document; position and mode (STANDARD/EINK) retained | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| 14 | Rotation | Rotate during reading; position stays reasonable, no crash, no lost content | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## Notes

- Record app version as `versionName` + `versionCode` + flavor (`full`/`eink`).
- Attach or link the matching `docs/performance/baseline-*.md` run for
  cases 1, 3, 8 (timing/memory context).
- Failures file one line each: device ID, case #, observed behavior,
  sample file, log pointer. No failure → keep `NOT_RUN` until actually run.

## Emulator-assisted evidence (2026-09-18, NOT a substitute for real hardware)

Device: emulator-5554 (sdk_gphone64_arm64, Android 15, 1080x2400).
Builds: full 0.1.0 / eink 0.1.0-eink, versionCode 1 (debug, dev-signed).
Real-hardware rows above stay `NOT_RUN` — the verdicts below cover only
layout/logic, never ghosting/refresh (REQ-0011 FR-6).

| # | Check | Evidence |
| --- | --- | --- |
| 5 | Back navigation | PASS (emulator): reader → Back → library/browse, no blank screen (R2-07 walkthrough, full + eink flavors) |
| 6 | Font size adjust | PASS (emulator): Step 3→4 persists across force-stop restart; layout keeps context (T09 walkthrough) |
| 7 | TOC | PASS (emulator): drawer lists 25 headings, Section-10 jump lands 20/56·34% (R2-07 walkthrough + page test `tocResolvesBothSections`) |
| 8 | Long document (logic) | PASS (emulator): 1MB/12149-block production open ≈8s wall, PSS 122MB settled, no OOM/ANR (R2-07 walkthrough); 10MB Loading shows at 6s, Back-cancel clean, mid-parse PSS 260MB/RSS 371MB, no ANR (R2-07); `:core:markdown` instrumented suite 5/5 |
| 9 | Code block | PASS (emulator): wrap toggle reachable in Settings; long lines reachable via horizontal scroll (T09) |
| 10 | Table | PASS (emulator): native cell grid on both flavors, wide table extends into horizontal scroll (R2-07 eink screenshot); header bold, inline markup in cells |
| 11 | Image | PASS (emulator): local 16/256 PNG render as real bitmaps; spaces + CJK segments resolve; escape/missing → static MISSING; remote https/http → “not loaded automatically” placeholders with zero loader calls (R2-07 walkthrough + page test `localImageDrawsRemoteAndBrokenPlaceholder`) |
| 12 | Contrast | NOT_RUN (needs human legibility verdict + screenshot, T09 AC-7) |
| 13 | App resume | PASS (emulator): force-stop → relaunch keeps mode (DataStore EINK verified) + position restore 26/56·45% via recents (R2-07 walkthrough) |
| 14 | Rotation | PASS (emulator): landscape keeps Section 10 at top 26/56·45%, portrait back identical, no crash/content loss (R2-07 walkthrough + page test `rotationKeepsPositionWithoutReparse`) |
| 1–4 | Ghosting/menu-artifact/rapid-turn/long-press | NOT_RUN (real E-Ink hardware only) |
| 15 | Search count (R2-07) | PASS (emulator): “1 / 1” composes after fix (missing `Text(status)` found by walkthrough); jump + current-highlight on production page; page test asserts “1 / 3” → step → “2 / 3” |
| 16 | Gone-file recovery (R2-07) | PASS (emulator): rescan drops deleted row → cold reopen shows “Can’t open this document / moved, renamed, or deleted” + Remove entry + Retry; library-screen dead row keeps gone-hint + removal (FR-6) |
| 17 | TXT literalness (R2-07) | PASS (emulator): faux heading/dash/ordered markers stay literal on eink flavor; CJK/emoji; 1/9 (page test `txtOpensAsLiteralParagraphs`) |
| 18 | Mode switch (R2-07) | PASS (emulator): full flavor STANDARD → EINK applies app-wide high-contrast theme, DataStore persists `display_mode=EINK`; eink flavor defaults EINK with zero setup |
