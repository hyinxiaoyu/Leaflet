# Reader Code Review — 2026-09-30

English | [中文](code-review-2026-09-30.zh.md)

## Conclusion and Scope

Five P2 findings remain OPEN in commit `5a754292c1ea02f33af0cb917f5cf24c29dbb763` (`feat(reader): freeze the reader list geometry and float the chrome (UX-06)`). Address the overlay findings before accepting UX-06. The external-link and reading-mode findings concern other production code first tracked in the same commit; they are not attributed specifically to the UX-06 layout change.

> **Follow-up status (2026-09-30):** all five findings were fixed on top of the reviewed commit; the closure regressions for CR-01/02/03 and the CR-04 device scenario are still NOT_RUN. See [Resolution](#resolution-2026-09-30-follow-up) at the end of this report.

The review focused on reader overlays, page turns, navigation, settings, and associated storage/test paths. It is not an exhaustive whole-repository audit. Static source paths are confirmed; user-visible failures have not been independently reproduced on a device. The reproduction steps below are proposed regressions, not tests already run.

All source line numbers refer to the reviewed commit, not the concurrently changing working tree. Finding IDs are local to this report and do not renumber the historical R2 defect ledger. See the [UX-06 delivery report](ux-06-delivery.md) for the executor's earlier evidence.

| ID | Priority | Finding | Requirement / task | Status |
| --- | --- | --- | --- | --- |
| CR-20260930-01 | P2 | Tail clamp can stop before footer-covered content is revealed | REQ-0005 FR-2/FR-7; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-02 | P2 | TOC landing can remain behind the title overlay | REQ-0006; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-03 | P2 | First-block search landing cannot move below the search overlay | REQ-0008; UX-06 | FIXED — device regression NOT_RUN |
| CR-20260930-04 | P2 | Package-visibility preflight can reject a launchable external URL | REQ-0009 FR-10 | FIXED — discriminating device regression NOT_RUN |
| CR-20260930-05 | P2 | A fresh E-Ink installation defaults to continuous scrolling | REQ-0005 FR-4 | FIXED — JVM regressions PASS |

## Resolution (2026-09-30, follow-up)

All five findings were addressed in the working tree on top of the reviewed commit. The overlay
findings were fixed by changing the reader's occlusion policy; the other two in the production code
they name. Device-level reproductions were still not executed (no device or emulator is attached to
this machine), so the closure regressions for CR-01/02/03 and the package-visibility scenario for
CR-04 remain NOT_RUN.

**CR-20260930-01 — tail clamp.** The list's tail gap now reserves the measured bottom chrome
(`max(32 dp, bottomChromePx + 8 dp)`) instead of a fixed 32 dp, and the turn clamp asks the list for
its real scroll boundary (`listState.canScrollForward` / `canScrollBackward`) instead of inferring it
from the last item's position against `viewportEndOffset`. At the tail the last line therefore sits
above the footer and above an open reading menu, and further turns are no-ops. The tail gap only
changes when the chrome itself changes, and it never moves any item: the padding sits after the last
item, so the body still cannot reflow on a menu toggle.
Regression: `ReaderInteractionTest.tailTurnRevealsTheLastLineAboveTheFooterAndTheOpenMenu` asserts the
lowest composed body line ends above the bottom chrome's `boundsInRoot.top`, first with the menu
closed and then with it open, and that the following turns do not move the document. The reader's two
chrome layers carry semantics tags (`ReaderChromeTags`) so the test measures the real overlay
rectangles instead of inferring them from paddings.

**CR-20260930-02 — TOC landing.** `jumpToAnchor` now closes the reading menu (and therefore the title
overlay) when it returns to the content, and the landing uses the same revealed jump as search.
Regression: `ReaderInteractionTest.tocLandingIsNotCoveredByTheTopChrome` performs a real drawer tap and
asserts the menu is gone, that the selected heading's top is below the top chrome, and that the
following page turn still keeps the body clear of it.

**CR-20260930-03 — first-block search landing.** The notices and the search bar now reserve their
measured height as list headroom (`contentPadding(top = topInsetPx)`), so the body is inset beneath
them; a match in block 0 — which no backward scroll can reveal — is visible. The document title is
deliberately excluded from that headroom and opening the search bar closes the menu, so the menu
toggle still cannot move the body. Jumps for later blocks use `scrollToItem(target, -topChromePx)`
(the documented offset convention: negative pushes the item down).
Regression: `ReaderInteractionTest.firstBlockSearchMatchStaysBelowTheSearchBar` opens search through
the real UI, types into the real field, and asserts the topmost composed body line starts below the
top chrome for the first match, a later match, the first match again, and in page-turn mode.

**CR-20260930-04 — external-link preflight.** `MainActivity.launchExternalUrl` no longer consults
`Intent.resolveActivity(packageManager)`; it launches and keeps the
`ActivityNotFoundException` recovery path, which is the documented approach for URL handoff under
package visibility. No `<queries>` declaration was added because no query is made.
Regression limitation: discriminating this fix needs a device with an installed, launchable handler
that the app cannot query, which this environment cannot provide; the finding's proposed device
scenario remains the closure step.

**CR-20260930-05 — E-Ink default reading mode.** `ReaderSettings.defaultReadingModeFor(displayMode)`
now supplies the first-run default (E-Ink → `PAGE_TURN`, Standard → `CONTINUOUS_SCROLL`);
`SettingsStore.readingMode` uses it when the key is absent, and the reader and Settings screen use it
for their pre-read initial value. An explicitly stored choice still wins, including after a
display-mode switch: the policy is that the reading mode is an independent setting, so a Standard
install switched to E-Ink keeps its stored mode until the user chooses page turns.
Regressions (JVM, executed and passing): `ReaderSettingsTest.firstRunReadingModeFollowsTheDisplayMode`
and `SettingsStoreReadingModeTest` (fresh E-Ink install, fresh Standard install, and an explicit
choice surviving a re-read and a display-mode switch).

Verification for this follow-up (2026-09-30): `./gradlew test --rerun-tasks` executed 694 JVM cases
(307 unique methods) with 0 failures, 0 errors and 0 skipped; `lint` and `ktlintCheck --rerun-tasks`
passed; `:app:assembleFullDebug` and `:app:assembleEinkDebug` produced `app-full-debug.apk`
(29,206,813 B) and `app-eink-debug.apk` (28,488,137 B); both flavors' androidTest source sets
compile. The instrumented tests themselves — and with them every device scenario above — are NOT_RUN:
no device or emulator is attached (`adb devices` is empty and no AVD is installed). See the delivery
notes in [ux-06-delivery.md](ux-06-delivery.md).

## CR-20260930-01 — Tail Clamp Ignores Footer Occlusion

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:384–387`, with fixed tail padding at line 444.
- **Trigger and impact:** In `PAGE_TURN`, the last item's bottom can lie inside the full list viewport but behind the progress footer. `atBottom` then rejects the next turn, leaving the last lines obscured even when unused tail-padding scroll space remains. An expanded menu can additionally exceed the fixed `32.dp` tail gap.
- **Cause:** Turn distance subtracts `bottomChromePx`, but the tail guard compares against `info.viewportEndOffset` without subtracting occlusion or considering remaining scroll space.
- **Minimal direction:** Use the actual list scroll boundary and ensure the final content can be revealed above the footer. Preserve fixed list geometry when accounting for expanded chrome; do not introduce menu-dependent list resizing.
- **Regression and closure:** Choose document length/typography so the last item initially ends beneath the footer with scroll space remaining. Turn forward and assert the final text's actual bounds are above the footer, then verify additional turns are no-ops. Repeat with the menu open and closed; asserting that the last block is merely composed is insufficient.

## CR-20260930-02 — TOC Landing Remains Under the Title

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:395–403`; title visibility at lines 589–595.
- **Trigger and impact:** Open the reading menu, open the table of contents (TOC), and select a heading sufficiently far from the document end. The selected heading lands at the list's top, behind the still-visible document-title overlay.
- **Cause:** `jumpToAnchor()` closes `tocOpen` but leaves `menuVisible` true. It calls `scrollToItem(target)` without the top-chrome reveal used by search and same-document link jumps.
- **Minimal direction:** Apply a consistent top-occlusion policy to TOC landings, or explicitly dismiss the occluding menu/title when returning to content. Retain immediate scrolling and existing position contracts.
- **Regression and closure:** Assert the selected heading's visible bounds are below the remaining top chrome after a real TOC tap. Include notices and repeat the subsequent page turn; checking only `firstVisibleItemIndex` does not prove visibility.

## CR-20260930-03 — First-block Search Reveal Is Clamped Away

- **Source:** `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt:196–198`, search landing at lines 349–356, and list padding at line 444.
- **Trigger and impact:** Search for text at the beginning of block 0. The current match can remain hidden beneath the floating search bar, despite the search count indicating a hit.
- **Cause:** After `scrollToItem(0)`, `revealBelowTopChrome()` requests a negative `scrollBy`. The list is already at its head and has no top content padding, so it cannot move the first block down by the required amount.
- **Minimal direction:** Provide a reveal strategy that works at document start, such as fixed top headroom compatible with the frozen-geometry requirement. A negative scroll alone cannot solve this boundary case.
- **Regression and closure:** Use a heading-less document whose first line contains the query. Open search through the real UI and assert the actual matched text is below the search bar. Cover both reading modes and a jump back from a later match; do not accept a model match or count as proof of visibility.

## CR-20260930-04 — External-link Preflight Can Misreport No Handler

- **Source:** `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt:122–129`; `app/src/main/AndroidManifest.xml` has no corresponding `<queries>` declaration.
- **Trigger and impact:** On Android 11+ with the app targeting API 35, a URL handler that is not visible to package queries can be installed and launchable. The preflight can nevertheless return null, reporting no external app and preventing the URL launch.
- **Cause:** `intent.resolveActivity(packageManager)` is treated as a prerequisite for `startActivity()`. Android package visibility can filter queries while activity launch is still permitted. The [Android official guidance](https://developer.android.com/training/package-visibility/use-cases#open-urls) recommends launching and catching `ActivityNotFoundException`, or declaring appropriate queries if preflight is needed.
- **Minimal direction:** Remove the mandatory resolution preflight and retain the existing `ActivityNotFoundException` recovery path. If preflight is retained, declare the intended query coverage.
- **Regression and closure:** Exercise the real MainActivity launch path with an installed URL handler excluded from package-query visibility, and separately with no handler. Verify successful handoff versus the recoverable notice. The current callback-based link tests do not cover PackageManager or activity launch.

## CR-20260930-05 — E-Ink Default Reading Mode Is Continuous

- **Source:** `app/src/main/kotlin/io/leaflet/reader/SettingsStore.kt:119–122`; `core/storage/src/main/kotlin/io/leaflet/storage/prefs/ReaderSettings.kt:53–55`; mode consumption in `ReaderScreen.kt:240–246`.
- **Trigger and impact:** Open a document on a fresh `eink` installation with no `reading_mode` preference. It enters `CONTINUOUS_SCROLL`; side-zone taps are inert and vertical finger scrolling is enabled instead of the required E-Ink page-turn default.
- **Cause:** Missing preferences always use `ReaderSettings.READING_MODE_DEFAULT`, which is `CONTINUOUS_SCROLL`. The store's `defaultMode` does not participate in the reading-mode default.
- **Minimal direction:** For an absent user choice, derive the default from the effective display mode: EINK → PAGE_TURN, STANDARD → CONTINUOUS_SCROLL. Preserve an explicitly stored reading-mode choice.
- **Regression and closure:** Verify fresh defaults for both flavors and actual side-tap/drag behavior. Verify that an explicit choice survives reopen and mode changes, and document the default policy for a Standard flavor switched to EINK before any reading-mode choice.

## Verification and Boundaries

The reviewed commit was extracted with `git archive` into an isolated `/tmp/leaflet-review.*` directory so concurrent source edits could not affect its result. The environment used macOS ARM64, JDK 17.0.9, Gradle Wrapper 8.13, and the installed Android SDK at `/opt/homebrew/share/android-commandlinetools`. Existing Gradle dependencies were copied into the temporary user home; the check used offline mode and disabled build-cache reuse.

```bash
./gradlew --offline --no-daemon --no-build-cache \
  :core:reader:testDebugUnitTest \
  :core:storage:testDebugUnitTest \
  :app:testFullDebugUnitTest \
  :app:testEinkDebugUnitTest
```

| Check | Result and limit |
| --- | --- |
| Isolated-commit JVM check | PASS; exit 0; 249 executed test cases across 24 XML reports, 0 failures, 0 errors. This is not a deduplicated whole-project test count. |
| Initial working-tree check | FAILED; concurrently added, untracked `TableGrid.kt` could not resolve `constrainWidth` / `constrainHeight`. That file was absent from the reviewed commit; its failure is not a committed-code finding. |
| New device regressions for these findings | NOT_RUN; proposed scenarios above have not been executed. |
| E-Ink hardware, layout-inspector evidence, and screenshots | NOT_RUN in this review. |
| Lint, ktlint, APK assembly, and full-suite acceptance | Not rerun by this review; prior executor results remain historical evidence only. |

Existing JVM tests passing does not close the findings. Closure requires the discriminating regressions above and applicable dual-flavor checks. This review and its documentation do not authorize product implementation or change requirement/task status to DONE.

## Artifacts and Cleanup

- Temporary commit snapshots, copied Gradle caches, logs, and isolated test reports were created only under `/tmp/leaflet-review.*`; those directories were deleted after verification.
- The initial working-tree check updated existing standard Gradle build outputs under `/Users/clover/source_dev/Leaflet/app/build/` and `/Users/clover/source_dev/Leaflet/core/*/build/`. They were retained to avoid interfering with concurrent development; no unrelated source or pre-existing artifact was deleted.
- This documentation follow-up adds this report and its complete Chinese pair, and links both from the UX-06 delivery-report pair. It makes no product-source or test changes and creates no additional temporary files.
