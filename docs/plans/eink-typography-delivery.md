# E-Ink Typography, Layout and Visual Policy — Delivery

English | [中文](eink-typography-delivery.zh.md)

Date: 2026-10-02
Scope: audit of the reader and library surfaces against the E-Ink rules in `AGENTS.md` and the DisplayProfile/MotionPolicy architecture, plus concrete typography, page-layout and visual-policy improvements.
Requirement: REQ-0007 (FR-13–FR-15, AC-10/AC-11), REQ-0004 (display-policy follow-up), REQ-0005 (unchanged)
Status: Implemented and statically reviewed only. **Nothing was built, tested, linted or run on a device** — this host has no JDK, Android SDK or emulator, and none was installed. E-Ink hardware behavior (ghosting, refresh, physical contrast, legibility) is **UNVERIFIED**.
Independent review: not performed (executor self-report).

## Audit findings

| Area | Finding (before) | Action |
| --- | --- | --- |
| Body size | 16 sp × step scale in both modes | E-Ink 18 sp base via profile policy |
| Line height | 1.38 / 1.72 / 2.06 × size; the Markwon `TextView` adds it on top of the font's natural height, so the real pitch is about 0.17 em looser | E-Ink 1.45 / 1.60 / 1.80, exact baseline distance (`setLineHeight`); Standard unchanged |
| Margins | 8 / 16 / 24 dp in both modes | E-Ink 16 / 28 / 44 dp |
| Measure | Unbounded: 100+ characters per line on a 10.3-inch panel | E-Ink 36 em cap, centred, flowing text only |
| Justification / hyphenation | None | E-Ink: Markdown paragraphs justified + hyphenated; literal TXT blocks left verbatim |
| Body colour | `TextView` inherited the activity theme's translucent black (`#DE000000`); not tied to the Material scheme | Colour from `MaterialTheme.colorScheme.onSurface` (pure black on E-Ink); links keep the text colour on E-Ink |
| Chrome text | Material 3 scale: 11–14 sp regular for rows, status, footer, buttons | E-Ink: at least 14 sp, Medium weight for the small styles; body 16 / 18 sp |
| Hairlines | 1 dp `#999999` divider and card outline | E-Ink `outlineVariant` `#666666` |
| Dialogs | Platform translucent dim and window fade on two `AlertDialog`s | `LeafletAlertDialog`: no dim, no window animation, solid white, 2 dp outline under the high-contrast profile |
| Progress text | Reader footer recomposed on every block crossing in continuous mode | E-Ink: at most one visible update per second (profile value) |
| Page-turn overlap | 3–8 %, default 5 % | **Unchanged** on purpose: REQ-0005 bounds it and R3-05 tests pin it |
| Already conformant | Instant `NavHost` transitions, ripple off, overscroll glow off, instant scrolls, menu fling off, static loading text, 500 ms scan-progress throttle | Left alone |

## Changed files

| File | Change |
| --- | --- |
| `core/eink/src/main/kotlin/io/leaflet/eink/ReadingLayoutPolicy.kt` | New: `ReadingLayoutPolicy` (Standard / E-Ink presets), `RefreshPolicy.progressMinIntervalMs`, `progressUpdateDelayMs` |
| `core/eink/src/main/kotlin/io/leaflet/eink/DisplayProfile.kt` | `readingLayout` added to the interface and both presets |
| `core/reader/src/main/kotlin/io/leaflet/reader/ReadingLayout.kt` | New: pure `ReadingLayoutResolver` shared by Reader and Settings preview |
| `app/src/main/kotlin/io/leaflet/reader/ui/reader/ReaderScreen.kt` | Resolver wiring; measure cap and centring; `ReaderTextView` + `applyTypography` (colour, justification, hyphenation, exact line height, platform-default restore); block padding; throttled footer |
| `app/src/main/kotlin/io/leaflet/reader/ui/ReadingSettings.kt` | Preview resolves through the same policy |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/Theme.kt` | `LocalDisplayProfile`, chrome typography, darker E-Ink `outlineVariant` |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/Typography.kt` | New: `leafletTypography(highContrast)` |
| `app/src/main/kotlin/io/leaflet/reader/ui/theme/DialogPolicy.kt` | New: `LeafletAlertDialog` |
| `app/src/main/kotlin/io/leaflet/reader/ui/library/LibraryHome.kt` | Two dialogs use `LeafletAlertDialog` |
| `core/eink/src/test/.../ReadingLayoutPolicyTest.kt`, `core/reader/src/test/.../ReadingLayoutTest.kt`, `app/src/test/.../TypographyTest.kt` | New JVM tests (not run) |
| `app/src/androidTest/.../ReaderEinkTypographyTest.kt` | New instrumented test (not run) |
| `docs/architecture/eink.md`, `reader.md`, `docs/requirements/REQ-0004`, `REQ-0007`, `CHANGELOG.md` (and `.zh` pairs) | Policy table, FR-13–FR-15, AC-10/AC-11, changelog entry |

No dependency, module, manifest, flavor source set or vendor SDK was added, and no pagination engine. Both flavors share the one Reader; the only per-mode difference is the profile's numbers. `:core:eink` still depends on Android base APIs only.

## Behavior

- Standard resolves to the pre-existing numbers (16 sp, line height `22/16 × {1.00, 1.25, 1.50}` added to the natural height, margins 8/16/24 dp, no cap, ragged right, 4 dp block padding), pinned by `ReadingLayoutTest`. Two Standard side effects: body text now uses the theme's `onSurface` (`#202720`) instead of the platform's translucent black, which also makes it follow the dark scheme; and a paragraph's container now fills the item width, which changes no visible geometry.
- Switching E-Ink → Standard at runtime restores the platform's own wrapping and link-colour defaults (`ReaderTextView` records them at construction). This path has no automated test.

## Verification — NOT RUN on this host

Run on a machine with JDK 17, the Android SDK and an emulator or device:

```bash
./gradlew :app:assembleFullDebug :app:assembleEinkDebug
./gradlew test lint ktlintCheck
./gradlew :core:eink:testDebugUnitTest :core:reader:testDebugUnitTest
./gradlew :app:connectedFullDebugAndroidTest
./gradlew :app:connectedEinkDebugAndroidTest
# new instrumented test only
./gradlew :app:connectedFullDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.leaflet.reader.ui.ReaderEinkTypographyTest
```

APIs and behaviors the self-review could not confirm, to check first if a build or test fails: Material 3 `AlertDialog` parameters `containerColor` / `tonalElevation` with `AlertDialogDefaults` (BOM 2024.10.01); `TextView.setLineHeight` (API 28) and the platform-default getters read in `ReaderTextView`; the `DialogWindowProvider` parent walk in `DialogPolicy.kt`; ktlint import order and wrapping in the new files.

## UNVERIFIED — real E-Ink devices (6 / 7 / 10.3 inch; R3-09)

- Legibility of 18 sp default type and the Medium-weight small text; whether 14 sp is enough for chrome.
- Line height 1.45–1.80 (Latin and CJK), 16–44 dp margins, and the 36 em measure on a 10.3-inch panel.
- Justification and hyphenation: rivers on narrow 6-inch lines, hyphenation rules for text in a language other than the device locale.
- Contrast of the `#666666` hairlines and of the 2 dp dialog outline; selected-state clarity with the `#EEEEEE` fills.
- Dialog without dim: whether the window animation is really gone and no residual transition shows.
- Footer cadence of one update per second: ghosting and the feel of a page turn followed by a late footer.
- Page-turn overlap of 5 % (about one line) and half-cut lines at page edges.
- Ghosting, refresh and partial/full redraw behavior in general; the generic controller does not control hardware.

## Known limitations and follow-ups

- The yellow "other match" search highlight is near-white on a grayscale panel and the search `TextField` caret blinks while focused; both need a device check, and the highlight needs a mode-aware token threaded through several call sites.
- Literal TXT / fallback blocks are not justified or hyphenated (Compose `Text`, shown verbatim by design).
- Hyphenation uses the device locale's rules.
- The preview in Settings uses Compose `Text`, so its justification approximates the Reader's hyphenated `TextView`.
- Prerequisites for the next task are not claimed: R3-09 (device matrix) and R3-10 (owner decisions) remain blocked. Nothing was committed or pushed.
