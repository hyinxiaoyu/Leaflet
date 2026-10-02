# Library Navigation UX — Independent Acceptance (2026-10-01)

English | [中文](library-navigation-ux-acceptance-2026-10-01.zh.md)

Acceptance ID: `NAV-ACC-20261001`
Type: independently executed, scope-limited acceptance; separate from implementation delivery/self-report.
Verdict: **NOT_ACCEPTED — two production ViewModel edge cases reproduced.**
Scope: UX-05 / NB-00–NB-05 and [REQ-0012](../requirements/REQ-0012-library-navigation.md).
Evidence: [results and source hashes](review-evidence/2026-10-01-navigation-acceptance/results.json).

## Evidence classification

| Category | Meaning in this report |
| --- | --- |
| CONFIRMED_THIS_RUN | Actual direct compiler/JUnit execution or explicitly identified static/primary-API inspection in this run |
| HISTORICAL_SELF_REPORT | Statements in the existing delivery/plan/task records; not independently rerun here |
| NOT_RUN / BLOCKED | Missing execution evidence or a specific environment restriction; neither is a pass |

The [existing delivery](library-navigation-ux-delivery.md), [plan](library-navigation-ux-plan.md), [task cards](library-navigation-ux-tasks.md), and earlier review reports remain unchanged. Their “820 cases / 341 methods”, builds, and lint statements are historical records, not this run's results. This report does not revoke historical acceptance of unrelated reader changes or infer that earlier commands never ran.

The delivered/software-verified status in those records is not enough to close NB-05: their own page matrix is NOT_RUN and ktlint was not fully green. This independent verdict uses the current scoped source snapshot and its explicit limitations.

## Checks actually executed

Environment: macOS ARM64; JDK 17.0.9; cached Kotlin compiler 2.0.21, JUnit 4.13.2, Lifecycle 2.8.7, Coroutines 1.10.2. Production `LibraryViewModel`, `Breadcrumbs`, `BrowseLevel`, and required model sources were freshly compiled unchanged in `/tmp`. Existing core/storage bytecode was used only to link types; the ViewModels under test use `scanner = null`. No Room/SAF/scanner execution is claimed.

| Check | Result | Evidence and boundary |
| --- | --- | --- |
| Fresh direct Kotlin compilation | PASS | Current production/helper/model source plus the focused tests; not an Android app build |
| `BreadcrumbTest` (8), `LibraryAncestorNavigationTest` (8), `LibraryBrowseSelectionTest` (2) | PASS — 18/18 | [JUnit output](review-evidence/2026-10-01-navigation-acceptance/existing-junit.txt); common JVM code, not four flavor executions |
| Independent `NavigationAcceptanceProbe` | FAIL — 2/2 | [Probe source](review-evidence/2026-10-01-navigation-acceptance/NavigationAcceptanceProbe.kt), [JUnit output](review-evidence/2026-10-01-navigation-acceptance/independent-junit.txt) |
| Seven new breadcrumb/row/scan resource keys | PASS, static only | XML parsed; each default key has a Simplified Chinese counterpart; not full locale/TalkBack validation |
| Focused Gradle test invocation | BLOCKED before tasks | [Startup error](review-evidence/2026-10-01-navigation-acceptance/gradle-blocked.txt): `FileLockContentionHandler` → `java.net.SocketException: Operation not permitted` |
| Android page tests / current APK builds / lint / ktlint | NOT_RUN | Gradle startup and/or ADB socket restrictions; old outputs were not relabeled as current results |
| Real E-Ink refresh/ghosting and TalkBack | NOT_RUN | No hardware/accessibility measurement in this run |

ADB also failed to install its local listener with `Operation not permitted` ([this run's startup output](review-evidence/2026-10-01-navigation-acceptance/adb-blocked.txt)). An installed `dev_arm64_api35` AVD was listed, so this run records **sandbox access blocked**, not “no emulator exists”. There is no request to bypass that restriction.

## Confirmed findings

### NAV-ACC-01 — Stale breadcrumb provenance is lost (P1)

**Confirmed by actual production ViewModel execution.** A Library-A root target is generated, Library B is opened at `Docs/Notes`, and the queued A target is passed to `openAncestor`. Expected: B's state is unchanged. Actual: B's prefix becomes the empty root. This violates REQ-0012 FR-2 / AC-2.

`openAncestor(target: String)` in `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` captures the Library identity only when the method is called. Its atomic-update check protects against a switch during that call, not an action generated before the switch. The UI callback supplies only the path; the root string is valid in every Library. The existing replaced-Library test sends `Docs` while B is at its root and therefore only proves that a non-ancestor is rejected.

Repair boundary: NB-01/NB-02 must carry the originating Library and prefix/revision with the breadcrumb request, validate that context against current state, and add stale-root and same-path/different-Library cases. The JVM reproduction simulates a queued UI request; Android pointer delivery itself was not run.

### NAV-ACC-02 — Cancel can publish CANCELLING without a scan owner (P2)

**Confirmed by actual production ViewModel execution.** After opening a Library with no active scan, `cancelRefresh()` changes `IDLE` to `CANCELLING`. `refreshJob` is absent, so no cancelled job exists to publish a terminal state. This is a recovery/state-machine defect relevant to NB-03 and AC-4; the full active-scanner matrix remains unexecuted.

The method in `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryViewModel.kt` cancels the optional job and unconditionally overwrites progress. In production, a delayed Cancel callback after a scan settles can hit the same missing/inactive-owner branch; a stuck Cancel display is a source-based inference, not a device observation.

Repair boundary: ignore cancellation when there is no active, current scan or the progress is already terminal; preserve completed results and verify cancellation ownership/races. Add a completed-scan/late-Cancel case using the real scanner seam before closing AC-4.

## Page-test readiness and unverified behavior

Static review of `app/src/androidTest/kotlin/io/leaflet/reader/ui/LibraryBrowsePageTest.kt` found blockers to treating its six compiled methods as acceptance evidence:

- Single-node `onNodeWithContentDescription(Document)` and `onNodeWithText(MD)` queries are used in a fixture containing many document/MD rows. The queries are not uniquely scoped; check them on a device and use row-scoped or explicit multi-node assertions as appropriate.
- The scan lister has a single `CompletableDeferred` gate. Once released for the successful scan it cannot hold the retry scan in SCANNING; waiting for that transient phase can race. CANCELLING is not separately measured. Use resettable/per-scan control and measure each required phase.
- The fixture exercises short names and two nested directory levels. It does not demonstrate ten-level/narrow-screen reachability, long Chinese/English names at 200% font scale, empty-folder status stability, or both Standard/E-Ink runtime modes. Running both distribution flavors is not proof of both runtime policies.
- A different-prefix list reset has no explicit `LazyListState`/prefix-key handling in the reviewed screen. Whether retained scroll position violates FR-6 needs a scrolled-folder → ancestor page reproduction; this report does not present that as a device-confirmed failure.

An initial concern about “no click action followed by `performClick`” was **excluded**. The pinned Android ui-test 1.7.5 bytecode delegates `performClick` to `performTouchInput`, so a physical tap on a non-action node is legitimate. [Primary cached API evidence](review-evidence/2026-10-01-navigation-acceptance/compose-click-primary-evidence.txt) prevents this from becoming a false finding.

## Acceptance matrix

| Criterion | Verdict | What is and is not proved |
| --- | --- | --- |
| AC-1 ancestor navigation/Back | PARTIAL | Basic ViewModel transitions pass; real taps and root-exit behavior NOT_RUN |
| AC-2 path identity/stale rejection | FAIL | Segment matching passes; stale-root request changes the newly selected Library |
| AC-3 long paths / 200% scale / breadcrumb targets | NOT_RUN | Ten-segment generation is unit-tested; actual reachability/bounds are not |
| AC-4 scan stability/recovery | FAIL / PARTIAL | Idle cancellation defect reproduced; real scan phases and pixel/scroll invariants NOT_RUN |
| AC-5 rows, names, type cues, Reader handoff | NOT_RUN | Source has minimum-size modifiers and resources; actual clickable bounds/wrapping/callback taps are unmeasured |
| AC-6 preserved contracts | PARTIAL | Later-Library selection regressions pass; full storage cancellation/index retention and folder scroll-reset evidence not rerun |
| AC-7 full gates | BLOCKED | Direct scoped JVM checks executed; Gradle/build/lint/ktlint not executable in this sandbox |

NB-05 cannot be accepted from these results. Correct the two confirmed state defects, make the page suite deterministic/adequately scoped, then execute the missing UI and full gates. No production fix was made by this validation-only task.

## Recheck and retained evidence

The evidence runner uses the frozen cached compiler/dependencies and a compiled core/storage JAR for linking. It freshly compiles production source and the probes, runs the three existing classes and the independent class, and returns nonzero when assertions fail. It uses a self-cleaning `/tmp` directory and does not modify app source. After changing the request protocol, adapt probe call sites while retaining the stale-origin scenario.

```bash
python3 docs/plans/review-evidence/2026-10-01-navigation-acceptance/run_navigation_checks.py   --java /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java
```

Then, in an environment that permits Gradle/ADB sockets, run the project JVM/lint/ktlint and dual-debug-build gates, followed by the corrected page suite in both flavors:

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:connectedFullDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
./gradlew :app:connectedEinkDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.LibraryBrowsePageTest
```

The initial focused Gradle attempt used JDK 17, isolated `GRADLE_USER_HOME`, `--offline --no-daemon`, in-process Kotlin, a 2 GiB heap, and one worker; it failed before any task/test ran. No successful Gradle command is claimed here.

This task creates only this paired independent report and its retained evidence directory. The original delivery/plan/task/review records, regular app tests, and product code are unchanged. Valuable evidence is intentionally retained at `/Users/clover/source_dev/Leaflet/docs/plans/review-evidence/2026-10-01-navigation-acceptance/`; it includes two reproduction probes, a recheck runner, raw outcomes, the blocked-build error, cached API evidence, and source/dependency hashes. Temporary source/cache copies and compiled outputs under `/tmp/leaflet-navigation-acceptance.G9Ad29` were deleted after recording the results. Only this run's PID 20796 block was removed from the shared ADB startup log; its 15 pre-existing blocks were preserved at `/private/var/folders/kg/sjpsnph952bczrr6s_t90c3m0000gn/T/adb.501.log` because they belong to other sessions. The retained recheck runner was executed successfully as a reproduction: 18 existing tests passed and the two acceptance assertions failed again ([output](review-evidence/2026-10-01-navigation-acceptance/recheck-runner.txt)), returning exit code 1. No emulator was started and no commit was made.
