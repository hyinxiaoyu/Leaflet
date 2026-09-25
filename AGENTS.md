# Leaflet Agent Rules

## Project and Current Stage

Leaflet is a local-first, folder-first Android Markdown/TXT reader with E-Ink as a first-class target. It is not an editor or note-taking platform.

The project is currently an M1 Alpha candidate. The Android project is in place; both flavors build, R2-07 production-page tests pass 12 tests per flavor, and 240 deduplicated JVM tests pass. Real-device E-Ink ghosting and refresh behavior, the project license, the formal application ID, signing, and distribution remain undecided or unverified. Task boundaries and stop conditions are in `docs/plans/round-2-stabilization-plan.md` and `docs/plans/round-2-acceptance-verdict.md` (`M0-M1-implementation-plan.md` is a PLANNED snapshot). Work only on the task explicitly assigned by the user. Do not initialize the project or advance to a later task unless an implementation task was specified.

Follow the user's current explicit instructions, applicable project rules, accepted REQs/ADRs, and established implementation conventions in that order. Requirements start at `docs/requirements/README.md`; tasks start at `docs/plans/remaining-mvp-tasks.md`. The initialization requirements are archived; see `docs/product/initial-requirements-map.md` for their destinations. Interpret scope ambiguity using section 2 of the original implementation plan and current ADRs. The archived prompt does not authorize reinitializing the project. If a material conflict arises, report the evidence and the smallest resolution; do not expand scope on your own.

## Before Starting Work

1. Read this file, `README.md`, the relevant task card in the implementation plan, and its prerequisite deliverables.
2. Read the applicable `docs/requirements/REQ-*.md`, architecture documents, and ADRs. Do not claim to have reviewed files you have not read.
3. Inspect the existing code, build configuration, tests, and workspace changes. Preserve unrelated work by the user or other agents.
4. Confirm task scope, file ownership, acceptance criteria, and stop conditions. Planning, audit, and documentation tasks do not authorize product implementation.

## Requirements and Decision Workflow

- REQs describe WHAT and WHY; architecture documents, ADRs, and the implementation plan describe HOW.
- REQ status must be one of `DRAFT / READY / IN_PROGRESS / BLOCKED / DONE / DEFERRED`. Feature implementation starts from a READY requirement.
- T00/T01 research and requirements work, and explicitly authorized documentation maintenance, do not require a READY feature requirement first. Small bugs may be fixed directly; a clear new feature requires a REQ first.
- Important architecture or dependency choices require evidence and an ADR. Keep a decision Proposed when evidence is insufficient. Supersede material changes to accepted ADRs with a new ADR; do not rewrite history.
- Do not change module contracts or perform broad refactors without authorization. If a breaking change is required, first record its impact and migration plan, then handle dependent tasks.

## Architecture Boundaries

- Planned modules: `app`, `core:model`, `core:storage`, `core:markdown`, `core:reader`, and `core:eink`. T01 finalized this plan; do not create empty modules without a use case.
- Follow UI → ViewModel → Repository → Data Source. Use StateFlow for state and Coroutines for asynchronous work; prefer manual dependency injection for the MVP.
- `storage` encapsulates SAF, Room, and DataStore. `reader` must not access those implementations directly or depend on the Onyx SDK.
- Adapt the existing native Markdown parser. Do not build a complete Markdown parser or put an arbitrarily large document into one TextView.
- Reader loading must be cancellable and expose explicit error and fallback states, with extensible position mapping. Pixel `scrollY` alone is not a durable reading-position contract.

## Storage and Privacy Constraints

- Original files are the single source of truth. Use read-only SAF access by default; do not copy files into an app-owned library or write back to originals.
- Removing a Library, clearing the database, or uninstalling the app must not delete original files. The database stores auxiliary data only.
- Persist URI permissions. Handle revoked permissions, missing files, unknown metadata, cancellation, and partial failure; close every file stream.
- The initial scan indexes metadata only; it must not parse the entire library. A failed or cancelled scan must not treat unscanned entries as deleted.
- Resolve relative paths only inside the authorized tree. Never construct ordinary filesystem paths by concatenating content URIs.
- Work offline by default. Do not automatically load remote images or add accounts, analytics, ads, or network services. Do not log document contents, credentials, or unredacted sensitive data.

## Product Flavors and E-Ink Constraints

- `full` and `eink` are distribution flavors; STANDARD and EINK are runtime modes. Keep these dimensions distinct.
- Both M0/M1 flavors retain basic mode switching; `eink` defaults to EINK. Keep temporary application IDs in centralized configuration.
- Share the Reader. Flavors contain only necessary differences in dependencies, resources, manifests, platform adapters, and capabilities.
- Centralize animation, scrolling, and visual policy in DisplayProfile/MotionPolicy. Use AppCapabilities for available features; avoid scattered Boolean flags and device-brand checks.
- Disable animations, smooth scrolling, overscroll effects, crossfades, and ripples on E-Ink. Use static or throttled loading states and avoid transparency, shadows, and frequent UI updates.
- MVP page turns use immediate scrolling by viewport minus overlap. Do not implement a full pagination layout engine.
- MVP uses a generic/NoOp EInkController and does not integrate a vendor SDK. Do not claim that the generic implementation controls hardware partial refresh. Emulator results cannot replace real-device acceptance.

## Dependencies and Change Scope

- Prefer existing code, the standard library, AndroidX, and small, mature dependencies. Audit version compatibility, maintenance, license, native code, release size, and E-Ink necessity before adding a dependency.
- Calibrate toolchain and compatibility claims against official documentation or source, and record versions and dates. Do not claim current compatibility from memory.
- Explain the benefit of large dependencies or DI frameworks. Dependency changes require updating third-party license and APK-size records.
- Do not copy reference-project code until its license is verified. Do not choose a project license or provide production signing material on behalf of the owner.
- Do not add an editor, accounts, AI/RAG, cloud sync, PDF/EPUB, plugins, or library-wide FTS as a side task. Do not duplicate business logic across flavors.
- Do not globally reformat, upgrade unrelated dependencies, or clean unrelated code. Do not overwrite others' changes. Do not commit or publish automatically.

## Build, Tests, and Formatting

The following commands were verified against actual Gradle tasks by T02 on 2026-09-17:

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew test lint ktlintCheck
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
./gradlew :app:assembleFullRelease :app:assembleEinkRelease
```

Formatting uses ktlint (`org.jlleitschuh.gradle.ktlint:12.1.2`) via `ktlintCheck`. Compose functions with uppercase names are exempted from `ktlint_standard_function-naming` in `app/build.gradle.kts` under `ktlint { additionalEditorconfig }`. See `docs/development/build.md` for measured version evidence and the deviation from the T00 proposal.

Choose checks based on change risk and distinguish JVM, instrumented, and real E-Ink device verification. Cover empty or damaged files, Chinese/Japanese/English text and emoji, relative resources, revoked permissions, process recreation, large files, and cancellation. For 100 KB / 1 MB / 5 MB / 10 MB documents, an explained fallback is acceptable; OOM, ANR, crashes, and silent truncation are not.

For documentation-only changes, check content consistency, file paths, and links. Do not run unrelated or nonexistent build tasks.

## Definition of Done and Handoff

- Implementation meets the applicable acceptance criteria; relevant tests and both flavor builds pass, with no clear Standard or E-Ink regression.
- Original-file safety, module boundaries, and flavor isolation remain intact; no unexplained large dependency is introduced.
- Update relevant REQ status and architecture documents. Update CHANGELOG for user-visible behavior changes. Review size when dependencies change.
- Record checks that could not run, the reason, and the follow-up command. Without an E-Ink device, mark hardware verification as unverified; do not claim DONE or released.
- Report changed files, behavior, verification results, limitations, and whether prerequisites are met. Stop at the task's stop condition; do not claim the next task automatically.

An engineering handoff report must include created content, directory tree, architecture points, flavor behavior, core AGENTS rules, REQs/ADRs, actual build commands, verification results, unimplemented items, and next steps. The first-time project initialization is now a historical task and must not be repeated.

## Documentation Language Policy

English is the primary language of project documentation; Simplified Chinese is maintained as a paired translation.

- **English first:** `README.md`, design documents, plans, reports, notes, runbooks, changelogs, ADRs, and other documentation are authored in English and must be complete and self-sufficient in English.
- **Paired files:** Keep each document as `foo.md` (English) and `foo.zh.md` (Simplified Chinese) in the same directory. Immediately below the H1, the English file must have `English | [中文](foo.zh.md)` and the Chinese file must have `[English](foo.md) | 中文`. Chinese is secondary and does not replace the English file.
- **Agent instructions are English-only:** `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, and other agent instruction files must not be translated or paired.
- **Complete the Chinese side:** If its translation is missing, empty, partial, stale, or left as a placeholder or `TODO`, complete it in the same change. Do not leave untranslated sections.
- **Keep each pair in sync:** Editing either side requires updating the other in the same change. The `deepseek-harness` repository additionally records `foo.i18n.yaml` with `pnpm run verify-translation-pairing --write <pair>`.
- **Preserve structure:** Mirror heading depth and order, list kinds, table shape, link targets, and code fences. Do not translate code, identifiers, commands, paths, logs, or API names. On first use, write a term as `中文术语 (English term)`.
- **Scope:** Translate documents touched by the current task and any additional documents explicitly included in a user-requested sweep. Leave unrelated documents alone. In an upstream or third-party repository whose contribution rules reject extra files, follow that repository's convention and state the reason.
- **Canonical policy:** `~/.agents/rules/documentation-language-policy.md` and its paired `documentation-language-policy.zh.md`.
