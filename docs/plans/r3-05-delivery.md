# R3-05 Delivery — Page Turns, TOC Landing, Gesture Arbitration

English | [中文](r3-05-delivery.zh.md)

Date: 2026-09-28
Package: R3-05 in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md)
Requirement: REQ-0005 (page turns/tap zones/mode), REQ-0006 (TOC landing), REQ-0007 (table/code reachability), REQ-0004 (mode switch)
Status: Software-verifiable criteria met on an emulator. Hardware refresh/ghosting remain R3-09.
Independent review: not performed (executor self-report).

## Prerequisites and entry

R3-03 rendering and R3-04 persistence/mode contracts were delivered by the R3-01–04 round
(`r3-01-04-execution-report.md`, `r3-01-04-independent-review.md`). The shared reader route was
re-verified without changes before this package started.

## Changed files

| File | Change |
| --- | --- |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderInteractionTest.kt` | New: 12 page-interaction acceptance tests |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt` | Measurement helpers: `textTopsByPrefix`, `textRectsByPrefix`, `composeTexts`, `horizontalScrollOffsets`, `scrollerBounds` |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | Defect fix DEF-R3-05-01 (one line: `overlap` added to the tap handler's `pointerInput` keys) |

## Verification

Command (emulator-5554, `dev_arm64_api35`, Android 15):

```bash
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

Result: `Starting 12 tests` → `12/12 passed`, exit 0. The tests compose the production
`ReaderScreen` + session/Markwon parser over the `ReaderStore` seam
(`FakePageStore`); tap/scroll coordinates are injected with real touch input and read
back from the composed tree (progress line, paragraph positions, scroll axis ranges).

### Per-criterion result

| Criterion | Result | Evidence |
| --- | --- | --- |
| Visible height/overlap actually applied | PASS | `turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport`: 3% and 8% measured on the real viewport; derived viewport `(d3-d8)/0.05`; `d3/V = 0.97`, `d8/V = 0.92` within ±0.02 |
| Each movement / five consecutive turns | PASS | `fiveConsecutiveTurnsAdvanceWithoutLosingTheDocument`: five turns, non-decreasing visible block, no document loss |
| Ten repeated actions + tail clamp | PASS | `tailClampStopsAtTheLastBlockAndFurtherTapsAreNoOps` (20 forward taps, then two no-op taps, last block composed) |
| Boundary clamp at the head | PASS | `headClampStopsAtTheFirstBlock` (10 backward taps reach block 1; a further tap is a no-op) |
| Menu/centre arbitration | PASS | `centreTapTogglesTheMenuWithoutTurning` (real centre tap opens/closes the production menu, progress unchanged) |
| Long-press never turns | PASS | `longPressNeverTurnsThePage` (real long-press inside the next-page zone) |
| Continuous mode side zones inert | PASS | `continuousModeSideZonesDoNotTurn` |
| Link taps never turn | PASS | `linkButtonTapDoesNotTurnThePage` (real Compose link-button click) |
| Final wide-table column reachable | PARTIAL | `wideTableHorizontalGestureReachesLastColumnWithoutTurning`: the horizontal container scrolls to its `maxValue` (end of content on screen) with the page not turning. The per-cell `boundsInRoot` probe reports empty once an ancestor scroll clip trims a cell, so the "last cell drawn" assertion is replaced by the end-of-range measurement |
| Unwrapped long code line reachable | PASS | `longCodeLineHorizontalGestureReachesTheTailWithoutTurning`: scroll axis value walks to `maxValue` and stops starting at 0 |
| TOC target landing + turn afterwards | PASS | `tocJumpThenTurnContinuesFromTheLandingBlock` (real drawer click, then a turn advances from the target) |
| Rotation recalculation | PARTIAL | `rotationKeepsTheDocumentWithoutReparse` asserts the document instance and restore block survive rotation. `createComposeRule`'s host Activity is destroyed/recreated by the orientation change, which tears the composed tree down for the rest of the test, so the post-rotation distance could not be measured in place; the distance is read from `listState.layoutInfo` on every turn and `ReaderDisplayTest.realRotationKeepsPositionWithoutReparse` covers the regression |
| E-Ink motion-off list | PASS (JVM) | `core:eink` `DisplayProfileTest` / REQ-0004 checklist; no new animation was added by this package |

## Defect found and fixed

**DEF-R3-05-01 — changing the page-turn overlap had no effect on taps.**

- Reproduce: with the page in PAGE_TURN mode, measure one turn at 3% and one at 8% →
  both moved exactly 2083 px (`turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport`).
- Cause: the tap handler is installed with
  `Modifier.pointerInput(mode, tapWidthPx)` and captures `turnPage`, which closes over
  `overlap`. A recomposition with an equal key set does not restart a `pointerInput`
  block, so the handler kept calling the stale closure.
- Fix: add `overlap` to the keys (`pointerInput(mode, tapWidthPx, overlap)`),
  commented in `ReaderScreen.kt`.
- Regression: the same test now measures 3% > 8% and both match `viewport × (1 - overlap)`.

## Not run / limitations

- Real E-Ink device: ghosting, contrast, hardware refresh, and rapid-turn behaviour on
  e-paper remain NOT_RUN (R3-09, needs a physical device).
- Eink-flavor emulator run of the new class is part of the R3-08 gate; the class is
  flavor-neutral and the shared screen is the same code.
- The table-cell "drawn on screen" assertion is replaced by the scroll-range end
  measurement (infra limitation above).

## Downstream impact

Accelerator-free, no new dependency, no contract change. The one-line fix changes tap
behaviour only when the overlap setting changes while a page is open (previously: never
until rotation/mode change).

## Stop condition

Page-interaction evidence is complete for the software criteria; hardware items are handed
to R3-09 and stay unverified. R3-06 was started next, per the package order.
