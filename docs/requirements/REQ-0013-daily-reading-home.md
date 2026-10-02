# REQ-0013 — Daily Reading Home and Settings

English | [中文](REQ-0013-daily-reading-home.zh.md)

Status: IN_PROGRESS
Priority: P1
Milestone: M1 product refinement

## Problem

The app can read files but its entry screen clips when Libraries grow, recent files lack a primary continuation action, typography controls show abstract steps, and incomplete window/color policy makes the shell inconsistent.

## User Story

As a daily reader, I want a clear first step, one-tap return to my last document, reachable folder/history/settings controls, and a live typography preview, so that Leaflet is comfortable to use repeatedly.

## Scope

A single scrolling Home with first-use guidance, real Library and Recent data, continuation action, recoverable stale-entry hints, and confirmed history clearing; settings preview and uniform choices; shared system-bar/cutout bounds and monochrome E-Ink tokens.

## Non-goals

No editor, accounts, sync, library-wide search, schema migration, new dependencies, invented reading percentages/counts, signing/license decisions, or vendor hardware refresh.

## User Experience

Empty Home explains folder selection and original-file safety. Returning users see their latest document, folders, history, and Settings/About in one scroll. Unavailable documents keep recovery hints. Typography changes update a sample immediately; source settings remain auto-persisted.

## Functional Requirements

- FR-1: Home has one vertical LazyColumn; 20 Libraries and 30 Recent entries do not hide navigation controls.
- FR-2: Continue opens the real latest document ID through the existing Reader restore path; missing/access-lost documents remain visible with honest actions.
- FR-3: History clearing requires confirmation and never deletes original files; existing Library removal confirmation is preserved.
- FR-4: Settings show resolved font size, line spacing and margin values, with a preview using ReaderStyle; all choices remain persisted through SettingsViewModel.
- FR-5: Navigation respects system bars/cutouts; E-Ink theme tokens are grayscale and use the current no-motion policy.
- FR-6: All new UI copy has English/Simplified Chinese resources, with no developer milestones or fabricated metrics.

## Acceptance Criteria

- AC-1: Empty Home has a working Add folder callback, readable guidance and reachable Settings/About.
- AC-2: With 20 Libraries/30 Recent entries, every section and footer remains reachable; Continue passes the correct ID and unavailable entries are recoverable.
- AC-3: Cancel history-clear leaves rows intact; confirm invokes clear once; Library removal still confirms and original-file safety is unchanged.
- AC-4: Each typography change updates the preview and actual value labels; all option groups expose selected semantics and at least 48 dp targets.
- AC-5: Header/footer avoid status/navigation bars and cutouts; E-Ink color tokens contain no chromatic components and selected controls remain legible.
- AC-6: Fresh JVM/component checks pass; actual page/device checks are recorded separately, with unavailable checks never relabeled as passes.

## Edge Cases

No Libraries, no history, missing files, lost grants, metadata still resolving, long Chinese/English names, 200% font scale, portrait/landscape, large lists, system dark mode with E-Ink enabled.

## E-Ink Considerations

Static feedback, opaque surfaces, clear borders, no new motion/overscroll/ripples; hardware refresh and ghosting still require a real device.

## Standard Android Considerations

Use the same layout and controls with a calm reading palette; preserve system dark theme and accessibility semantics.

## Performance Considerations

Lazy rows reuse existing state streams; no document-body scan or timer-driven recent labels. No additional storage query is required for this slice.

## Storage / Data Impact

None. Continue uses existing IDs and reading positions; settings use existing DataStore keys; history operations remain auxiliary-data-only.

## Test Plan

Focused JVM checks for display policy and existing metadata/state contracts; production-content page cases for large Home, continuation, clearing confirmation and settings preview; fresh Kotlin/Compose compilation for both flavors.

## Dependencies

REQ-0001/0003/0004/0007/0010 and the current UX-03/04 direction. This slice implements shell improvements only; global localization, external-open intents and release gates remain separate.

## Open Questions

Android page execution is currently constrained by sandbox socket permissions. Record actual execution limits; this READY scope is authorization for implementation, not a DONE/release claim.
