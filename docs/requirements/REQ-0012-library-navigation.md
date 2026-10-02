# REQ-0012 — Library Navigation (Breadcrumbs, Stable Scan Status, Row Targets)

English | [中文](REQ-0012-library-navigation.zh.md)

Status: READY
Priority: P2
Milestone: M1 (UI/UX optimization phase; parent UX-05, delivered through tasks NB-00–NB-05)

Source: `docs/plans/ui-ux-audit-and-optimization-plan.md` UX-05 (ISS-014–ISS-017); [Library Navigation UX Plan](../plans/library-navigation-ux-plan.md); scoped supplement to [REQ-0001](REQ-0001-folder-library.md) (browse/scanning) and [REQ-0004](REQ-0004-display-profiles.md) (display/motion policy).

## Problem

Deep folder navigation gives the user no way to jump to an ancestor: the only way up is Back, one level at a time, and the current location is a raw relative-path string. Scan progress inserts and removes text, buttons, and gaps above the file list, so the list jumps whenever a scan starts, is cancelled, or fails. File rows are roughly 28 dp tall with cramped padding, and folders and documents are told apart only by a trailing slash — hard to hit and ambiguous on a grayscale E-Ink screen.

## User Story

As a reader whose collection is nested several levels deep, I want to see where I am and jump straight to any ancestor, have the file list stay put while a scan runs, and have large, clearly typed rows to tap, so that I can reach the document I want quickly without losing my place.

## Scope

- A breadcrumb for the open Library: the Library root plus one segment per path segment of the current relative prefix. Tapping an ancestor opens that exact prefix; the current segment is identified and does not navigate.
- A reserved scan-status region whose outer size does not depend on the scan state, while Refresh, Cancel, Retry, and the recoverable failure/cancellation information stay available.
- File and folder rows with at least 48 dp clickable height that grows for wrapped names and larger fonts, distinct folder/document cues, and readable MD/TXT labels.

Included: `LibraryBrowseScreen`, its rows, the browse state and ViewModel action they need, and the string resources and tests for those.

Excluded (unchanged, owned by other packages): UX-04's `LibraryListScreen`/Recent restructure, UX-01 window insets, UX-02 E-Ink palette work, UX-10 full string extraction, per-folder scroll history, process-death restoration of the browsing path, storage/index migrations, and Reader behavior.

## Non-goals

- No folder-history persistence and no process-death restoration of the browsing path.
- No additional scan, database table, or index change; a breadcrumb never queries SAF and never parses a content URI.
- No global design system, icon-library dependency, sorting modes, or Library-wide search.
- No new Library-management actions and no change to scan algorithms, cancellation, or index-write semantics.

## User Experience

1. At `Docs/Notes/2026` the browse screen shows the Library root and the segments `Docs`, `Notes`, `2026`. Tapping `Docs` shows `Docs`'s direct children; tapping the root shows the root entries. Back from `Docs/Notes/2026` moves up exactly one segment; Back at the root leaves the Library.
2. Navigating to a different prefix starts that directory's list at the top. Tapping the current segment leaves state and scroll unchanged.
3. Long paths stay reachable: the breadcrumb is one bounded-height row that scrolls horizontally, and it keeps full names accessible even when visual labels are shortened.
4. Starting, cancelling, or failing a scan never moves the file list: the status text and its single action live in a reserved region.
5. Folder rows show a folder cue; file rows show a document cue and an MD/TXT label. Every row is at least 48 dp tall, long names wrap, and the row grows instead of truncating controls.
6. Opening a file still hands the original document id to the Reader.

## Functional Requirements

- **FR-1 Segments:** Generate the Library root and one segment per path segment of the current relative prefix. Root is the empty prefix; every target is an exact path prefix at a segment boundary. Literal percent characters and case are preserved; content URIs are never used as path strings.
- **FR-2 Guarded ancestor selection:** One ViewModel action selects a generated target. A stale Library request, sibling target, tree escape, invalid externally supplied prefix, or the current target is ignored without changing state. Ancestor selection needs no file content or SAF query.
- **FR-3 Anchored breadcrumb:** Render the generated segments in one bounded-height, horizontally scrollable row. Each non-current segment is a target of at least 48 dp that opens its exact prefix; the current segment is visually and semantically identifiable and has no redundant navigation action; full names remain accessible when a label is shortened.
- **FR-4 Stable status region:** One reserved region hosts the scan status text and its single action (Refresh, Cancel, or Retry) for every state — `IDLE`, `SCANNING`, `CANCELLING`, `SUCCEEDED`, `FAILED`, `CANCELLED`. Its outer size is independent of state at a fixed available width and font scale; the failure/cancellation reason stays available and is not hidden by ellipsis alone (the full text remains exposed to accessibility).
- **FR-5 Row targets and type cues:** Every file/folder row is clickable across its full width with at least 48 dp height, and grows for wrapped names or a larger font scale. Folder and document rows carry distinct cues that do not rely only on color, plus readable type labels (MD / Markdown / TXT). Decorative icons do not duplicate announcements.
- **FR-6 Preserved contracts:** Empty folders remain selectable, directory-before-file ordering and case-insensitive name order are unchanged, a different prefix starts its list at the top, the read-only SAF boundary and scan cancellation/index-write semantics are untouched, and both flavors share one implementation through the existing display/motion policy.
- **FR-7 Strings:** New user-visible labels are Android string resources with English and Simplified Chinese counterparts. Legacy strings are not extracted here (UX-10).

## Acceptance Criteria

- **AC-1 (plan AC-N1) Ancestors:** At a three-level prefix, the breadcrumb shows the Library root and every path segment. Tapping an ancestor opens that exact prefix; the current segment has no redundant action. Back moves up exactly one segment and exits once at the root.
- **AC-2 (plan AC-N2) Path identity:** Root is the empty prefix; matching is by whole path segment, so `a` is not an ancestor of `ab`. Case, spaces, Chinese names, and literal percent characters are preserved. Only the root/current/ancestor targets generated for the current Library are accepted; stale, sibling, escaping, or unrelated targets are rejected without a state change.
- **AC-3 (plan AC-N3) Long paths:** The current location stays identifiable and every ancestor stays reachable on a narrow screen and at 200% system font scale, through a bounded-height horizontally scrollable row with accessible full labels. Segments measure at least 48 dp.
- **AC-4 (plan AC-N4) Scan stability:** At unchanged width, font scale, prefix, and indexed rows, transitions through `IDLE → SCANNING → CANCELLING → CANCELLED`, `SCANNING → SUCCEEDED`, and `SCANNING → FAILED → Retry` leave list viewport position and size and the first visible item key/offset unchanged, allowing one physical pixel of measurement rounding. Refresh/Cancel/Retry remain operable and the failure/cancellation reason remains available. A changed document index or device configuration is tested separately and is not a scan-state-only invariant.
- **AC-5 (plan AC-N5) Row affordance:** Measured clickable bounds for folder rows and for MD/Markdown/TXT file rows are at least 48 dp; at 200% font scale long English/Chinese names wrap without truncating controls or forcing a fixed row height; empty folders remain selectable; opening a file passes the same document id to the Reader.
- **AC-6 (plan AC-N6) Preserved contracts:** Directory-before-file order and case-insensitive name order are unchanged; independent Libraries, the read-only SAF boundary, scan cancellation, and retention of the previous index after failure remain intact.
- **AC-7 Gates:** The JVM/lint/ktlint gates and both `full` and `eink` debug builds pass, and every new user-visible string has an English and a Simplified Chinese resource.

## Edge Cases

Empty prefix (root only); a single-segment prefix; a ten-level path; names with spaces, Chinese characters, mixed case, and literal `%`; a sibling whose name extends another (`a` versus `ab`); empty directories; two Libraries whose folder names match; a target delivered after Back or after the Library changed; 200% system font scale; portrait and landscape; a scrolled populated folder; a revoked grant; a failed or cancelled scan with a long path or reason; a very long file or folder name; and an oversized list that must not re-measure on status-only updates.

## E-Ink Considerations

No animation, crossfade, or smooth scrolling is introduced. The breadcrumb and status region are static text and static targets; the current segment is distinguished by weight and an accessible state, not by color alone. Ripple and motion follow the existing DisplayProfile/MotionPolicy. Both runtime modes are exercised; real E-Ink ghosting/refresh behavior remains a hardware check.

## Standard Android Considerations

Standard mode uses the same screen and the same browse state; ripple and scrolling follow DisplayProfile. The breadcrumb's horizontal scroll never participates in Reader page-turn gestures, and no new permission or intent is added. TalkBack reading order remains a device check.

## Performance Considerations

Segment generation and target validation are pure and linear in path depth. The breadcrumb performs no SAF or index query per recomposition, and a status-only update at a fixed width and font scale must not re-measure the file list. No measured threshold is claimed here; the reserved region is sized from the supported status/action layout rather than a guessed universal height.

## Storage / Data Impact

None. No schema, DAO, migration, or DataStore change; the browse prefix stays in-memory ViewModel state, and no original file is read, copied, or written by this feature.

## Test Plan

- JVM: pure segment/target mapping and validation — root, three-level, and ten-level paths; Chinese, spaced, mixed-case, and literal-`%` names; `a` versus `ab`; invalid, stale, sibling, escaping, and current targets — plus ViewModel transitions over a fake `LibraryListSource`, including an unchanged-state assertion for rejected requests and a Library switch that starts at the new root. Existing `BrowseLevelTest` and `LibraryRemovalTest` stay green.
- Instrumented page tests against the production `LibraryBrowseScreen` with a fake source and controlled progress: tapping ancestors and the root, the current segment's no-op, segment and row clickable bounds of at least 48 dp, each scan-state transition with measured list bounds plus the first visible item key/offset, a long error/path, an empty folder, a scrolled populated folder, and long names at 200% font scale, in both runtime display modes.
- Real E-Ink ghosting/refresh and TalkBack checks are recorded separately and remain NOT_RUN without hardware; software acceptance is not a release claim.

## Dependencies

Scoped supplement to [REQ-0001](REQ-0001-folder-library.md) (browse and scanning behavior) and [REQ-0004](REQ-0004-display-profiles.md) (display and motion policy); it consumes the document metadata of [REQ-0002](REQ-0002-markdown-txt-reader.md) but does not change it. Parent: UX-05, split into NB-00–NB-05. UX-01/UX-02/UX-04 are not delivered; the plan's NB-00 readiness record holds the narrowed dependency and its rationale.

## Implementation status (2026-10-01)

NB-01–NB-05 are implemented and software-verified; device, E-Ink, and TalkBack checks remain NOT_RUN. See [Library Navigation UX delivery](../plans/library-navigation-ux-delivery.md).

- **FR-1/FR-2 (AC-1/AC-2):** `Breadcrumbs.kt` generates the Library root plus one entry per path boundary and validates targets by whole path segment. A tap travels as an `AncestorRequest` carrying the Library and prefix it was rendered for, and `LibraryViewModel.openAncestor` verifies both are still current, so a stale, sibling, escaping, externally supplied, or other-Library target is ignored without a state change, and the current directory is a no-op (NAV-ACC-01). Regressions: `BreadcrumbTest` (8 JVM methods), `LibraryAncestorNavigationTest` (11 JVM methods).
- **FR-3/FR-5 (AC-1/AC-3/AC-5):** `LibraryBrowseScreen` renders the breadcrumb in one bounded-height horizontally scrollable row — each entry at least 48 dp, the current entry bold, state-described, and not clickable — and every file/folder row as a full-width, at-least-48 dp target with an in-repo folder/document glyph, an MD/Markdown/TXT tag, and wrapped names. The browse list is a bounded `weight(1f)` viewport so it scrolls and the Back control stays on screen.
- **FR-4 (AC-4):** one reserved status region hosts the status text (always two lines) and exactly one action (Refresh/Cancel/Retry) for every `ScanProgressState`; failure and cancellation are carried by `progress` alone, so no state-dependent block can appear above the list. `cancelRefresh` only acts while a refresh job is active and the state is `SCANNING`/`CANCELLING`, so a late Cancel cannot publish an unfinishable `CANCELLING` (NAV-ACC-02; `LibraryScanCancelTest`, 3 JVM methods).
- **FR-6 (AC-6):** a change of Library or prefix resets the browse list to the top through a hoisted `LazyListState`, so a scrolled directory does not leak its offset into the next one.
- **FR-7:** the new labels live in `values/strings.xml` with `values-zh/strings.xml` counterparts. Because UX-10 has not yet delivered full locale parity, `MissingTranslation` is suppressed in the three default string files with an explicit comment and must be removed with that work.
- **Gates:** `./gradlew --offline --no-build-cache --max-workers=1` over every unit-test task ran 844 cases / 347 unique methods with 0 failures, 0 errors, 0 skipped; `lint` and `ktlintCheck` passed; `:app:assembleFullDebug` and `:app:assembleEinkDebug` passed. `LibraryBrowsePageTest` (11 page methods on the production screen with a real scanner and an in-memory Room index) compiles but is NOT_RUN without a device. The independent acceptance's own probe and runner were re-run against this revision: 21 existing tests and both `NavigationAcceptanceProbe` cases pass (`review-evidence/2026-10-01-navigation-acceptance/recheck-after-fix.txt`).

## Open Questions

- Breadcrumb label-shortening rules and the reserved status-region height are measured per device and font scale; only "state-independent at a fixed width and font scale" is frozen here.
- TalkBack reading order, 200% font-scale wrapping, and real E-Ink ghosting remain unverified without hardware.

## Independent fix follow-up (2026-10-01)

The original NAV-ACC-20261001 report stays historical. [NAV-FOLLOWUP-20261001](../plans/library-navigation-ux-acceptance-followup-2026-10-01.md) freshly verifies the origin-aware ViewModel action and idle-cancel guard (24 regressions plus two independent probes). It also reproduces and fixes a remaining UI binding defect: delegated `State` was read when a queued callback ran, so the origin could be relabeled as the latest Library/prefix. The screen now captures plain Library/prefix values during rendering. The real compiled callback fails before the patch and passes in both flavor compilations afterward.

The page test now inspects row children in the unmerged semantics tree and includes a retained-click-action/prefix-change regression. Both navigation components and the 12-method page suite compile per flavor; changed-file ktlint passes. Actual page execution, APK assembly, whole-project lint/ktlint, real E-Ink, and TalkBack remain unverified because Gradle/ADB sockets are blocked. This is focused fix evidence, not closure of every AC or advancement to DONE.
