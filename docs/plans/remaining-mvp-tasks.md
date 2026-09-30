# Remaining Tasks for the Initial MVP

English | [中文](remaining-mvp-tasks.zh.md)

Date: 2026-09-19
Status: Plan; not an independent acceptance result for this round.
Basis: `docs/archive/init.md` §§10, 15, 23–26, 48–58; latest revision of `round-2-acceptance-verdict.md`; `round-2-open-defects.md`.

## Counting Rules

- §26 of the archived prompt lists 25 MVP capabilities. Current records show implementation entry points for all; there is insufficient evidence that an entire major MVP feature has never started.
- “Implemented” does not mean “accepted”: syntax, persistence, gestures, relative resources, performance, and real-device behavior still include PARTIAL/NOT_RUN.
- The executor's latest record says DEF-003/006/007 are closed and 30 tests pass per flavor. This planning review checked documents only and did not rerun verification; independent review is R3-01. DEF-008 remains FIXED_PENDING_VERIFICATION.
- The remaining work is **10 independently deliverable packages: 8 software closeout/verification + 1 device acceptance + 1 release preparation**. This is task decomposition, not ten new features or a duration estimate. Do not report a completion percentage: REQ statuses still include READY/IN_PROGRESS and do not reliably represent implementation progress. Add only minimal fixes for newly found defects; do not assume all tests will pass.

## Remaining Work Packages

| ID | Work package | Initial requirement / current REQ | Deliverables and acceptance | Dependency |
| --- | --- | --- | --- | --- |
| R3-01 | Independent review of latest defects and lifecycle stability | DEF-003/006/007/008; REQ-0008/0011 | Rerun duplicate-cell counterexample; verify highlight identity on a real page; review code-fence/storage regressions; test DEF-008 across rotation/Back/destroy and record residual risk. “Not reproduced” is not proven root cause. | None |
| R3-02 | Complete Library scenario acceptance | §§11–14; REQ-0001 | Exact file-discovery/hierarchy matrix; multiple Libraries, duplicate add, empty folder, add/delete/update/refresh, partial failure/cancel; prove originals unchanged; verify 500-document/5,000-entry caps and explicit failure behavior with measurement basis. | None; serialize device work and other packages |
| R3-03 | Full Markdown/TXT syntax display acceptance | §§10, 14, 24; REQ-0002/0007 | Production-page checks for H1–H6, emphasis, strike, lists/tasks, quote, inline/fenced code, rule, table; TXT/fallback literal text; unknown syntax/damaged encoding preserves body; code copy correct. | R3-01 |
| R3-04 | Position, settings, and mode persistence | §§15, 17, 26; REQ-0003/0004/0007/0010 | Real Room/DataStore: change typography → kill process → restore; external edit → refresh → position fallback/notice; recent item/permission recovery; both flavor defaults, hot mode switch, capability gating. In-memory DataStore is not persistence evidence. | R3-01 |
| R3-05 | Page turns, TOC landing, gesture arbitration | §§16–17, 23–24; REQ-0005/0006/0007 | Five consecutive overlap turns; boundaries/ten turns; menu/rotation viewport; actual TOC tap landing; reach final wide-table column/long-code line; horizontal/link/copy/long-press must not misfire; verify E-Ink motion-off list. | R3-03/04 |
| R3-06 | Links, images, and system boundaries | §§15, 58; REQ-0009 | Cross-document/same-document fragments; Chinese/space/parent paths; unknown anchor/missing target tap; external Intent and no-handler notice; image peak/sample/lifecycle; no automatic remote image network. | R3-03 |
| R3-07 | Large docs, search, and large TOC | §§25, 48, 51; REQ-0002/0006/0008/0011 | In the actual app, open 100 KB/1/5/10 MB shapes; explain first screen, cancel, search/match cap; navigate 1,000+ headings; prevent stale results on rapid input/switch. A parser-only test is not page-load evidence. | R3-03/05 |
| R3-08 | Performance, reproducible build, candidate package | §§7, 50, 54, 56–57, 63; REQ-0011 | Repeatable Library open/scan/document first screen/turn/scroll/memory baselines; clean Linux build evidence; both releases, APK composition/permissions/test-resource isolation; AC→evidence map and current README/AGENTS/REQ/CHANGELOG status. | R3-01–07 |
| R3-09 | Standard Android and E-Ink device matrix | §§20, 49; REQ-0004/0007/0011 | Ordinary phone plus target 6/7/10.3-inch categories; ghosting, contrast, menu, rapid turns, resume/rotation. Missing devices remain NOT_RUN; emulator cannot replace hardware. | Stable candidate; device supplied/arranged by user |
| R3-10 | Formal release decisions and preparation | §§1, 6, 47, 58; REQ-0010/0011 | Owner confirms license, formal applicationId, signing custody, and distribution channel; prepare LICENSE/third-party inventory, version/change notes, install/upgrade checks. Signing/upload/release requires explicit authorization. | R3-08/09 and owner decisions |

## What to Do Next

Follow-up note (2026-09-19): user confirmed this plan is being executed; do not reassign work already underway. Detailed outputs, entry conditions, and R3-01–04 handoffs for R3-05–08 are in [R3 Next Deliverables and Handoffs](r3-next-deliverables.md). Do not assume the first batch is done before its actual prerequisite deliverables arrive.

Next dispatch should cover only **R3-01–04**. Review the latest defect fixes first, then Library, baseline rendering, and persistence. Do not dispatch “reimplement table search” unless R3-01 proves the current fix still fails.

R3-05–08 are later software acceptance. Device scheduling and owner decisions for R3-09/10 may be prepared early; release still depends on acceptance.

Roles: project manager/architecture owner keeps scope, evidence, and acceptance; executor adds tests and fixes confirmed defects. If verification finds a cross-module contract problem, report impact; do not refactor along the way.

## Standard Handoff for Each Package

1. Input: applicable REQ, architecture/ADR, package scope, prerequisite deliverables, and source identity.
2. Verify current behavior first; reuse valid evidence and avoid reimplementation.
3. If a defect is found, record reproduce → failing test → minimal fix → regression. Do not close by weakening AC, deleting assertions, or merely raising timeouts.
4. Output: changed files, real commands/test counts, devices/samples, PASS/FAIL/NOT_RUN, residual risk.
5. Before independent review, mark “awaiting acceptance”; executor self-report is not final closure.
6. Stop at the package boundary. Do not claim the next task, commit, or release automatically.

This planning round ran no build and changed no implementation. R3 IDs in this document supersede the earlier coarse four-package split in chat. Do not include M2 Reader quality, M3 Full polish/formal release, M4 library FTS/scale, or M5 Onyx refresh integration among these ten MVP packages. LaTeX/Mermaid, two-column layout, and complex animation remain deferred. A reliable total for the long-term roadmap is unavailable until future directions become testable requirements.

## Later plans

[Library browse and removal](library-browse-and-remove-plan.md) (2026-09-28) adds REQ-0001 A1 and A2. LT-1, LT-2, LT-3, and LT-4 are implemented. These tasks are not part of R3-01–10.

## Delivery status (2026-09-28)

| Package | Status | Delivery report |
| --- | --- | --- |
| R3-01–04 | Delivered; `DEF-008` re-verified in the R3-01 round | [r3-01-04-execution-report.md](r3-01-04-execution-report.md), [r3-01-04-independent-review.md](r3-01-04-independent-review.md) |
| R3-05 | Software criteria met on an emulator; two real defects fixed | [r3-05-delivery.md](r3-05-delivery.md) |
| R3-06 | Software criteria met on an emulator; two real defects fixed; real-provider image pipeline and CJK/space resolution covered | [r3-06-delivery.md](r3-06-delivery.md) |
| R3-07 | All planned shapes measured on an emulator, including giant code to 10 MB and the >16 MiB read-cap notice; provider read timing open | [r3-07-delivery.md](r3-07-delivery.md) |
| R3-08 | Software candidate evidence assembled | [r3-08-delivery.md](r3-08-delivery.md) |
| R3-09 | **BLOCKED**: needs physical hardware supplied by the owner | [r3-09-10-readiness.md](r3-09-10-readiness.md) |
| R3-10 | **BLOCKED**: needs owner decisions on license, application ID, signing, and distribution | [r3-09-10-readiness.md](r3-09-10-readiness.md) |

These delivery reports are executor self-reports; none of them is an independent acceptance
result. Hardware rows stay unverified until R3-09 runs on a real device.
