# Leaflet M0 / M1 Implementation Plan

English | [中文](M0-M1-implementation-plan.zh.md)

Status: PLANNED (planning only; implementation had not started at the snapshot date)
Date: 2026-09-17
Source requirements: `docs/archive/init.md` §§1–63
Repository at snapshot: only `docs/archive/init.md`; no Android project, build configuration, or existing implementation.

## 1. Goals and Scope

M0: create a buildable project with both flavors and the basic “add folder → browse → read → restore position” flow.

M1: complete the E-Ink MVP defined in `docs/archive/init.md` and produce an acceptable Alpha candidate. Signing and public distribution are separate from development acceptance.

This plan defines tasks, boundaries, dependencies, deliverables, and acceptance only; it does not mean implementation or verification is complete. Each later agent takes one task whose prerequisites are satisfied.

Explicitly out of scope: editor, accounts, cloud sync, AI/RAG, PDF/EPUB, full annotation, plugins, library-wide FTS, vendor SDK, two-column large-screen layout, and complex animation. Do not create empty stubs for these in the name of architecture preparation.

## 2. Requirement Interpretation and Open Decisions

| Topic | Plan interpretation | Source and handling |
| --- | --- | --- |
| Standard mode in E-Ink flavor | Both flavors retain basic mode switching in M0/M1; E-Ink defaults to EINK | Explicit switch requirement in §§6/26 takes precedence over README example in §42. Record ADR and describe honestly. Locking E-Ink-only later requires a requirement change. |
| Tables, code, relative resources | Baseline usable implementations are required in M1; M2 improves them | §§10/15/23/24 require them; do not defer all support under roadmap §39. |
| When DisplayProfile appears | Establish abstraction/policy in project foundation; wire full Reader behavior later | Initial-delivery requirement §62 takes precedence; Phase 4 in §61 means complete wiring and verification. |
| Large documents | 10 MB need not have full rich-text fidelity, but must be cancellable and avoid OOM/ANR/crash; explained plain-text fallback is allowed | No silent truncation or false completion; do not replace reading with rejecting all large files. |
| Images and external links | Local images and relative document links are required; remote images are static placeholders; external links open only after user tap | No network SDK; Markdown rendering is not permission to access the network by default. |
| Fonts | Basic choices use system fonts; do not bundle font files by default | Meet baseline behavior while controlling APK size. |
| Favorites | Excluded from MVP | Permission to store favorites in §opening is not an implementation requirement in §26. |
| License, formal applicationId, signing | License is owner decision; use temporary IDs; confirm before formal release | Do not grant a license or create/submit production signing material on the owner's behalf. |

The following technical matters require evidence from prerequisite tasks; compatibility is not yet verified: JDK/Gradle/AGP/Kotlin/Compose combination, min/target SDK, Markwon maintenance/extensions, target BOOX Android versions, performance thresholds, and final license.

License and formal ID do not block local development. Implementation may continue without target hardware, but E-Ink hardware acceptance must not be marked passed.

## 3. Architecture Proposal and Ownership

Start with six Gradle modules; T01 freezes details:

| Module | Responsibility | Allowed dependencies |
| --- | --- | --- |
| `:app` | Compose shell, navigation, ViewModels, manual DI, flavor config; library/reader/settings packages | All core modules |
| `:core:model` | Library, Document, ReadingPosition, small IDs/value types | Kotlin base library; no Android UI |
| `:core:storage` | SAF, scanning, Room, DataStore, Repository | model |
| `:core:markdown` | Native parser adapter, title/TOC, render content, position map | model and audited Markdown dependency |
| `:core:reader` | View/RecyclerView reader, layout, scrolling, search navigation, interaction | model, markdown, eink |
| `:core:eink` | DisplayProfile, MotionPolicy, EInkController, generic policy | Android base APIs only; no app/storage |

Keep design-system code in `app` initially and pass style parameters to Reader. Keep database/storage in one module with packages. Do not create empty `feature:*` modules or a large DI framework; split only when separate build/reuse value exists.

Reader never accesses Room, SAF, or vendor SDK directly. App obtains streams/metadata through Repository and gives cancellable work to Reader. Markdown may reuse a parser AST; do not invent a complete Markdown AST/parser.

T01 defines base contracts and T02 creates their minimal compilable shape:

- Stable app-internal DocumentId; URI/relativePath are access/display information, not stable identity after rename.
- LibraryRepository add/list/remove-reference/refresh/scan-state; DocumentSource opens read-only streams by ID and resolves relative resources inside the Library.
- Reader input, loading/error/degraded states, and versioned ReadingPosition with block/offset/progress/optional anchor; do not rely on pixels alone.
- ParsedDocument title/TOC/renderable content/position map; navigation must not assume one file equals one TextView.
- DisplayProfile/MotionPolicy own motion/scroll/contrast/refresh policy; AppCapabilities owns availability. Keep them distinct.
- EInkController exposes enter/exit/page/scroll/refresh intents; MVP uses only useful NoOp/generic behavior, never a fake Onyx implementation.

A breaking contract change pauses dependent work; architecture owner updates ADR and task impact. Do not refactor another agent's implementation unilaterally.

## 4. Milestones and Dependencies

| Batch | Tasks | Entry | Exit |
| --- | --- | --- | --- |
| A: preparation | T00 | None | Evidence and open questions documented |
| B: requirements/contracts | T01 | T00 | REQs, ADRs, and interfaces ready for implementation |
| C: initial project | T02 | T01 | Both flavors compile; minimal Library/Settings/policy and CI |
| D: foundation | T03, T04 | T02 | Samples and read-only storage |
| E: reading flow | T05, T06 | T03/T04 | Folder discovery and basic Reader; may run in parallel |
| F: persisted flow | T07 | T05/T06 | M0 flow accepted |
| G: MVP Reader | T08 → T09 → T10 → T11 | T07 | Navigation, typography, resources, search usable |
| H: quality/delivery | T12 → T13 | T08–T11 | Mode integration and device/performance/candidate acceptance |

The initial build agent stops at T02; do not automatically implement the full MVP. T00/T01 are prerequisite research/planning, not a mandate to complete everything in one round.

Parallelize only when file ownership is clear: T03/T04, T05/T06. Reader internals should be serial to avoid concurrent changes to session, adapter, position, and gestures. The integration task owns global Gradle/version-catalog edits; do not edit them concurrently.

## 5. Task Cards

### T00 — Technical Evidence and Feasibility Audit

No dependency; read-only research, no production implementation. Inspect local Android/JDK toolchain, official compatibility tables, and PilcrowMD, Markdown Reader, Markor, KOReader, OnyxAndroidDemo, and Markwon from `init.md` §46. Deliver `docs/research/README.md`, `markdown-renderers.md`, `eink-readers.md`, and `onyx-epd.md`; record source URL/date/tag/commit/license, reusable ideas, and rejection reasons.

Compare Markwon, WebView + marked, Compose Markdown, and custom parser. Verify RecyclerView, tables, task lists, relative resources, and giant-block limits. Provide an actionable pinned-version proposal; label unknowns. Stop with preferred option and risks. Do not integrate Onyx or copy reference code.

### T01 — Requirements, Architecture, and Handoff Contracts

Depends on T00. Deliver root `AGENTS.md` draft; product vision/principles/roadmap; REQ/ADR templates and indexes; architecture overview/reader/storage/eink.

Fix REQ IDs: 0001 Library/scanning; 0002 MD/TXT render; 0003 position/recent; 0004 display/flavors; 0005 turns/gestures; 0006 TOC; 0007 typography/tables/code; 0008 in-document search; 0009 local resources/links; 0010 Settings/About; 0011 performance/release. ADR topics: folder-first, SAF, flavors/capabilities, native renderer, EInkController; also define module boundaries, position restore, and large-file fallback.

Acceptance: every MVP requirement maps to REQ/task; each REQ has §35 fields and §36 status values. ADRs without evidence remain Proposed. Stop when implementation contracts are clear and actionable REQs can be READY; build commands remain pending T02.

### T02 — Minimal Project and Both Flavors (Initial Delivery)

Depends on T01; foundation for REQ-0004/0010/0011. Add Gradle Wrapper, Kotlin DSL/version catalog, modules, manual DI, shared Compose shell, Library empty state, Settings/About, basic DisplayProfile/MotionPolicy. Create `full`/`eink` in one dimension; centralize temporary applicationIds; keep Reader out of flavor-specific source trees; default modes are STANDARD/EINK respectively.

Deliver project, README, CHANGELOG, build/test/release docs, basic GitHub Actions, and third-party license list. State project license as TBD. Acceptance: both debug APKs build/install/start and have distinguishable identity; minimal navigation works; both flavors enter CI build/test/lint; formatting tool/command are explicit. Verify actual Gradle tasks before recording commands. Report build vs. device startup separately. Stop at §62 initial delivery; do not implement SAF/full Reader or fake sample business data.

### T03 — Reading Samples and Test Baseline

Depends on T02; REQ-0011. Cover §48 in `testdata/markdown/`, plus empty/large TXT, duplicate heading, BOM, broken encoding, giant paragraph/code, nested lists, image and relative-link folders. Deliver deterministic generator, fixture guide, performance template, and `eink-test-matrix.md`.

Verify exact 100 KB/1/5/10 MB samples with both many-small-block and single-giant-block shapes; exclude test resources from release APK; do not copy documents of unknown origin. Stop with reusable inputs/assertion targets, not fabricated metrics.

### T04 — SAF and Persistence Foundation

Depends on T02; REQ-0001/0003/0010. Implement Library/Document Room tables/foreign keys, versioned position/recent state, DataStore settings, read-only SAF and persistent grants, stream closure/coroutine cancellation. Do not replace content URIs with paths, use MANAGE_EXTERNAL_STORAGE, store bodies, or copy originals by default.

Acceptance: grants survive process recreation; cancellation/denial/revocation/missing files are recoverable; removing Library clears local references only; do not release a grant still shared by another Library. Verify Repository/DAO and SAF instrumented tests and original-file invariance. Stop before recursive-browse UI.

### T05 — Library Scan and Folder Browsing

Depends on T03/T04; REQ-0001. Multiple Libraries, folder picker, recursive metadata scan, `.md/.markdown/.txt`, preserved hierarchy, manual refresh, static/throttled progress. Skip only on reliable matching mtime/size; unknown metadata is not proof of unchanged file. Make scans cancellable and concurrent refreshes merged/serialized. Delete missing rows only after complete success; partial error/permission/cancel does not imply deletion.

Acceptance: deterministic duplicate-add behavior; refresh reflects adds/updates/deletes; empty/unsupported folders work; initial scan does not parse bodies; browse preserves hierarchy. Test slow queries, missing metadata, deep folders, mid-scan failure, and at least one real SAF provider. Stop before background monitoring/library-wide search.

### T06 — Native Markdown/TXT Basic Reader

Depends on T03/T04; REQ-0002. File opening, cancellable background parse, TXT, basic Markdown, loading/error states, title extraction, and block rendering compatible with future navigation. Title order: frontmatter title → first H1 → filename. Limit frontmatter subset; invalid metadata must not fail body.

Validate Markwon adapter on real samples. Never put arbitrary whole document in one TextView; giant blocks need split plain-text fallback or equivalent bounded behavior. Verify required headings/paragraphs/emphasis/lists/tasks/quotes/code/rules, unknown-syntax fallback, UTF-8/BOM, and explicit unknown-encoding fallback without false auto-detection. Test multilingual/emoji, empty/malformed, 10 MB, stale-result rejection, and resource release. Stop with independently verifiable basic Reader; tables/images/search follow later tasks.

### T07 — Reading Flow, Position, and Recent Items

Depends on T05/T06; REQ-0003. Wire folder to Reader, throttled saves + exit flush, recent items, process recreation. Restore block/offset for same version; after content/font/width changes validate and fall back to anchor/progress. Do not assume block index survives edit. Invalidate cache on document changes.

Acceptance: reopen after exit/process restart at reasonable position; missing/renamed/unauthorized recent item has explicit recovery; page recreation does not reparse unchanged content. M0 exit is both flavors supporting add Library → browse → read MD/TXT → exit → restore; this is not M1 completion.

### T08 — TOC, Page Turns, and Gesture Arbitration

Depends on T07; REQ-0005/0006. Hierarchical TOC, duplicate anchors, tap navigation, continuous/PAGE_TURN modes, left/center/right zones, menu. Turn by actual visible height with default 5% overlap (allowed 3–8%); account for Insets/menu/rotation/bounds; no smooth scroll on E-Ink. Links/buttons/code copy/horizontal table/code gestures precede page turns; long press never turns. Acceptance: TOC lands correctly, repeated rapid turns stay in bounds, horizontal scroll does not turn, mode switch preserves location. Stop before true pagination, zone editor, full gesture framework.

### T09 — Typography, Code, and Tables

Depends on T08; REQ-0007/0010. Size/system font/line-spacing/margins; monospace/language label/copy/soft-wrap for code; horizontal scrolling for wide tables. E-Ink defaults wrap ON; wrap-off reaches long-line end. Implement NORMAL table only; defer WRAP/CARD and hide unavailable controls.

Acceptance: settings persist, reflow preserves context, all table columns/code line are reachable on small screens, grayscale/dark contrast is legible. No bundled fonts or complex syntax-highlighting engine.

### T10 — Relative Links and Local Images

Depends on T09; REQ-0009. In-library relative MD/TXT links, parent paths/fragments/same-doc anchors, local images, tap-only external links. Resolve within authorized tree and parent; never concatenate content URI; permit in-tree `..`, reject tree escape; normalize URI consistently; unknown scheme does not execute. Sample-decode images for viewport with bounded memory/cancellation; missing/damaged/unsupported gets placeholder; remote images do not auto-network.

Test spaces/Chinese/nested/`../`/duplicate fragments/missing target, no-handler external app, giant-image no OOM. Stop before network images, cross-Library access, embedded browser.

### T11 — Search in Current Document

Depends on T10; REQ-0008. Literal search in loaded document, count/navigation/current highlight; Markdown uses visible text, not syntax markers. Use parse/render position map, not source offsets as screen coordinates. Cancel stale work/results. Define behavior for large/fallback content; no full parse per query.

Test Chinese/Japanese/emoji/repeats/no match/cross-block rule/code/table and document-switch reset. Stop before regex, advanced query, background library index, or FTS.

### T12 — Display Policy and Settings Integration Acceptance

Depends on T08–T11; REQ-0004/0010. Fully wire both profiles, MotionPolicy, AppCapabilities, EInkController lifecycle, Settings/About. Verify both flavors hot-switch and persist mode; E-Ink animation/smooth/overscroll/ripple/crossfade off, no infinite loader; Standard basic reading works. Generic/NoOp makes no hardware partial/full-refresh claim and Settings shows no unavailable modes. Stop before Onyx or Standard-only animation; centralize policy and avoid brand checks in Reader.

### T13 — Quality Gates and E-Ink Alpha Delivery

Depends on T12; REQ-0011 and all MVP REQs. Run both flavors' builds/unit/lint/format/device suites, release checks, APK analysis, repeatable performance baselines, real E-Ink matrix, and documentation handoff.

Deliver `docs/performance/apk-size.md`, baselines, `docs/development/eink-test-matrix.md`, build/test/release docs, third-party list, CHANGELOG. Record full/eink release bytes/build/R8/signing/dependency/native/font/image makeup; similar initial size is acceptable. Record device/system/firmware/version/sample and cold start/Library/document open/turn/scroll/memory/scan. Set automatic gates only after repeatable thresholds. Test ordinary phone and 6/7/10.3-inch E-Ink; emulator may help size/layout but cannot establish ghosting/refresh. Missing hardware is BLOCKED/NOT_RUN and remains a risk. Verify originals unchanged after uninstall/database clear/Library removal and no unapproved permission/network in merged manifest. Provide §63 delivery report; only evidence-backed REQs become DONE. If signing/license/formal ID/hardware is absent, say candidate package, not formal release.

## 6. Verification Gates

The following commands are candidates, not yet verified against actual tasks in this plan snapshot:

```bash
./gradlew :app:assembleEinkDebug :app:assembleFullDebug
./gradlew test
./gradlew lint
./gradlew :app:connectedEinkDebugAndroidTest
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:assembleEinkRelease :app:assembleFullRelease
```

Add the formatting command only after selecting the tool; do not invent nonexistent Gradle tasks. Report JVM, Android device, and real E-Ink checks separately; one cannot substitute for another.

General Definition of Done: implement only the READY REQ/task scope; code/tests/both flavors pass; attach evidence and mark unrun checks/reasons; screenshots do not replace behavior tests; no source-file writes/default copying/duplicate flavor Readers/unreviewed dependency; update needed REQ/architecture/visible CHANGELOG and dependency license/size; hand off changed files, verified commands/results, limits, and prerequisite status.

## 7. Agent Dispatch Template

```text
Execute TXX from docs/plans/M0-M1-implementation-plan.md only.
Read root AGENTS.md, applicable REQ, architecture/ADR, and prerequisite deliverables.
List prerequisite completion and evidence; do not fake missing dependencies.
Modify only the task module and necessary tests/docs.
Do not add out-of-scope features, edit global versions concurrently, or change frozen contracts without review.
Confirm acceptance criteria before implementation; report evidence/minimal alternative if the requirement conflicts or fails.
Report files, behavior, commands/results, unverified items, and residual risks.
Stop when task criteria are met; do not claim later work automatically.
```

## 8. Release Decisions and Rollback

- T00/T01 technical blockers: actual device Android baseline and native-renderer feasibility for required syntax/large files; keep proposal status if evidence is insufficient.
- Before M1 public release, the owner decides license, formal package name, channel, and signing custody. Agents do not grant legal rights or submit production keys.
- Keep each logical task independently reversible. On failure, revert that task or disable its capability; never reset the user's workspace. Schema changes need migration tests; do not hide position/settings loss by clearing the database.
- If Markdown resource paths are unstable, use an explained verified read-only text fallback. If vendor refresh is unavailable, use generic system behavior. State actual capability in UI/delivery.
- No schedule commitment: complete T00/T02 first, then estimate remaining work from toolchain, renderer, and device evidence. Task count is not a reliable person-day estimate.
