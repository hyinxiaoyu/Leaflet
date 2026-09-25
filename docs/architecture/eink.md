# E-Ink Architecture

English | [中文](eink.zh.md)

Date: 2026-09-17
Upstream: `overview.md` §3 contracts 7/8; ADR-0003/0005/0007.
Requirements: REQ-0004 (display policy/flavors), REQ-0005 (turns/gestures), REQ-0010 (Settings/About).


## English Overview

This document defines the shared E-Ink policy boundary for REQ-0004/0005/0010 and ADR-0003/0005/0007. Keep the four concepts separate: DisplayProfile controls runtime presentation (motion, scrolling, contrast, refresh intent, and Reader decorations); MotionPolicy is the global animation switch; AppCapabilities determines feature availability from flavor and runtime environment; EInkController exposes vendor-neutral intent methods. UI code must not scatter `isEink` or device-brand checks, and `:core:eink` uses Android base APIs only.

`StandardDisplayProfile` permits restrained transitions and continuous scrolling. `EinkDisplayProfile` disables animation, smooth scrolling, overscroll effects, and ripple; uses static throttled loading feedback; and defaults to viewport page turns. Both flavors retain runtime switching (`full` defaults to STANDARD, `eink` to EINK). Page turns scroll by visible viewport minus overlap, default 5% and bounded to 3–8%; gestures for links, tables, code, copy, and long-press take priority. A real pagination engine is out of MVP scope.

The Generic controller may count refresh intents and occasionally request a system redraw. This is only a redraw cadence policy; it cannot claim REGAL/GU partial refresh or GC full refresh. MVP implements only NoOp and Generic controllers, wires policy through the app/Reader, and shows no unavailable hardware controls. Ghosting and refresh acceptance requires real 6-inch, 7-inch, and 10.3-inch E-Ink devices; emulator evidence is insufficient.

Open calibration: Generic currently defaults to one full-redraw intent per 12 partial-refresh intents; T13 must validate this on hardware. Android-version coverage for BOOX remains a device-matrix item.

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

## Open Questions

- Generic controller currently defaults to one full-redraw intent per 12 partial intents (`fullRefreshEveryNthIntent = 12`). T13 must calibrate on hardware; ghosting claims before then remain NOT_RUN/BLOCKED.
- BOOX Android version baseline awaits physical device matrix. Research proposed minSdk 29/targetSdk 35; T02 build succeeded, but that does not establish hardware coverage.
