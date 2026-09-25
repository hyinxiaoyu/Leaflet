# Next Round — MVP Defect Convergence

English | [中文](round-2-stabilization-plan.zh.md)

Status: READY FOR ASSIGNMENT (tasks are ready to dispatch; code is not yet fixed).
Basis: [Acceptance report, 2026-09-18](acceptance-2026-09-18.md).

## Goal and Stop Conditions

Fix F01–F08 issues blocking basic reading, data consistency, and valid acceptance, and produce an M1 Alpha candidate verifiable on real App pages. Keep the current stack and both flavors. Add no M2 features, vendor SDK, or broad refactor.

For every task, first add a focused regression that reproduces the issue, then make the smallest fix. Verify the **production path**; assertions on enums, source strings, or helper return values do not accept a UI feature.

The REQs already exist. Do not create duplicate requirements. Update the implementation state of the relevant REQ and consolidate status/evidence in R2-08; do not mark DONE early.

## Order and Dependencies

```text
R2-00 build baseline
  → R2-01 parser/read boundaries
  → R2-02 block slicing and position mapping
  → R2-03 real rendering and images
  → R2-04 storage commit consistency
  → R2-05 reading state and recovery errors
  → R2-06 search closure
  → R2-07 page-level acceptance
  → R2-08 documentation and release-candidate review
```

This is a conservative single-agent sequence; parallel work is not required. If the user assigns multiple agents, R2-04 may run independently after R2-00. R2-01/02/03/05/06 share Reader contracts and should be serial by default. Only R2-00 edits global Gradle/version-catalog settings. Coordinate any necessary test-dependency change to avoid overwrites.

## R2-00 — Reproducible Build and Valid Gates

Priority P1; no prerequisite; REQ-0011; finding F08. Scope: `gradle.properties`, Gradle config, CI, build/testing docs. Remove machine-specific JDK path; use JDK 17 through environment/local user config without upgrading versions. Confirm SDK config does not depend on host init scripts. Ensure formatting covers all source modules; fix only mechanical issues required by that gate.

Acceptance: macOS and clean Linux/JDK 17 can run both debug builds, unit tests, lint, formatting; record environment/results. If Linux is unavailable, mark unverified and do not claim CI passed. Record test count and configure app UI test runner/dependencies; zero tests are allowed initially only if docs say “no page tests yet.” Stop before AGP/Kotlin/Compose upgrades or automatic push/remote setup.

## R2-01 — Production Parse Thread, Read/Cache Bounds, Damaged Encoding

Priority P0; depends on R2-00; REQ-0002/0003/0011; F02/F07. Scope ReaderSession, TextPipeline, parser seam/tests, and only necessary ViewModel injection.

Move decode/parse CPU work to a background dispatcher; stream reads use IO, and cancellation closes/stops reading. Design bounded read/fallback before loading data; do not `readBytes()` then truncate as a fake memory bound. Use standard decoding behavior for damaged UTF-8. Define cache capacity/eviction; unknown fingerprint cannot hit cache.

Verify dispatcher through production ReaderSession/scope. 100 KB/1/5/10 MB many-block and giant-block samples do not block UI; use the REQ's 1-second cancellation target; cancelling a slow stream frees resources. Files above limit still have explained readable split/fallback; never silently drop the tail. Regressions include invalid UTF-8 (`F4 90 80 80`, overlong, surrogate, truncation), reopening with null fingerprint, cache cap across documents, and late old parse.

Stop before a full pagination engine or generic streaming framework. If the synchronous parser cannot meet cancellation, provide an evidence-based local fallback; do not close by inflating test timeout.

## R2-02 — Source Completeness and One Position Map

Priority P0; depends on R2-01; REQ-0002/0006/0008/0009; F03. Scope MarkdownRenderer/BlockSlicer, DocumentParser, position model, TocNavigation, tests.

Remove visible-text `contains` guessing as the primary source-slice method. Use parser output/mapping that preserves all content and structure; split giant blocks completely. Build one anchor → block map shared by TOC, links, and reading-position restore; do not layer workarounds.

Regression inputs: adjacent headings without blank lines, repeated/sub-string headings, `# A **B**`, headings inside quotes/lists, backtick/tilde fences, a single code block over 4,000 lines with blank lines, oversized table, unique marker at file end. Acceptance: no missing/duplicated body, end marker visible/searchable, duplicate TOC entries land on separate correct blocks, formatted heading navigates correctly. For 1,000+ headings, avoid full-document search per heading and record scaling as input doubles.

Prefer existing parser AST. If parser dependency must change, update ADR and compatibility/size evidence first. Stop with one production mapping; do not add a parallel location system.

## R2-03 — Connect Real Markdown Rendering and Local Images

Priority P0; depends on R2-02; REQ-0002/0007/0009; F01/F04. Scope ReaderScreen, ReaderHostView/rendering adapter, image lifecycle, necessary page tests.

Either wire a native rich-text view into existing LazyColumn or connect existing ReaderHostView; choose one minimal path that preserves position/page-turn contracts. Do not create a third Reader. Tables must draw cells/columns; the local-image Ready branch must draw the bitmap.

From the real App route, verify visual H1–H6, emphasis, strike, task list, quote, inline code, narrow-screen horizontal table, exact code copy, visible PNG/JPEG, and zero automatic remote-image network calls. Link/copy/horizontal-scroll gestures beat page turns. Typography/width changes preserve position; image completion, rotation, document switch, and item reuse show correct images without recycled bitmap use. Device tests use production construction/real views and include screenshots plus assertions; `createForJvmTests()` strings are not rich-text proof.

Stop before syntax highlighting, font packages, TABLE CARD/WRAP, or network images.

## R2-04 — Atomic Scan Commit and Position Preservation

Priority P1; depends on R2-00; REQ-0001/0003; F05. Scope LibraryScanner, Room DAO/database boundary, storage tests.

Separate scan and commit. Commit a complete scan in a real transaction; cancellation/failure rolls back to the old index. Updating document metadata must not replace the parent row and delete position. Audit refresh/delete/Library-removal concurrency.

Use real Room to reproduce: refresh a modified document after saving position; cancel/throw at item N; fail during deletion; interleave refresh and Library removal. After error/cancel, compare prior Document/ReadingPosition/lastScannedAt completely. Successful refresh keeps fallback position; only true deletion clears it. Original checksum is unchanged. Measure current 500-document/5,000-entry hard caps; do not hide O(N²) dedup behind a cap or build a scale platform. Prefer no schema change; if required, include Migration and upgrade test.

## R2-05 — Historical Position Fingerprint and Recoverable Errors

Priority P1; depends on R2-01/02/04; REQ-0001/0003; F06. Scope ReaderWiring/ViewModel, Repository/position-read interface, permission recovery.

Read the fingerprint actually saved with the position, separate from the current document fingerprint. Define cache invalidation after refresh/reopen. Map storage exceptions at the existing boundary to ReaderFailure; revoked permission offers reauthorization, missing file offers remove/retry.

Acceptance: save → external edit → manual refresh → reopen falls back through anchor/progress; null fingerprint cannot reuse stale content; restart/rotation/exit preserve a valid position. After real permission revocation, reselecting the same tree retains Library identity/history and resumes. Test production wiring with actual storage exception types, not only a FakeGateway throwing SecurityException; recent-item and Reader states agree. Stop before cross-Library permissions, cloud providers, or full-library content hashes.

## R2-06 — Cancellable Search Matching Real Rendered Content

Priority P1; depends on R2-02/03/05; REQ-0008; F07/F01. Scope DocumentSearch, BlockPositionMap, SearchHighlight, Reader ViewModel/Screen binding.

Make loops cooperatively cancellable and avoid materializing unbounded dense matches. If capped/batched, define count/completeness semantics; never underreport silently. Highlight coordinates must match actual rich text.

Acceptance: 10 MB repeated-character search, rapid query changes, and document switch cancel cleanly with no stale highlights. Syntax markers do not match; visible table/code do. Chinese/Japanese/English/emoji, duplicates, no-match all navigate correctly. No FTS/regex/library-wide search and no full reparse per input.

## R2-07 — Real-Page Regression and E-Ink Acceptance

Priority P1; depends on R2-03/05/06; all MVP REQs. Scope app androidTest, test config, test/performance records; verification only, no incidental feature repair.

Cover both flavors through a real scenario: add test folder → browse → MD/TXT → typography/mode → TOC/search/image → exit/restore; include rotation, lost permission, missing target, and tap arbitration. Large documents go through production ReaderSession, Markwon, and page; record read/decode/parse/first-readable/cancel time and memory. Do not test only parser or report settled PSS as peak.

Both connected-app tasks must execute nonzero tests and produce reports; run actual storage/markdown module device suites and bind reports to current source snapshot. Report layer counts separately. Test ordinary Android and available BOOX devices. Missing 6/7/10.3-inch categories remain NOT_RUN; no E-Ink release acceptance claim without hardware. If a defect appears, record owning R2 task and reproduction; do not expand this verification task.

## R2-08 — Status, License, and Alpha Candidate Report

Priority P2; depends on R2-07; REQ-0010/0011; F09. Scope README/AGENTS, REQ index/status, architecture, release/testing, APK/performance records, CHANGELOG, acceptance report.

Update to actual status: remove “no project” and “wired but no production call” inaccuracies; correct unsigned Release, TBD license, and test coverage. Supersede changed Accepted ADRs with a new ADR; do not rewrite history. Map every AC to test/command/manual record; separate PASS/FAIL/NOT_RUN. No test means not passed. Performance records state real measurement path, environment, sample, and source identity. Rebuild both release candidates and verify test fixtures are absent and permissions/offline boundary unchanged.

Formal license, ID, signing, and distribution remain owner decisions; no automatic signing, commit, push, or release. Stop with clear “passed portions” and “release blockers.” Mark REQ DONE only with complete evidence and no blockers.

## Dispatch Text

```text
Execute only R2-XX from docs/plans/round-2-stabilization-plan.md.
Read AGENTS.md, the related acceptance finding, original REQ/ADR, and prerequisites.
Reproduce first, then make the smallest fix and verify the real production call chain.
Do not expand scope or hide defects by removing assertions/raising timeouts.
Report changed files, actual commands/counts/results, unverified items, and next-task prerequisites.
Stop at the task boundary; do not advance or release automatically.
```
