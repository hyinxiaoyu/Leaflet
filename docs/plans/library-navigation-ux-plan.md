# Library Navigation UX Plan

English | [中文](library-navigation-ux-plan.zh.md)

Date: 2026-10-01
Status: DELIVERED — SOFTWARE VERIFIED, DEVICE PENDING; NB-00's requirement gate and NB-01–NB-05 are complete — see [delivery](library-navigation-ux-delivery.md).
Parent: [UI/UX Optimization Tasks](ui-ux-optimization-tasks.md), UX-05 (ISS-014/015/016/017).
Tasks: [Library Navigation UX Tasks](library-navigation-ux-tasks.md).
Baseline: [UX-07 delivery](ux-07-delivery.md); [implemented Library browse/removal tasks](library-browse-and-remove-tasks.md).

## Outcome and scope

Make folder browsing easier to navigate and operate: users can jump to an ancestor folder, scan-state changes do not displace the file list, and file/folder rows have clear type cues and at least 48 dp touch targets.

This is the next browsing slice. It covers `LibraryBrowseScreen` and the app-owned navigation state it needs. UX-04's home/Recent restructuring, UX-01's external-open intents, global UX-02 palette work, global UX-10 string extraction, Reader behavior, storage migrations, and new dependencies stay in their existing work packages.

## Confirmed baseline

| Evidence | Current behavior | Implication |
| --- | --- | --- |
| `LibraryScreens.kt`, `LibraryBrowseScreen` | `state.prefix` is plain text; only enter-directory and Back actions exist | Add ancestor actions using the indexed relative prefix |
| `LibraryViewModel.kt` | `openLibrary` resets the prefix; `enterDirectory`/`leaveDirectory` own prefix updates | Keep folder navigation in the ViewModel; switching Libraries starts at their root |
| `BrowseLevel.kt` | Existing indexed directories plus document-derived parents produce direct children with stable keys | Reuse ordering and empty-directory support; no additional scan or database table |
| `LibraryScreens.kt`, progress and notice branches | Status text, buttons, and gaps appear/disappear above the list | Reserve a stable status region rather than adding scan-specific spacers |
| `LibraryScreens.kt`, row modifiers | File rows have 4 dp vertical padding; folder rows have 8 dp | Measure actual clickable bounds, then enforce a minimum instead of a fixed row height |
| `MainActivity.kt`, `LeafletNav.kt`, `Theme.kt` | Edge-to-edge is enabled; these routes have no explicit safe-area scaffold; E-Ink palette assigns only some tokens | UX-01/02 readiness is not established by source inspection alone |

Source review confirms these implementation facts. This planning change supplies no new emulator or real-device evidence. The UX-07 test counts are historical evidence for that delivery, not acceptance of this slice.

## Acceptance decisions

- **AC-N1 — Ancestors:** At `Docs/Notes/2026`, show the Library root and every path segment. Each ancestor opens that exact prefix; the current segment is identified and has no redundant navigation action. Root Back leaves the Library; nested Back moves up exactly one segment.
- **AC-N2 — Path identity:** Root is the empty prefix. Match whole path segments; `a` is not an ancestor of `ab`. Preserve case, spaces, Chinese names, and literal percent characters. Accept only the root/current/ancestor targets generated for the current Library; reject stale or unrelated targets without changing state. Never use content URIs as path strings.
- **AC-N3 — Long paths:** Keep the current location identifiable and every ancestor reachable on narrow screens and at 200% system font scale. Prefer a horizontally scrollable breadcrumb row with bounded segment labels and accessible full labels; wrapping must not change height on scan-state updates. New breadcrumb controls also meet 48 dp touch targets.
- **AC-N4 — Scan stability:** At unchanged width, font scale, prefix, and indexed rows, transitions through `IDLE`, `SCANNING`, `CANCELLING`, `SUCCEEDED`, `CANCELLED`, and `FAILED` leave list viewport position/size and the first visible item key/offset unchanged, allowing one physical pixel of measurement rounding. Refresh/Cancel/Retry remain operable; the failure/cancellation reason remains available. A changed document index or device configuration is tested separately and is not a scan-state-only invariant.
- **AC-N5 — Row affordance:** Every file/folder clickable row is at least 48 dp high, grows for wrapped names or larger fonts, and keeps the entire row clickable. Folder/document cues and readable MD/TXT labels do not rely only on color. Opening a file passes the same document ID to the Reader.
- **AC-N6 — Preserved contracts:** Empty folders, directory-before-file ordering, independent Libraries, read-only SAF, scan cancellation, and retention of the previous index after failure remain intact. Both flavors use one implementation and the existing display/motion policy.

## NB-00 readiness record (2026-10-01)

**Requirement gate: CLOSED.** [REQ-0012 — Library Navigation (Breadcrumbs, Stable Scan Status, Row Targets)](../requirements/REQ-0012-library-navigation.md) is `READY` and carries the plan's AC-N1–AC-N6 as AC-1–AC-6. REQ-0001 stays `IN_PROGRESS`; this slice does not claim it complete.

**Baseline.** `HEAD = 5a754292c1ea02f33af0cb917f5cf24c29dbb763` with 57 uncommitted working-tree entries. The uncommitted UX-06/UX-07 delivery and the [2026-10-01 review follow-up](code-review-2026-10-01.md) fixes were left untouched; NB-01–NB-05 changed only `LibraryScreens.kt`, `LibraryViewModel.kt`, browse-level helpers, their tests, the new string resources, and paired documentation.

**Prerequisite matrix (source inspection, 2026-10-01).**

| Prerequisite | Verdict | Evidence |
| --- | --- | --- |
| UX-01 window insets and `ACTION_VIEW` scaffolding | NOT DELIVERED | No `windowInsetsPadding`/`safeDrawing` call exists in `app/src/main/kotlin` (only `enableEdgeToEdge()` in `MainActivity.kt`); `AndroidManifest.xml` has no Markdown/TXT `ACTION_VIEW` filter |
| UX-02 E-Ink color tokens and search highlights | PARTIAL | `Theme.kt` has the EINK branch and motion gating but not the UX-02 token assignments; the search highlight change is absent |
| UX-04 unified Library screen and Recent progress | NOT DELIVERED | `LibraryListScreen` still nests an unweighted `LazyColumn` inside a `Column`; rows still have no icon or progress badge |

**Dependency narrowing and rationale.** NB-01 needs no UI prerequisite and was started directly. NB-02/NB-03/NB-04 were narrowed to `LibraryBrowseScreen`, its row composables, and the browse ViewModel/state; they do not require the UX-04 `LibraryListScreen`/Recent restructure, the UX-01 insets work, or the UX-02 palette, and they introduce no new palette token or inset handling of their own. The user explicitly authorized proceeding on 2026-10-01 with those prerequisites unmet. The residual risk is a rebase of the shared `LibraryScreens.kt` when UX-01/UX-04 land; UX-01/UX-02/UX-04 remain owned by their own packages and are not claimed here. UI tasks started serially in the documented order NB-02 → NB-03 → NB-04.

**Long-path and font-scale policy.** The breadcrumb is one bounded-height, horizontally scrollable row; visual labels may be shortened but the full name stays exposed to accessibility. Every measurement claim is made at a fixed width and font scale, including a 200% system font-scale case, never as a universal height.

## Prerequisites and requirement gate

The parent UX-05 card depends on UX-01 and UX-04; UX-04 depends on UX-02. This split preserves that dependency chain. NB-00 records their actual readiness and the necessary handoff evidence. Missing prerequisites are handled in those existing packages, not silently bundled into the breadcrumb task.

[REQ-0001](../requirements/REQ-0001-folder-library.md) is currently `IN_PROGRESS` and covers browse/scanning safety. [REQ-0004](../requirements/REQ-0004-display-profiles.md) covers display policy. Before implementation, NB-00 must record a READY feature requirement or a READY scoped supplement for the new ancestor actions and acceptance above, following [AGENTS.md](../../AGENTS.md). This plan does not change REQ statuses or declare prerequisites delivered. Any narrowing of the parent's dependencies needs an explicit documented rationale and evidence.

## Implementation boundaries

Keep the existing UI → ViewModel → storage ownership. Use a small pure mapping for breadcrumb segments and a guarded ViewModel ancestor transition, reusing `browseLevel`, `parentPrefix`, and existing state. The UI receives labels and targets; it does not traverse SAF, infer paths from URIs, or alter the index.

Navigation to a different prefix resets that directory's list to the top; tapping the current segment does nothing. Recomposition and scan-status-only changes preserve scroll state. Per-folder scroll history, process-death restoration of the browsing path, and changed Reader-return restoration are outside this slice.

The status region has stable outer bounds at a given width/font scale. Size it to the supported status/action layout and keep long path/error details accessible without displacing the list. Do not assume a universal hardcoded slot height or hide failures behind ellipsis. Legitimate remeasurement after rotation or font-scale changes is allowed.

Use existing theme tokens, motion policy, resources, and test dependencies. Add new user-visible labels to Android string resources with English and Simplified Chinese counterparts; this does not authorize extracting all legacy strings. Reuse existing icon assets or small local vector drawables, with no icon-library dependency. No storage/API migration or ADR supersession is expected.

## Execution and file ownership

NB-00 → NB-01 → NB-02; NB-03 and NB-04 follow NB-00 and the external UI prerequisites; NB-05 follows NB-02/03/04. Detailed cards and stop conditions are in [the task split](library-navigation-ux-tasks.md).

NB-02/03/04 share `LibraryScreens.kt`. Implement or integrate them in the order NB-02 → NB-03 → NB-04, even though their behavior has limited logical coupling. Reserve the navigation helper/ViewModel files for NB-01, then hand them to NB-02. Shared resource and page-test changes are integrated serially. This is an ownership rule, not an instruction to spawn parallel agents.

## Verification and completion

Use the existing JUnit/Coroutines and Compose instrumented seams; add only focused tests for real path transitions, measured viewport bounds, and clickable row bounds. A fake source may supply deterministic Library metadata/progress; keep the production screen and navigation path under test rather than mirroring them in a test-only UI.

NB-05 runs the frozen JDK 17 toolchain gates and both debug flavors, then the focused browsing page suite per flavor. Record actual test counts, screenshots, failed commands and corrections, and omitted checks. Real E-Ink ghosting/refresh and TalkBack need their own evidence; software acceptance does not imply a release or completed hardware acceptance.

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
```

These are planned implementation gates, not commands executed in this documentation change. Avoid concurrent builds against the same build directories; use an isolated source copy if necessary. Put temporary fixtures, logs, and emulator copies only in the repository cwd or `/tmp`, clean them when finished, and retain only explicitly documented delivery evidence.

This planning change creates the English/Chinese plan and task files and links them from UX-05. Verify their pairing, links, references, and diff only; no product code, build, emulator, temporary data, or commit is part of this change.
