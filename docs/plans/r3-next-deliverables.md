# R3 Next Deliverables and Handoffs

English | [中文](r3-next-deliverables.zh.md)

Date: 2026-09-19
Role: Project management and architecture follow-up. No feature implementation, build, or agent scheduling was performed in this plan.
Scope is governed by [Remaining MVP Tasks](remaining-mvp-tasks.md). This file details handoffs; it creates no new task numbering system or feature scope.

## Evidence and Current Status

The user confirmed R3 is in progress. The independent acceptance report currently available still mainly covers R2; no per-package independent delivery reports for R3-01–04 were found. Do not infer R3 completion from R2 test counts or reassign work already underway.

| Work package | Current status | Next action |
| --- | --- | --- |
| R3-01–04 | In progress per user; per-item completion awaits handoff | Submit actual output, evidence, and unfinished criteria, then independently review |
| R3-05 | Later task; prerequisites not yet confirmed | Start after R3-03/04 satisfy the relevant contracts |
| R3-06 | Later task; prerequisites not yet confirmed | Start after R3-03 satisfies the relevant contracts |
| R3-07 | Later task; prerequisites not yet confirmed | Start after R3-03/05 satisfy the relevant contracts |
| R3-08 | Existing evidence may be organized; final acceptance awaits prerequisites | Summarize candidate after R3-01–07 handoffs |
| R3-09/10 | Device and pre-release preparation | Keep unavailable-device/owner decisions open; do not block software work without dependencies |

DEF-008 remains pending verification; failure to reproduce in one run is not proof of root cause. If software criteria are met but hardware is the only remaining item, hand that item to R3-09 and keep the original AC marked unverified. Do not block unrelated later work or claim that AC passed.

## First-Batch Handoff Requirements

Each R3-01–04 executor creates `docs/plans/r3-XX-delivery.md` (XX = 01–04) with actual delivery only; do not prefill PASS.

| Task | Required output | Rejected substitute |
| --- | --- | --- |
| R3-01 | Rerun known counterexamples, production-path assertions, lifecycle outcome, residual risks | Cite old green logs and close DEF-008 without new evidence |
| R3-02 | Exact directory/multi-Library/refresh/cancel matrix, original-file invariance, capacity rationale | Create only the Library model or test a small static list |
| R3-03 | Syntax/TXT/fallback/copy verification on actual Reader page | Assert AST node or source-string contents only |
| R3-04 | Save → process recreation → restore using actual Room/DataStore, including mode/position | Use in-memory stores or recreate only a ViewModel and call it process death |

Every report includes REQ/AC, changed files, source commit or file hash, commands/exit codes, test report path/time, device/sample, PASS/FAIL/NOT_RUN, remaining defects, and impact on downstream contracts. If no commit exists, use relevant file hashes; do not force a commit of the user's workspace.

## Batch 2A — R3-05 Reader Interaction

Entry: R3-03 rendering and R3-04 position/settings contracts are handed off; relevant software blockers are resolved.

Deliver `docs/plans/r3-05-delivery.md`, with necessary existing REQ/test updates. Verify actual visible height/overlap and each movement; five consecutive turns, boundary clamp, and ten repeated actions; recalculate after menu/rotation; verify TOC target; use real touch events for links, copy, horizontal table/code gestures, long-press, and center menu; confirm final table column and unwrapped code line are reachable; preserve context on mode switch; record every E-Ink effect in the REQ checklist and ensure Standard does not regress.

Stop when page interaction evidence is complete. Ghosting/hardware refresh belongs to R3-09. Do not implement pagination, a zone editor, or new animation.

## Batch 2B — R3-06 Relative Resources and External Apps

Entry: R3-03 handoff is complete. R3-05 and this task share ReaderScreen, so implement serially by default.

Deliver `docs/plans/r3-06-delivery.md`, resource sample list, and device results. Verify from the actual page: relative links with Chinese/space paths, `./`, in-tree `../`, cross-document fragments, duplicate headings, and missing anchors; reject out-of-tree/unknown/missing targets without losing context; exercise real HTTPS ACTION_VIEW and no-handler behavior; read/sample/draw local images through a real provider; test cancellation across document switch/rotation/destruction and oversized images with peak memory; prove remote images only show placeholders and never invoke network loaders.

Stop when local resources and system boundaries are verified. Do not add network images, cross-Library grants, or an embedded browser.

## Batch 3 — R3-07 Large Documents and Search

Entry: R3-03/05 are handed off. Diagnose bottlenecks before considering a renderer replacement.

Deliver `docs/plans/r3-07-delivery.md` and measurement reports under `docs/performance/`. Run 100 KB/1 MB/5 MB/10 MB many-small-block and giant paragraph/code samples through production read → parse → page; report read, parse, first-readable-content, and peak memory separately (settled PSS is not peak). Check cancellation, rapid switching, and background/restore for resource release and stale-result isolation; use existing REQ criteria without relaxing them. Test TOC on 1,000+ headings, rapid search input, cross-document reset, dense repeated matches, and searchable/reachable fallback. Inspect a marker at the file end; first-screen display alone does not prove completeness. Any read cap must be explicit, not hidden in comments.

Stop with a repeatable baseline and pass/fail matrix. Record failures as concrete acceptance defects; do not add library-wide FTS or M2 performance refactoring.

## Batch 4 — R3-08 Software Candidate

Entry: R3-01–07 handoffs are complete; unresolved software defects remain blockers in the candidate report.

Deliver `docs/plans/r3-08-delivery.md`, updating performance/APK/build/release records and current AC status. Bind source identity and artifact hashes. Run both flavors, unit tests, lint, formatting, and applicable device suites; distinguish newly run, incrementally reused, and historical evidence. Add reproducible baselines for Library open/scan, document open, turn/scroll. Provide clean Linux build evidence or mark it NOT_RUN. Build both release candidates and state signing, size/composition, permissions, and test-resource packaging. If formal ID/license is unresolved, keep candidate wording. Map REQ/AC to evidence and resolve README/AGENTS status drift; close only defects with sufficient evidence.

Software-candidate acceptance is separate from formal release. Without hardware/license/signing prerequisites, deliver a limited candidate but do not upload or release.

## Follow-up Cadence and Dispatch Template

Follow up when a package is handed off. Do not repeatedly run the whole suite while shared-workspace implementation is active. Do not parallelize work on the same emulator and overwrite each other's reports.

```text
Execute R3-XX from remaining-mvp-tasks.md; handoff details are in r3-next-deliverables.md.
Check prerequisite reports and source state first; use current REQs/ADRs.
Verify existing behavior, make only necessary fixes, and produce r3-XX-delivery.md.
Record actual commands, test counts, devices, failures, and unverified items. Self-test is not independent acceptance.
If a prerequisite has a local gap, explain its impact and continue independent work without bypassing dependencies.
Stop at the package boundary; do not claim the next package or release automatically.
```

This plan does not mean a task was assigned or ongoing monitoring started. Follow up only when new delivery, user notification, or an explicit monitoring task triggers it.
