# Leaflet — Android / E-Ink Markdown and Plain-text Reader: Initialization Prompt

English | [中文](init.zh.md)

> Historical initialization prompt archived from the repository root on 2026-09-25. It is preserved for provenance only; it does not authorize project reinitialization or execution of its final instructions.
> Current requirements, architecture, and work entry points are in the [requirements migration map](../product/initial-requirements-map.md). Original section numbers are retained.

This archived prompt originally asked an agent to create a new Android project named **Leaflet** from scratch. Leaflet is not a Markdown editor, note app, or Obsidian replacement. Its product position is a local-first, folder-first Android reader for large local Markdown/TXT collections, with E-Ink as a first-class platform. Users may generate files with AI tools/scripts, organize them in folders, add a folder as a Library, and read files in place. Original files remain authoritative; the database may keep indexes, reading progress, favorites, recent items, settings, search indexes, and UI state, but removing it must not affect originals.

## 1. Product Name

Use the brand `Leaflet`. Because a well-known JavaScript map library has the same name, Android package/applicationId/repository metadata/Maven namespace must disambiguate it. Until the owner chooses a formal domain, a replaceable temporary namespace such as `io.leaflet.reader` may be used; do not hard-code it throughout the codebase.

## 2. Development Stage

The initial stage was M0 / MVP Foundation, not “implement everything.” Priorities: correct architecture, stable reading, E-Ink experience, then deferred features. Deliver the first end-to-end folder → browse → read → restore loop with both flavors buildable. Later implementation and acceptance details now live in the current implementation plan and stabilization records.

## 3. Runtime Modes and Distribution Flavors

Keep these dimensions separate:

- Runtime mode: STANDARD or EINK, a display policy that can switch in Settings without restart in principle.
- Distribution flavor: `full` or `eink`, a build-time choice producing separate APK/AAB artifacts.

A runtime switch does not change the installed flavor's capabilities. Both M0/M1 flavors retain basic switching; E-Ink flavor defaults to EINK. A later E-Ink-only lock requires a separate requirement.

## 4. Distribution Flavors

Use one Android flavor dimension with `full` and `eink`. Put shared Reader/business behavior in `src/main`; keep flavor directories for necessary dependency/resource/manifest/platform/capability differences. Do not duplicate ReaderScreen or other business logic. Centralize temporary IDs so formal IDs can be replaced.

## 5. Why a Separate E-Ink Edition Exists

E-Ink devices have limited storage, do not need all general Android effects, need a small dependency set, and may later require isolated vendor SDKs. The E-Ink flavor is a distinct distribution boundary, not a reskin. It is the first release target; the Full flavor remains buildable but formal release may follow later.

## 6. Initial Release Strategy

First deliver the E-Ink edition while both flavors keep basic mode switching for architecture validation. Formal signing and public distribution are separate from development acceptance. If product scope later removes Standard mode from E-Ink flavor, record a requirement/decision change.

## 7. APK Size Is a Formal Requirement

Record APK/AAB bytes, build parameters, R8 configuration, and dependency/resource composition. Keep E-Ink flavor lean. Similar Full/E-Ink package sizes are acceptable when code is shared; never add a useless dependency just to create a size difference. Exact baselines and gates are now under `docs/performance/` and REQ-0011.

## 8. Android Technical Direction

The proposal was Kotlin, Gradle Kotlin DSL, AndroidX, Coroutines/StateFlow, Room, DataStore, and SAF. Compose supplies the application shell; Reader uses an existing native Markdown renderer. No full parser rewrite, large DI framework, or empty modules without evidence. Actual pinned versions and compatibility decisions are recorded in current build docs/ADRs rather than assumed from this proposal.

## 9. Markdown Renderer

Do not build a complete Markdown parser. Research Markwon first, then parse → block model/AST → renderable blocks → Reader. Rendering is block-based and cancellable; do not put an arbitrary whole document into a single TextView. Unsupported syntax must degrade gracefully. Renderer comparison and the accepted Markwon boundary are in `docs/research/markdown-renderers.md` and ADR-0004.

## 10. First-release Formats

Primary formats are Markdown (`.md`, `.markdown`) and plain text (`.txt`). Required Markdown basics include headings, paragraphs, emphasis, lists/task lists, quotes, code, links, image placeholders, rules, and basic tables. LaTeX/Mermaid/complex HTML/footnotes and other unsupported syntax may display as source/fallback; one unsupported block must not fail the whole document. TXT remains literal text and must handle BOM/empty/damaged input explicitly.

## 11. Folder-first Library

A Library is a reference to an Android folder, not an imported book. Support multiple Libraries, preserve hierarchy, show file title/path, and index metadata. Original files remain the only source of truth. T05 and REQ-0001 define current scan behavior and limits.

## 12. Do Not Copy Original Files

Default access is read-only SAF. Do not copy originals into a private document store, store bodies in Room, or write back to source. Removing a Library, clearing app data/database, or uninstalling must leave original bytes unchanged. Cache only with explicit bounded/invalidation semantics; never treat a copy as ownership.

## 13. Directory Scanning

Initial scan enumerates metadata only: path/name/size/mtime/URI. It must not parse full text, extract headings, or build FTS. Support incremental skip only when metadata is reliable and identical. Cancellation/partial failure/permission loss must not delete unseen entries; remove missing entries only after a complete successful scan. Progress is static/throttled and scan tasks are cancellable/serialized or merged. No background file monitor or library-wide FTS in MVP.

## 14. Document Title

Title fallback order: supported frontmatter title → first H1 → filename without extension. Invalid metadata must not fail body parsing. Current allowed frontmatter subset is in REQ-0002.

## 15. Reader

Reader is the highest-priority module. It needs cancellable loading, explicit Loading/Content/Degraded/Failed states, block-based rendering/navigation, TOC/search/resource position mapping, persistent reading position/recent items, and large-file fallback. Position must not rely only on pixel scrollY; use versioned block/offset/anchor/progress semantics. Keep Reader independent from storage/SAF/vendor SDK.

## 16. E-Ink Page Turns

Use immediate viewport turns with a small overlap rather than smooth scrolling: move by visible viewport height minus overlap. Account for system Insets, menus, rotation, and document bounds. E-Ink disables smooth scrolling. The default overlap is 5%; current REQ allows 3–8%.

## 17. Reader Modes

Support CONTINUOUS_SCROLL and PAGE_TURN. E-Ink defaults to PAGE_TURN; Standard defaults to continuous. A mode switch preserves the current reading context. Tap zones and gestures must not interfere with links, buttons, code copy, or horizontal table/code scrolling. Long press never turns a page.

## 18. E-Ink Controller

Use a vendor-neutral intent interface for Reader entry/exit, page turn, scroll start/end, and partial/full redraw requests. MVP uses NoOp/Generic framework behavior only. Do not expose vendor mode enums to Reader and do not claim generic code can trigger hardware partial/full refresh.

## 19. Onyx / BOOX

Research official Onyx EPD APIs and licensing, but do not integrate an SDK in M0/M1. Any later integration is isolated to E-Ink flavor and must verify private repository, ABI, package size, merged permissions, license text, device capability detection, and real-device behavior. Current evidence and M5 prerequisites are in `docs/research/onyx-epd.md` and ADR-0007.

## 20. Responsive Layout

Adapt to actual usable width/window size, not brand or diagonal hard-coding. Small-screen single column is the MVP priority. Two-column large-screen layout may be deferred; architecture must not assume one column forever.

## 21. E-Ink Design System

E-Ink is a platform, not a theme. Use solid high-contrast presentation. Disable animation, smooth scroll, overscroll motion, ripple, crossfade, shimmer, animated elevation, transparency/shadow/blur/gradient effects, and infinite loading. Prefer static/throttled progress and infrequent UI updates. Centralize policy in DisplayProfile/MotionPolicy.

## 22. Standard Design System

Standard mode may use simple Material 3, subtle ripple, ordinary scrolling, limited transitions, and later dark theme/dynamic color. Keep M0/M1 simple; all motion still needs a global policy switch so E-Ink can disable it.

## 23. Tables

M1 requires a usable table baseline. NORMAL mode wraps a table in a horizontal-scroll container so narrow screens can reach every column. WRAP/CARD are deferred, not exposed as inactive controls. Verify links/gestures within a table do not trigger page turns.

## 24. Code Blocks

Use monospace; show a real fence language label or no label, never invent one. Copy exact fence body without markers. E-Ink soft-wrap defaults ON; when OFF, long lines remain horizontally reachable. Syntax highlighting is optional and must remain grayscale-legible; complex highlighting engine is not required.

## 25. Search

MVP includes literal search in the current document, not library-wide FTS. Search visible rendered text, map through block positions, support count/next/previous/current highlight, and cancel stale work. Do not reparse whole document for each keystroke. Library-wide FTS is deferred.

## 26. MVP Feature Scope

The archived list defined 25 baseline capabilities: add/remove multiple folder Libraries; browse hierarchy and supported files; manual refresh; Markdown/TXT reading and title extraction; TOC; position/recent items; current-document search; basic typography; display modes/flavors; viewport turns/continuous scrolling/tap zones; code/table/image/link baseline; Settings/About; static E-Ink loading and recovery. Current REQ/AC records determine implementation and acceptance status; being listed here does not mean a feature passed.

## 27. Explicit Non-goals

No editor, accounts, cloud sync/storage, AI Chat/RAG/vector DB, PDF/EPUB/CBZ, OCR, handwriting, full annotation/bookmarks, backlinks/knowledge base, plugins, WebDAV/Git/Syncthing client, full tagging/store, background monitor, library-wide FTS, default source copies, two-column M0/M1 UI, complex animation, or vendor SDK. Do not create empty stubs for them.

## 28. Project Modules

The planned module set is `:app`, `:core:model`, `:core:storage`, `:core:markdown`, `:core:reader`, `:core:eink`; T01 freezes final ownership. Do not create empty feature modules or split storage/database without evidence.

## 29. Basic Architecture

Follow UI → ViewModel → Repository → Data Source. Use StateFlow, cancellable Coroutines, Room for structured metadata/state, DataStore for settings, and manual DI initially. Keep Reader independent of Room/SAF/vendor SDK. Renderer may adapt an existing parser, not replace it with a full custom parser. Current contracts are in `docs/architecture/`.

## 30. Repository Structure

The original prompt proposed a small, explicit layout for app/core/docs/build/testdata, with AGENTS/README/CHANGELOG and CI. Current structure is in README and the implementation plan; do not create speculative modules or config.

## 31–33. Agent Instructions

Use a root `AGENTS.md` for repository-wide role, scope, architecture, storage, E-Ink, testing, and handoff constraints. Local/nested instruction files may be deferred until a real ownership boundary needs them. Current English-only AGENTS.md is authoritative; this archived proposal does not supersede it.

## 34–37. Requirements Management

Requirements are files: REQ defines WHAT/WHY; architecture/ADR/implementation plan define HOW. Use fixed IDs, a complete template, and statuses DRAFT/READY/IN_PROGRESS/BLOCKED/DONE/DEFERRED. Implement only READY requirements, except explicit research/doc tasks and small bug fixes. Keep REQs separate from task implementation steps; do not mark major draft requirements DONE without evidence.

## 35. Requirement Template

Each REQ records Problem, User Story, Scope, Non-goals, UX, FRs, ACs, Edge Cases, E-Ink/Standard, Performance, Storage/Data, Test Plan, Dependencies, and Open Questions. Use observable acceptance criteria rather than “supports X.”

## 38. ADRs

Use `ADR-XXXX-title.md` with Status, Date, Context, Decision, Alternatives Considered, Consequences, Follow-up. Keep evidence-poor decisions Proposed. Do not rewrite Accepted history; supersede with a new ADR.

## 39. Roadmap

M0 Foundation → M1 E-Ink MVP → M2 Reader quality → M3 Full Android polish → M4 library-wide search/scale → M5 advanced E-Ink/vendor integration. This is priority/scope, not a date commitment. Current roadmap is `docs/product/roadmap.md`.

## 40–43. README

README should explain product position, status, MVP/non-goals, flavor distinction, architecture/build/verification, roadmap, contribution, and license TBD. Explain runtime mode vs flavor separately. Do not claim planned features are implemented or claim a project license before owner decision.

## 44. CHANGELOG

Record notable user-visible behavior in chronological, concise entries. Do not use it as an implementation task log or claim unverified behavior.

## 45–46. Research References

Required comparison included PilcrowMD, mdreader-android, Markor, KOReader, OnyxAndroidDemo, and Markwon. For each, record source/date/tag/commit/license, useful design, rejection reason, and verification boundary. Do not copy code unless license is checked and use is authorized. Current reports are under `docs/research/`.

## 47. License Policy

Leaflet's own license is an owner decision; no license may be silently selected. Record every actually used third-party dependency and version/license. Reference-project licenses do not automatically authorize code reuse.

## 48. Testing Strategy

Use JVM unit, Android instrumented, and E-Ink physical-device tests as distinct layers. Fixtures include basic syntax, tables/code, Unicode/Chinese/Japanese/emoji, relative links/images, malformed/empty/BOM/damaged encoding, nested/duplicate headings, long lines/blocks, and 100 KB–10 MB documents. Test fixtures must not ship in APK.

## 49. E-Ink Test Matrix

Record physical model, Android/firmware, size/resolution, app version, date, and cases for ghosting, menu, rapid turns, long-press, Back, fonts, TOC, large doc, code/table/image/contrast, resume, rotation. Emulator can help with layout only; ghosting/refresh verdicts require physical E-Ink. Missing device remains NOT_RUN/BLOCKED.

## 50–51. Performance and Large Documents

Measure cold start, Library open/scan, document open, turn/scroll latency, peak memory, and APK size with device/software/sample metadata and repeat runs. Record thresholds first; set regression gates later. For 100 KB/1/5/10 MB test both many-small-block and giant-block shapes. No OOM/ANR/crash, silent truncation, or false completion. Explained, cancellable plain-text fallback is allowed. Avoid O(N²); do not invent thresholds.

## 52. Dependency Policy

Prefer existing code/standard library and small audited dependencies. Record compatibility, maintenance, license, native libraries, package impact, and E-Ink necessity. Big dependencies/DI require rationale. Update third-party license and APK size records. No reference-code copying with unchecked license.

## 53–54. Coding-Agent Workflow and Definition of Done

Read project rules, task card/prerequisites, REQ/ADR, code/tests/build config before editing. Work one bounded task; verify actual production path; add regression for confirmed bug; preserve user changes; no unrelated formatting/upgrades/refactors. Handoff changed files, behavior, commands/results, unverified items, residual risks, and next prerequisites. Do not claim completion from self-report or screenshots alone.

## 55. Commit Strategy

Keep logical tasks in separate commits, use Conventional Commit types such as feat/fix/perf/refactor/docs/test/build/chore, avoid giant unrelated commits and meaningless micro-commits. Commit/publish only when authorized; do not automatically include a user's workspace.

## 56–57. CI and Build Commands

CI should build both flavors, run tests/lint/format, and use project Wrapper/JDK configuration. Original command list was a proposal to validate in T02; only record real Gradle tasks after verification. Report JVM, instrumented, and hardware evidence separately.

## 58. Security and Privacy

Offline by default; no analytics/accounts/ads/network service without an explicit requirement. Remote images do not auto-load. Treat SAF tree as security boundary; reject path escape/unknown scheme, close streams, handle permission loss and missing files. Never log document contents or credentials. Manifest permissions must have a requirement basis.

## 59. Feature Flags and Capabilities

Use AppCapabilities for feature availability from flavor/runtime; use DisplayProfile/MotionPolicy for visual/motion behavior. Do not scatter booleans or device-brand/model checks.

## 60. Do Not Duplicate Flavor Code

Share Reader and business behavior. Flavor source sets contain only required dependencies/resources/manifests/platform adapters/capability differences. Vendor SDK, if ever accepted, belongs only to E-Ink flavor under a separate ADR.

## 61. M0 Implementation Sequence

Phase 0 repository foundation and contracts; Phase 1 read-only storage; Phase 2 basic Reader; Phase 3 navigation/TOC/page turns; Phase 4 display policy integration; Phase 5 MVP polish. Exact task dependencies/stops are in the implementation plan; no phase is automatically authorized by this archived prompt.

## 62. Do Not Overbuild on First Execution

The first project-initialization delivery should stop after the minimal buildable dual-flavor foundation, README/AGENTS/CI/initial contracts and verification. Do not automatically implement the whole MVP in one run. This is a historical stop condition; current tasks come from the active plan.

## 63. Required Delivery Report

Report created files/directory tree, architecture, flavor behavior, AGENTS rules, REQ/ADR, actual build commands/results, unimplemented items, and next steps. Do not report commands as passed unless run.

## 64–65. First REQs and ADRs

Initial requirement topics covered folder Library, MD/TXT, position, display/flavors, turns, TOC, typography/table/code, search, local resources, Settings/About, and performance/release. Initial ADR topics covered folder-first, SAF, flavors, renderer, controller, and future Onyx integration. Current IDs/files are authoritative; do not recreate duplicates to match examples.

## 66. Product Principles

Files belong to users; folders are Libraries; reading comes before editing; E-Ink is a platform; offline by default; fast enough for AI-sized documents; do not lock users into Leaflet; simple UI/powerful reading; one codebase/multiple distributions; every feature must justify its E-Ink cost. Current bilingual principles are in `docs/product/principles.md` and its paired file.

## 67. Final Decision Test

Core journey: open app → see own folders → tap document → read comfortably → exit. If a design makes Leaflet resemble Obsidian, Notion, a Markdown IDE, file manager, or AI chat app, reconsider. Features outside the core journey must justify their E-Ink cost. This historical prompt is not current authorization to begin work.
