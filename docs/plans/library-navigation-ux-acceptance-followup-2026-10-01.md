# Library Navigation UX — Acceptance Follow-up and Fix (2026-10-01)

English | [中文](library-navigation-ux-acceptance-followup-2026-10-01.zh.md)

ID: `NAV-FOLLOWUP-20261001`
Verdict: **FOCUSED FIX VERIFIED; full page acceptance remains pending.**
Authorization: the user requested acceptance and direct implementation when checks fail.
Previous record: [NAV-ACC-20261001](library-navigation-ux-acceptance-2026-10-01.md), preserved as a historical failing snapshot.
Current evidence: [results and source hashes](review-evidence/2026-10-01-navigation-followup/results.json).

## Recheck and scope

At entry, the workspace already contained the `AncestorRequest` protocol and idle/inactive/terminal cancellation guards. Fresh direct compilation and execution confirmed 24 existing navigation/cancel/selection regressions and the two independent acceptance probes now pass. Those production ViewModel changes were preserved and are not claimed as newly authored by this follow-up.

The remaining acceptance review traced the actual page-to-ViewModel callback, then fixed the failing page binding and its regression coverage. No dependency, database, storage contract, scan algorithm, or unrelated reader change was introduced.

## Confirmed residual defect and repair

`LibraryBrowseScreen` declares `val state by collectAsState()`. Its breadcrumb callback constructed `AncestorRequest` by reading `state.libraryId` and `state.prefix` when the queued callback executed. These delegated reads return the latest state, so a Library-A tap could be relabeled as Library B even though `openAncestor` correctly rejects an explicit stale-A request.

A freshly compiled production callback was invoked with an advancing `State` and the real ViewModel. Before the fix, B moved from `Docs/Notes` to its root; the assertion failed. This is execution of the actual compiler-generated callback, not a test-only reimplementation. It does not constitute Android view or pointer-dispatch testing. [Before output](review-evidence/2026-10-01-navigation-followup/callback-before.txt).

The screen now captures plain `renderedLibraryId` and `renderedPrefix` values during rendering, uses that prefix for the rendered segments, and captures those immutable strings in the callback. Both Full and Eink compiled callbacks retain A's origin and leave B unchanged. [Full output](review-evidence/2026-10-01-navigation-followup/full-callback-after.txt), [Eink output](review-evidence/2026-10-01-navigation-followup/eink-callback-after.txt).

The page-test cue/type selectors were also corrected to use the unmerged semantics tree: clickable rows merge child semantics, so a merged row carrying the description is not its own tagged ancestor. [Pinned Foundation 1.7.5 primary evidence](review-evidence/2026-10-01-navigation-followup/clickable-semantics-primary.txt) confirms merging is enabled. The selector remains constrained to its row; no assertion was weakened.

## Changed files

| File | This follow-up's change |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryScreens.kt` | Capture rendered Library/prefix values before building the queued breadcrumb callback |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` | Unmerged row-child queries; new retained root-click/prefix-change regression; 12 page methods total |
| `CHANGELOG.md` / `.zh.md` | Describe the user-visible queued-tap correction |
| `docs/requirements/REQ-0012-library-navigation.md` / `.zh.md` | Append scoped verification evidence without marking the overall requirement DONE |
| This report pair and `review-evidence/2026-10-01-navigation-followup/` | New results and reproducible probes, separate from prior acceptance/delivery reports |

The new page regression retains the real root `OnClick` action, changes the directory, invokes the retained action, and requires the new state to remain unchanged. It compiles but has not executed on Android.

## Actual verification

Environment: macOS ARM64, JDK 17.0.9, Kotlin/Compose compiler 2.0.21, pinned cached dependencies. Navigation components and Theme sources were freshly compiled using each flavor's existing resolved lint-model dependency/R jars. Other app/core jars are link dependencies; this is not a full app build or APK assembly.

| Check | Result |
| --- | --- |
| Existing JVM cases: Breadcrumb 8, Ancestor 11, Selection 2, Cancel 3 | PASS — 24/24 |
| Prior independent ViewModel probes | PASS — 2/2; [combined output](review-evidence/2026-10-01-navigation-followup/focused-jvm.txt) |
| Production UI callback before repair | FAIL — 1/1, B was incorrectly moved to root |
| Production UI callback after repair | PASS — 1/1 in Full and 1/1 in Eink; 28 final JVM executions total, 27 distinct methods |
| Navigation components and current page suite, both flavors | PASS — four direct Kotlin/Compose compilations; [Full main](review-evidence/2026-10-01-navigation-followup/full-main-compile.txt), [Full page](review-evidence/2026-10-01-navigation-followup/full-page-compile.txt), [Eink main](review-evidence/2026-10-01-navigation-followup/eink-main-compile.txt), [Eink page](review-evidence/2026-10-01-navigation-followup/eink-page-compile.txt) |
| Changed-source ktlint 1.0.1 | PASS — the two changed Kotlin files; app's Compose function-naming exception retained; [output](review-evidence/2026-10-01-navigation-followup/changed-ktlint.txt) |
| Standard Gradle test/build/lint/ktlint gates | BLOCKED before task execution — `FileLockContentionHandler` socket `Operation not permitted`; [output](review-evidence/2026-10-01-navigation-followup/gradle-blocked.txt) |
| Android execution of the 12 page methods | NOT_RUN — ADB listener `Operation not permitted`; [output](review-evidence/2026-10-01-navigation-followup/adb-blocked.txt) |
| Real E-Ink / TalkBack | NOT_RUN |

The direct compiler setup initially parsed colon-separated jar lists incorrectly; this was corrected before compilation. The standalone formatter setup initially lacked logging dependencies and selected an incompatible SLF4J API; after resolving the cached CLI dependencies, ktlint returned exit 0. These setup failures were not treated as product failures or successful gates. Existing compiler deprecation warnings remain.

## Evidence and completion boundary

The original two failing ViewModel scenarios are now verified passing, and the remaining production page-callback defect is repaired with before/after JVM execution proof. The historical failed report is not overwritten. NB-05 and complete REQ-0012 acceptance remain pending actual page measurements, all project gates, and separate hardware/accessibility evidence; compilation alone does not confirm row sizes, scan viewport stability, or gesture behavior.

A repeat of the ordinary and independent JVM checks is available with the retained self-cleaning runner:

```bash
python3 docs/plans/review-evidence/2026-10-01-navigation-followup/run_navigation_checks.py   --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

The [compiled callback probe](review-evidence/2026-10-01-navigation-followup/CompiledBreadcrumbCallbackProbe.kt) identifies the compiler-generated callback by parameter shape and invokes it reflectively to avoid requiring an Android host. That reflection is confined to retained diagnostics, not product code or regular app tests. It requires freshly compiled navigation components; it does not assert stable compiler-private method names.

When the environment permits sockets, run the complete project gates and the page suite for each flavor:

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
./gradlew :app:connectedEinkDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
```

Temporary compiler outputs, cached dependencies, formatter configuration, source backup, and logs were created under `/tmp/leaflet-navigation-followup.2xdLrQ` and removed after evidence capture. The JVM runner cleaned its own `/tmp` scratch directories. The ADB attempt used the task's TMPDIR so it did not append to the shared system log. Valuable evidence is intentionally retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-navigation-followup/`. No APK, screenshot, release, or commit is claimed.
