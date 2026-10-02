# UX-06 Delivery — Non-Reflowing Reader Overlay Architecture

English | [中文](ux-06-delivery.zh.md)

Date: 2026-09-30
Package: UX-06 in [UI/UX Optimization Tasks](ui-ux-optimization-tasks.md)
Plan: [Leaflet UI/UX Audit and Optimization Plan](ui-ux-audit-and-optimization-plan.md) (ISS-018, ISS-019, ISS-020, ISS-022)
Requirement: REQ-0005 (page turns, tap zones, visible height), REQ-0004 (motion policy), REQ-0003 (position preservation)
Status: Code and JVM/gate verification complete. The new instrumented acceptance tests compile but are NOT RUN (no device or emulator is attached to this machine); E-Ink hardware remains NOT RUN.
Independent review: not performed (executor self-report).

## Prerequisites and entry

The R3-08 software candidate (295 unique JVM tests, 75 instrumented tests per flavor) is the
baseline. This package changes the reader layout only: the reader route, session, parser,
position contracts, storage seams, and the page-turn distance formula are untouched.

## What changed

`ReaderContent` used to stack the document title, the notices, the search bar, the
`LazyColumn`, and the progress line/reading menu inside one in-flow `Column`. Every toggle of
`menuVisible` or `searchOpen` therefore shrank or grew the `Box(weight = 1f)` that holds the
list, which re-laid out the whole body — the E-Ink reflow and flash of ISS-018, with the title
permanently spending ~64 dp of viewport (ISS-019).

The reader is now one list plus an overlay layer:

1. **Frozen list geometry.** The `LazyColumn` is the only measured child of the root
   `Column(fillMaxSize().padding(horizontal = pageMarginDp, vertical = 16.dp))`. It keeps
   `fillMaxSize()` and a body inset — the headroom and chrome-aware tail gap described in the
   follow-up review below, which replaced the first cut's constant `contentPadding(bottom = 32.dp)`;
   nothing about the list's own geometry depends on chrome visibility.
2. **Overlay chrome.** Inside the wrapping `Box`, declared after the list so it draws on top:
   a top layer (document title — only while the reading menu is open — plus the stale,
   degraded, and link notices and the search bar) and a bottom layer (a discreet anchored
   progress footer over a 1 dp divider, plus the existing reading menu when open). All layers
   are opaque `surface` and static: no animation, transparency, or ripple (REQ-0004).
3. **Visible-height page turns.** The overlay no longer shrinks the viewport, so the measured
   top/bottom chrome heights are subtracted from the turn distance through the existing
   `PageTurn.visibleHeightPx` (REQ-0005 FR-2). Turns therefore still move
   `visibleHeight − overlap` and never skip content hidden behind the menu or notices.
4. **Jumps stay visible.** A search or same-document anchor jump lands on the first item of the
   list, which is now under the floating bar. `revealBelowTopChrome()` shifts such a jump down
   by the measured top chrome so the landing block is readable (the old in-flow bar used to
   provide this for free).
5. **First-run tap-zone onboarding (ISS-020).** A one-time, opaque, non-animated overlay
   illustrates the three zones with the reader's real thirds (previous page / reading menu /
   next page) and a mode-aware subtitle, dismissed by one tap anywhere or by "Got it". The
   whole overlay consumes the tap, so the dismissing touch can never turn a page. The
   dismissal is persisted (`tapZonesHintSeen`) and the decision is the pure
   `TapZones.shouldShowHint`, so the hint appears at most once per install and never flashes
   before the stored value is read.

Position semantics are untouched: `restoreBlock`, TOC/anchor jumps, search jumps, the
throttled save observer, and the typography `keepBlock` seam still address plain block indices.

## Changed files

| File | Change |
| --- | --- |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | `ReaderContent` restructured to frozen-list + overlay chrome; visible-height turn distance; `revealBelowTopChrome`; new `TapZoneHintOverlay`/`HintZone` |
| `app/src/main/kotlin/io/leaflet/reader/SettingsStore.kt` | `tapZonesHintSeen` flow + `setTapZonesHintSeen` (DataStore) |
| `app/src/main/kotlin/io/leaflet/reader/MainActivity.kt` | `SettingsViewModel.tapZonesHintSeen` / `dismissTapZonesHint()` |
| `core/storage/src/main/kotlin/io/leaflet/storage/prefs/ReaderSettings.kt` | `TAP_ZONES_HINT_SEEN_DEFAULT` |
| `core/reader/src/main/kotlin/io/leaflet/reader/TapZones.kt` | `TapZones.shouldShowHint` (pure first-run decision) |
| `core/reader/src/test/kotlin/io/leaflet/reader/PageTurnTest.kt` | New `TapZonesTest.firstRunHintShowsOnlyWhenUnseenAndNotYetDismissed` |
| `app/src/main/res/values/strings.xml` | Seven first-run hint strings |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/ReaderInteractionTest.kt` | New UX-06 acceptance tests (overlay geometry, transient title, onboarding show/dismiss/persist, hint consumes the first tap): the class goes from 12 to 16 tests |
| `app/src/androidTest/kotlin/io/leaflet/reader/ui/PageViews.kt` | `pageSettingsViewModel(tapZonesHintSeen = true)` marks the existing page suites as post-onboarding |

## Verification

Run from the repository root on 2026-09-30:

```bash
./gradlew test lint ktlintCheck
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew :app:compileFullDebugAndroidTestKotlin :app:compileEinkDebugAndroidTestKotlin
```

| Check | Result |
| --- | --- |
| `test` (JVM, all variants) | PASS — 656 executed test cases, 297 unique test methods, 0 failures, 0 errors, 0 skipped (baseline was 296 unique; this package adds one) |
| `lint` | PASS |
| `ktlintCheck` | PASS |
| `:app:assembleFullDebug` / `:app:assembleEinkDebug` | PASS — `app-full-debug.apk` (28,382,037 B), `app-eink-debug.apk` (28,784,193 B) |
| androidTest compilation (both flavors) | PASS |
| Instrumented UX-06 acceptance tests | NOT RUN — no device/emulator attached (`adb devices` empty, no AVD installed) |
| E-Ink hardware (ghosting, refresh, contrast) | NOT RUN — R3-09 hardware matrix |

Follow-up command for the instrumented evidence once a device is available:

```bash
./gradlew :app:connectedEinkDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderInteractionTest
```

## Per-criterion result

| UX-06 acceptance criterion | Status | Evidence |
| --- | --- | --- |
| Toggling the menu or opening the search bar produces zero resize events or layout passes on the reading `LazyColumn` | PENDING DEVICE RUN | Structure: the list is the only measured child of the root `Column` and all chrome is a sibling overlay inside a wrapping `Box`; `ReaderInteractionTest.menuAndSearchOverlaysNeverResizeTheReadingList` asserts the list rect is unchanged (<= 0.5 px) across menu-open, search-open, and menu-close |
| On E-Ink, opening the menu shows no content reflow or text jumping | PENDING DEVICE RUN | Same structure (no measured geometry change); E-Ink waveform/ghosting behaviour itself is R3-09 hardware |
| Document title no longer permanently consumes reading space | PENDING DEVICE RUN | Title renders in the top overlay only while the menu is open; `ReaderInteractionTest.documentTitleIsTransientChromeNotAPermanentHeader` asserts it is absent while immersive and present with the menu |
| Tap-zone onboarding appears once on first launch and dismisses cleanly | PENDING DEVICE RUN | `TapZonesTest.firstRunHintShowsOnlyWhenUnseenAndNotYetDismissed` (JVM, PASS) plus `ReaderInteractionTest.firstRunTapZoneHintAppearsDismissesAndIsPersisted` and `tapZoneHintConsumesTheFirstTapWithoutTurning` |
| Page turns keep using the actual visible height (REQ-0005 FR-2) | PASS (JVM) | `PageTurn.visibleHeightPx` is now wired to the measured chrome; `turnDistanceMatchesTheConfiguredOverlapOnTheRealViewport` still derives the effective viewport from two measured turns |

## Limitations and follow-ups

- The overlay occludes body content in two places. The notices and the search bar reserve their
  measured height as list headroom, so they never hide body text (including a match in block 0,
  which no scroll can reveal — CR-20260930-03); the reading menu and the title, which does not
  reserve headroom, cover the bottom/top while they are open, and the turn distance accounts for
  them. The tail gap reserves the measured bottom chrome, so the last line can always be scrolled
  clear of the footer and of an open menu (CR-20260930-01). Not yet measured on hardware.
- The shipped guarantee is that the reading list's measured geometry cannot change when chrome
  toggles (asserted on the composed page). A strict "zero layout passes" claim would need
  layout-inspector accounting, which this environment could not produce.
- The bottom progress footer passes touches through to the tap zones (a tap on its right third
  turns the page). Accepted for now; a follow-up could absorb touches on the footer.
- System insets are still not applied inside the reader (status/navigation bar overlap is
  ISS-001/ISS-002, owned by UX-01). The overlay bars inherit that same behaviour as the
  previous in-flow chrome — no regression, but no fix either.
- `values-zh` does not exist yet, so the seven new hint strings are English-only; UX-10 owns the
  bilingual string resources.
- Emulator screenshots and layout-inspector evidence required by the UX-06 stop condition were
  not produced on this machine.

## Follow-up Code Review — 2026-09-30

The [reader code review](code-review-2026-09-30.md) records five P2 findings against commit
`5a754292c1ea02f33af0cb917f5cf24c29dbb763`: premature tail clamp behind the footer, TOC landing
under the title, first-block search occlusion, external-link package-visibility preflight, and
the E-Ink continuous-scroll default. The first three affect UX-06 acceptance; the last two concern
other source first tracked in the same commit. Static paths are confirmed, and the isolated targeted
JVM check passed 249 executed cases with no failures or errors.

**All five were fixed on top of that commit** (see the resolution section of the review report):

- The list's body inset is now `contentPadding(top = notices/search headroom, bottom =
  max(32 dp, measured bottom chrome + 8 dp))`, and the turn clamp uses the list's real scroll
  boundary (`canScrollForward` / `canScrollBackward`, CR-01). Neither value moves any item: the top
  padding only provides scroll room at the document head, the bottom padding sits after the last
  item, and the reading menu is deliberately excluded from the headroom, so the menu toggle still
  cannot reflow the body. The reader and Settings screen also take their pre-read reading mode from
  the display mode (CR-05), and the external-link handoff no longer gates on
  `Intent.resolveActivity` (CR-04).
- New regressions: JVM `SettingsStoreReadingModeTest` (3 tests, executed green) and
  `ReaderSettingsTest.firstRunReadingModeFollowsTheDisplayMode`; device tests
  `ReaderInteractionTest.tailTurnRevealsTheLastLineAboveTheFooterAndTheOpenMenu`,
  `tocLandingIsNotCoveredByTheTopChrome`, and `firstBlockSearchMatchStaysBelowTheSearchBar`, which
  measure the chrome's real `boundsInRoot` through the `ReaderChromeTags` semantics tags. The device
  tests compile for both flavors but were NOT RUN (no device/emulator), and the CR-04 discriminating
  scenario still needs a device with a query-invisible handler.
- Where this report says "zero reflow on toggle", read it as: the reading list's measured geometry and
  the body's item positions cannot change on a menu toggle; opening the search bar (or a notice
  appearing) adds measured headroom, which is a deliberate one-time inset that keeps content visible
  instead of hidden.
