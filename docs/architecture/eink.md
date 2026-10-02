# E-Ink Architecture

English | [中文](eink.zh.md)

Date: 2026-09-17
Upstream: `overview.md` §3 contracts 7/8; ADR-0003/0005/0007.
Requirements: REQ-0004 (display policy/flavors), REQ-0005 (turns/gestures), REQ-0010 (Settings/About).


## English Overview

This document defines the shared E-Ink policy boundary for REQ-0004/0005/0010 and ADR-0003/0005/0007. Keep the four concepts separate: DisplayProfile controls runtime presentation (motion, scrolling, contrast, refresh intent, and Reader decorations); MotionPolicy is the global animation switch; AppCapabilities determines feature availability from flavor and runtime environment; EInkController exposes vendor-neutral intent methods. UI code must not scatter `isEink` or device-brand checks, and `:core:eink` uses Android base APIs only.

`StandardDisplayProfile` permits restrained transitions and continuous scrolling. `EinkDisplayProfile` disables animation, smooth scrolling, overscroll effects, and ripple; uses static throttled loading feedback; and defaults to viewport page turns. Both flavors retain runtime switching (`full` defaults to STANDARD, `eink` to EINK). Page turns scroll by visible viewport minus overlap, default 5% and bounded to 3–8%; gestures for links, tables, code, copy, and long-press take priority. A real pagination engine is out of MVP scope.

The Generic controller may count refresh intents and occasionally request a system redraw. This is only a redraw cadence policy; it cannot claim REGAL/GU partial refresh or GC full refresh. MVP implements only NoOp and Generic controllers, wires policy through the app/Reader, and shows no unavailable hardware controls. Ghosting and refresh acceptance requires real 6-inch, 7-inch, and 10.3-inch E-Ink devices; emulator evidence is insufficient.

Reading layout (typography, margins, measure, justification) is a profile field too, so the shared Reader never branches on the mode (§6). Open calibration: Generic currently defaults to one full-redraw intent per 12 partial-refresh intents; T13 must validate this on hardware. Android-version coverage for BOOX remains a device-matrix item.

## 1. The Four Core Types and Responsibilities

| Concept | Type | Responsibility |
| --- | --- | --- |
| DisplayProfile | Runtime policy object | Motion, scroll, contrast, refresh policy, Reader decorations; one Standard and one E-Ink preset |
| MotionPolicy | Global motion switch | Every animation can be disabled through DisplayProfile or MotionPolicy |
| AppCapabilities | Feature availability | Determined by build flavor + runtime environment; controls shown/disabled/hidden features, separate from visual policy |
| EInkController | Vendor-neutral intent interface | Reader enter/exit, page turn, scroll start/end, partial/full refresh request; MVP implements NoOp/Generic only |

UI must not scatter `if (isEink)` or brand checks; capability branching is allowed, model allowlists are not (`onyx-epd.md` §2.5). `:core:eink` depends only on Android base APIs. Reader calls the interface; Onyx never enters Reader dependency graph (ADR-0005).

## 2. Standard and E-Ink Profile Behavior

| Behavior | StandardDisplayProfile | EinkDisplayProfile |
| --- | --- | --- |
| `animationsEnabled` | true, restrained transitions allowed | **false** |
| `smoothScrollingEnabled` | true | **false**, page turn is instant |
| Overscroll motion | platform default | **OFF** |
| Ripple | subtle allowed | **OFF or extremely weak** |
| Crossfade/transparency/shadow/gradient/blur/animated elevation/shimmer | restrained use allowed | **OFF**; solid background + high contrast, avoid alpha |
| Loading | ordinary indicator allowed | **static text + percent/fine bar**; no infinite animation |
| Default scroll behavior | CONTINUOUS_SCROLL | PAGE_TURN |
| Page turn | viewport algorithm | same algorithm, default 5% overlap |
| Refresh policy | system default | refresh less, redraw full page, occasional full redraw intent (see §4) |
| Reader decorations | ordinary | maximize reading area; tap menu show/hide; avoid thick progress bar/frequent clock |
| UI update rate | ordinary | throttle; do not redraw frequently |

Both M0/M1 flavors retain mode switch; `eink` defaults EINK, `full` STANDARD. Switch should not restart the app in principle.

## 3. Page Turns: 5% Viewport Overlap

```text
pageHeight = actual visible viewport (account for Insets and menu occlusion)
next:     scrollBy(viewportHeight - overlap)
previous: scrollBy(-(viewportHeight - overlap))
overlap default = 5%; REQ allows 3–8%, DataStore may tune, clamp outside range
```

- Clamp at start/end; recalculate after rotation; include menu occlusion; rapid turns cannot escape bounds.
- E-Ink forbids smoothScroll. Horizontal table/code gestures, links/buttons/copy, and long press (never a turn) precede tap-zone arbitration.
- Do not build true pagination (T08 stop condition). Mode switch keeps position through PositionMap + ReadingPosition fallback.

## 4. GC Cadence Simulation (Not Hardware Control)

- `GenericEInkController` may count partial-refresh intents and issue one full-redraw intent every N page-turn/partial-refresh intents (conservative N; calibrate against T13 device matrix).
- **This is only a “system redraw cadence” request, not hardware waveform control.** Do not claim REGAL/GU partial refresh or GC full refresh. Do not expose five UpdateOption modes, GC interval, Fast Mode, or other unavailable hardware switches (ADR-0005 §4 and `onyx-epd.md` §5).
- MVP does not implement Fast Mode. Paired enter/exit and normal refresh after exit are future ADR-0007 integration semantics.
- Ghosting/flicker/page-turn conclusions require physical 6/7/10.3-inch E-Ink device matrix; absent device stays NOT_RUN/BLOCKED.

## 5. MVP Implementation Checklist (NoOp / Generic only)

- `NoOpEInkController`: all intent methods are no-ops (Standard side/tests).
- `GenericEInkController`: records intents and applies framework behavior from DisplayProfile—instant scroll, static throttled loading, GC-like cadence counter; no Onyx imports.
- T12 wiring uses four seams: `profileFor`, `MotionPolicy.from`, `controllerForMode`, `capabilitiesForFlavor`.
  - `LeafletTheme` resolves profile/policy once. In EINK, `LocalIndication provides null` (ripple off) and transparent glow `OverscrollConfiguration` (overscroll motion off); Standard keeps platform defaults. Navigation swaps instantly.
  - `MainActivity` collects `SettingsStore.displayMode` once and provides it through the full theme tree. Settings switch recomposes immediately; DataStore restores it (REQ-0004 FR-3 / REQ-0010 FR-2).
  - `ReaderContent` obtains controller by mode. Pair `onReaderEnter/onReaderExit` with `DisposableEffect(eink,docId)`. `turnPage` brackets `scroll` with `onScrollStart → scroll → onScrollEnd → onPageTurn`; all scrolling is instant (`scrollToItem` / `scroll { scrollBy }`), no smooth family.
  - About shows version/flavor/temp ID/TBD license/third-party license entry (link, not full license text) and system redraw semantics. No hardware UpdateOption/GC interval/Fast Mode controls.
- T12 acceptance checks every disabled E-Ink effect, hot mode switch/persistence in both flavors, and no scattered brand checks in Reader.

## 6. Reading Layout and Chrome Policy

`DisplayProfile.readingLayout` (`ReadingLayoutPolicy`, `core/eink/.../ReadingLayoutPolicy.kt`) is an eleventh profile field, added after REQ-0004 FR-1's original ten. It holds the numbers behind typography and page layout so the shared Reader has one layout path and only the values differ per profile. `ReadingLayoutResolver` (`:core:reader`) turns the user's persisted steps plus this policy into sp/dp values; the Reader and the Settings preview both call it. Standard resolves to exactly the pre-existing numbers (pinned by `ReadingLayoutTest`).

| Setting | Standard | E-Ink |
| --- | --- | --- |
| Body size | 16 sp × step scale (0.85–1.45) | 18 sp × step scale |
| Line height, steps 0/1/2 | 1.375 / 1.719 / 2.063 × size, added to the font's natural height (legacy) | 1.45 / 1.60 / 1.80 × size, exact baseline distance |
| Page margin, steps 0/1/2 | 8 / 16 / 24 dp | 16 / 28 / 44 dp |
| Measure (line length) | uncapped | 36 em, centred; flowing text only (code, tables, images keep full width) |
| Justification | ragged right | inter-word justified for Markdown paragraphs; literal TXT / fallback blocks stay verbatim and ragged |
| Hyphenation | platform default | normal frequency with high-quality line breaking |
| Block padding | 4 dp | 6 dp |
| Chrome small text | Material 3 scale | at least 14 sp, Medium weight; body medium 16 sp, body large 18 sp |
| Body / link colour | theme `onSurface` | theme `onSurface` (pure black); links keep the text colour and rely on their underline |
| Divider / card outline (`outlineVariant`) | `#D0D8CE` | `#666666` (was `#999999`) |
| Footer progress text | live | at most one visible update per second (leading edge, trailing coalesced) |
| Confirmation dialogs | platform dim and window fade | no dim, no window animation, solid white container, 2 dp outline |

Page-turn overlap is deliberately unchanged (3–8 %, default 5 %): REQ-0005 bounds it and the R3-05 acceptance tests pin it. At the default it keeps roughly one text line across a turn on a 6-inch panel; whether that is enough is a device-matrix question. Lines that end up cut at a page edge cannot be avoided without a pagination engine, which is out of MVP scope.

Not changed and still open: the yellow "other match" search highlight is near-white on a grayscale panel (the current match is inverted black/white and unaffected), and the Material `TextField` caret in the search bar blinks while focused. Both need a device check before a fix is chosen.

All numbers are first-principles defaults. Panel contrast, ghosting, refresh behaviour and physical legibility are unverified until the real-device matrix has run.

## Open Questions

- Generic controller currently defaults to one full-redraw intent per 12 partial intents (`fullRefreshEveryNthIntent = 12`). T13 must calibrate on hardware; ghosting claims before then remain NOT_RUN/BLOCKED.
- The E-Ink reading-layout numbers in §6 (18 sp base, 1.45–1.80 line height, 16–44 dp margins, 36 em measure, justification) are unmeasured defaults; T13 must confirm or retune them on 6-, 7- and 10.3-inch panels.
- BOOX Android version baseline awaits physical device matrix. Research proposed minSdk 29/targetSdk 35; T02 build succeeded, but that does not establish hardware coverage.
