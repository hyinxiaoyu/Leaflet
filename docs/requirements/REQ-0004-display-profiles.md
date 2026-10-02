# REQ-0004 — Display Profiles, Flavors, and E-Ink Boundaries

English | [中文](REQ-0004-display-profiles.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0 (T02 minimal policy → T12 full wiring; M1 acceptance)

Source: `docs/archive/init.md` §§2–6, 18, 21–22, 26; implementation-plan T02/T12; `docs/research/eink-readers.md` §5.

## Problem

Two orthogonal concepts are easy to conflate: distribution flavor (`full` / `eink` APKs) and runtime mode (`STANDARD` / `EINK` display policy). Scattered `if (isEink)` branches inevitably miss an animation or scroll behavior, causing ghosting/flicker, while the E-Ink flavor can be polluted by general Android dependencies. Central policy objects, a capability object, and a vendor-neutral refresh interface must each own a distinct concern.

## User Stories

- As an E-Ink user, I want an animation-free, non-smooth, low-distraction reader by default so that page turns are clear and do not create distracting ghosting or flashing.
- As a user with both phone and E-Ink devices, I want to switch display modes in Settings with immediate effect, so that one app works well on both.
- As an E-Ink flavor user, I want a smaller package and stable feature set so that the app uses less of a device's limited storage.

## Scope

- Define `DisplayProfile` presets `StandardDisplayProfile` and `EinkDisplayProfile` with all ten initialization fields: animation/scroll/ripple/high-contrast flags, background/foreground, scroll/page-turn behavior, refresh policy, and Reader decorations.
- Define `MotionPolicy` as the global animation decision point; E-Ink defaults all motion off.
- Define `AppCapabilities` for feature availability. “Can it be used?” belongs to capabilities; “how does it look/move?” belongs to DisplayProfile.
- Provide vendor-neutral `EInkController` methods for Reader entry/exit, page turn, scroll start/end, and partial/full redraw requests. MVP implements only NoOp/Generic system behavior and does not integrate Onyx SDK.
- Both flavors retain runtime mode switching (`eink` defaults to EINK, `full` to STANDARD); temporary application IDs are centralized; Reader code is shared (target ≥90% shared core code).
- Enforce the E-Ink off-list: animation, smooth scroll, overscroll effects, ripple (off or extremely weak), crossfade, complex transparency/shadows/gradients/blur, infinite loading, and frequent UI updates.
- Wire the Settings mode switch and About page through REQ-0010; this REQ defines policy wiring only.

## Non-goals

No editor, accounts/cloud sync, AI/RAG, PDF/EPUB, full annotation, plugins, library-wide FTS, two-column large-screen layout, complex animation/Material motion/shared elements, Onyx/BOOX SDK before M5, hardware refresh controls that cannot be delivered, Standard-only animation work, or separate Reader forks per flavor.

## User Experience

1. Settings shows a radio choice for Standard Android / E-Ink; switching takes effect immediately without restart in principle and survives restart.
2. E-Ink uses solid backgrounds, high-contrast text, no shadows/transparency/gradients, static text with percentage/fine progress, and immediate menu show/hide.
3. Standard uses restrained Material 3, subtle ripple, and ordinary scrolling; complex transitions remain out of M0/M1.
4. Both flavors expose the switch; E-Ink flavor selects E-Ink on first launch.
5. Do not show unavailable hardware controls such as partial/full refresh intervals, GC/REGAL waveforms, or Fast Mode.

## Functional Requirements

- **FR-1 Abstraction:** Provide all ten `DisplayProfile` fields and two presets. UI/Reader obtain behavior through Profile/MotionPolicy; no scattered `isEink` animation branches.
- **FR-2 MotionPolicy:** All animation, transition, crossfade, shimmer, and infinite-loading decisions go through MotionPolicy. E-Ink sets `animationsEnabled=false` with no bypass.
- **FR-3 Both flavors switch:** `full` defaults to STANDARD and `eink` to EINK; both may switch. Persist the choice through process recreation and restart.
- **FR-4 E-Ink off-list:** Disable animation, smooth scrolling, overscroll, crossfade, complex transparency/shadows; ripple is off or extremely weak; no infinite loader; throttle progress updates.
- **FR-5 Capabilities separation:** `AppCapabilities` expresses availability (for example, a Standard-only feature absent from E-Ink flavor), separate from display policy. Express flavor differences through capabilities/source sets; do not duplicate Reader.
- **FR-6 EInkController boundary:** Expose the seven intent methods. MVP provides only `NoOpEInkController` and `GenericEInkController`; Generic may use system redraw only and must not claim hardware partial/full refresh. Reader never references Onyx SDK.
- **FR-7 Lifecycle wiring:** Call controller methods at Reader entry/exit, page turns, and scroll start/end. Without a vendor SDK, safely fall back to NoOp/system behavior.
- **FR-8 Package discipline:** Do not add animation assets, web/JS runtime, heavy editor, AI/network/analytics/ad SDKs to the E-Ink flavor. Record rationale and APK impact for large dependencies (REQ-0011).

## Acceptance Criteria

- **AC-1:** Install and launch both flavors. E-Ink defaults to EINK; Full defaults to STANDARD. Switch in either flavor and observe immediate effect without restart; force-stop/restart retains the selection.
- **AC-2:** Walk through open, page turn, scroll, menus, loading, and errors in E-Ink. Checklist confirms no smooth scroll, overscroll glow/bounce, ripple (except documented extremely weak), crossfade, or infinite loader.
- **AC-3:** Code review finds no Onyx reference in Screen/Reader; all motion decisions use Profile/MotionPolicy, with no raw `isEink` animation branches.
- **AC-4:** Settings exposes no partial/full refresh, waveform, or Fast Mode controls. Generic/NoOp documentation states that it does not trigger hardware refresh.
- **AC-5:** Both debug flavors build with `./gradlew :app:assembleEinkDebug :app:assembleFullDebug`; the applications are distinguishable by name/applicationId.
- **AC-6:** Every claim is supported by build output, a walkthrough checklist, or review evidence; avoid vague “supports X.”

## Edge Cases

Switching while reading preserves the same document/location (coordinate with REQ-0005), changing only visual/motion policy. Upgrades preserve the selection; clearing data returns to flavor default. Running Standard mode in the E-Ink flavor changes policy only and does not unlock features removed by that flavor's AppCapabilities.

## E-Ink Considerations

This REQ defines E-Ink's core constraints: solid high-contrast colors, no transparency/shadow/gradient/blur/animated elevation/shimmer, no infinite loading, and fewer UI updates. Throttle progress/percentage updates to avoid frequent region refresh and flashing.

## Standard Android Considerations

Keep Standard simple too: Material 3, subtle ripple, few transitions, ordinary scrolling; dark theme and dynamic color may be deferred. M0/M1 does not add page transitions, shared elements, or crossfade.

## Performance Considerations

Policy lookup must be allocation-free and main-thread safe. A mode switch must not reparse content; reuse REQ-0003 cache. Track E-Ink flavor size in `docs/performance/apk-size.md` (T02 baseline, T13 acceptance).

## Storage / Data Impact

DataStore stores `displayMode` and optional user overrides such as `highContrast`; Room stores no policy. Centralize temporary flavor IDs (`io.leaflet.reader` / `io.leaflet.reader.eink` are proposed; owner confirmation required before release).

## Test Plan

JVM tests assert every field in both presets and that disabled effects remain off; verify Capabilities and Profile are not conflated. Instrumented tests install both flavors, switch/persist mode, and walk the Reader paths. Ghosting/refresh conclusions require real E-Ink devices (T13); absent devices remain BLOCKED/NOT_RUN.

## Dependencies

Requires T02 flavors, minimal policy, and CI; REQ-0005 page-turn behavior uses `pageTurnBehavior`; REQ-0002/0003 loading and restoration use policy. Enables T12 Settings/About and T13 release candidate.

## Code review follow-up (2026-10-01)

The [2026-10-01 follow-up review](code-review-2026-10-01.md) found that `NavHost` still used the
navigation library's default enter/exit transitions (`fadeIn`/`fadeOut(tween(700))` in
navigation-compose 2.7.7, verified in its bytecode), bypassing FR-2/FR-4 on every route swap.
`LeafletTheme` now publishes the resolved `MotionPolicy` as `LocalMotionPolicy`, and `NavHost`
supplies all four transitions explicitly from that policy. FR-2 keeps the decision centralized and
§Standard adds no page transitions in M0/M1, so both display modes resolve to instant swaps; a
Standard animation policy in a later milestone changes that one place. Device transition capture
remains NOT_RUN (it needs frame-level observation on hardware).

## Open Questions

- Ripple strength was open; T12 resolved it to OFF (`EinkDisplayProfile.rippleEnabled=false`, `LocalIndication provides null`, covered by `einkPresetDisablesEverythingAnimated`). No “very weak” variant remains.
- Standard-mode animation inventory belongs to M3.
- Formal application ID, signing, and license remain owner decisions (plan §2) and do not block development.
- T02 froze JDK/Gradle/AGP/Kotlin/Compose based on build evidence: AGP 8.13.2, Kotlin 2.0.21, compileSdk 35, minSdk 29 (`docs/development/build.md`). Any upgrade needs an ADR.
