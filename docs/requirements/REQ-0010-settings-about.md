# REQ-0010 — Settings, Display Modes, and About

English | [中文](REQ-0010-settings-about.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0/M1

## Problem

Leaflet separates runtime modes (`STANDARD` / `EINK`) from distribution flavors (`full` / `eink`) (`docs/archive/init.md` §§3–6). Typography, refresh intent, and motion depend on runtime mode. Without unified Settings and About pages, users cannot change mode, adjust reading layout, or understand version/license state. Showing hardware controls that are not implemented, such as Onyx UpdateOption modes, would be a false promise (`docs/research/onyx-epd.md` §5).

Source: `docs/archive/init.md` §§3, 6, 26, 47; implementation-plan T02/T12.

## User Story

As a reader who switches between Standard and E-Ink, I want to change display mode, adjust typography, and see version/license information in Settings/About so that behavior is controllable and capability claims are honest.

## Scope

- Runtime display-mode switch STANDARD / EINK through DisplayProfile, with immediate effect in principle.
- Entry points to REQ-0007 typography controls (size/family/spacing/margins/soft-wrap) and REQ-0005 reading-mode switch (continuous/page turn).
- About page with app name, version, flavor identity, third-party license link, and project-license status.
- Until the owner selects a project license, About and documentation state “license TBD”; do not choose MIT/Apache or another license on their behalf.
- Centralized replaceable temporary application IDs (`io.leaflet.reader` / E-Ink suffix); never present them as formal IDs.
- Gate visible settings by AppCapabilities derived from flavor and runtime environment; show only deliverable capabilities.

## Non-goals

No Onyx hardware refresh modes (five UpdateOption values, GC interval, Fast Mode), Standard-only animation tuning, dynamic color, complex theme editor, accounts/cloud sync/analytics, configurable tap-zone editor, full gesture system, project-license selection, or production signing material.

## User Experience

Organize Settings into Display mode, Typography, Reading mode, and About. Both flavors support hot switching; `eink` defaults to EINK, `full` to STANDARD. Persist selection across process recreation. E-Ink follows the disabled-motion list and uses static loading/progress. Do not show unavailable hardware capabilities, even as disabled controls.

## Functional Requirements

- **FR-1:** Provide STANDARD/EINK switch, applied globally through DisplayProfile/MotionPolicy; Reader has no scattered `if (isEink)` branches.
- **FR-2:** Persist mode in DataStore, restore after recreation, and allow switching in both flavors despite different defaults.
- **FR-3:** Provide typography entry and values/persistence from REQ-0007 FR-1–4/7.
- **FR-4:** Provide CONTINUOUS_SCROLL/PAGE_TURN entry from REQ-0005; switching preserves position.
- **FR-5:** About shows `Leaflet`, version, distinguishable Full/E-Ink identity, and third-party license entry.
- **FR-6:** Until project license is decided, About, README, and CHANGELOG mark it TBD; do not display an unapproved license.
- **FR-7:** Centralize temporary application IDs and keep them replaceable; do not call them final in docs or About.
- **FR-8:** AppCapabilities gates settings; unsupported features are absent. Generic/NoOp EInkController has no hardware-refresh setting.
- **FR-9:** DisplayProfile/MotionPolicy controls and verifies animation, smooth scroll, ripple, overscroll, crossfade, and infinite loading are off on E-Ink.
- **FR-10:** MVP `requestFullRefresh`/`requestPartialRefresh` only mean asking the system to redraw. UI/docs must not claim REGAL/GU/GC hardware refresh.

## Acceptance Criteria

- **AC-1:** Install both flavors; About distinguishes them; E-Ink defaults EINK and Full defaults STANDARD.
- **AC-2:** Switch mode in either flavor and observe motion/scroll changes immediately; force-stop/restart retains choice.
- **AC-3:** E-Ink checklist passes: no smooth scroll or overscroll motion, ripple off (or documented very weak), no crossfade or infinite loader; static loading is allowed.
- **AC-4:** Settings and global UI copy contain no UpdateOption, REGAL, Fast Mode, GC interval, or equivalent hardware-mode claims.
- **AC-5:** About says license TBD. Repository search finds no unauthorized MIT/Apache claim for Leaflet itself (third-party inventory is exempt).
- **AC-6:** Typography settings persist per REQ-0007 AC-1; reading-mode switch preserves position per REQ-0005.
- **AC-7:** Unsupported capabilities do not appear, verified against the AppCapabilities matrix rather than visual inspection alone.

## Edge Cases

Switching mid-document retains position. Corrupt/missing DataStore falls back to flavor default without crash and writes back the default. E-Ink flavor may run EINK mode on a non-E-Ink phone using generic system behavior; do not use brand allowlists. Rotation/resume/backgrounding preserve mode and typography.

## E-Ink Considerations

Use solid high-contrast background/text, no transparency/shadow/gradient/blur/animated elevation/shimmer, and no infinite loader (`docs/archive/init.md` §21). Prefer static text, percentage, and simple progress. MotionPolicy globally disables animations; do not scatter logic in Screen.

## Standard Android Considerations

Standard uses simple Material 3 with subtle ripple, limited transitions, normal scrolling, and may defer dark theme/dynamic color. Do not show hardware-refresh controls. Full flavor polish is post-M1; M1 requires basic reading only.

## Performance Considerations

Mode switches reuse parsed blocks; do not reparse. DataStore read/write is asynchronous and must not block main thread or cause ANR.

## Storage / Data Impact

DataStore stores display, typography, and reading mode; clearing/uninstall never affects originals (REQ-0011). Settings schema must migrate without clearing away user values. Do not store document bodies or image copies.

## Test Plan

Unit-test capability gating, DataStore defaults/read/write/corruption fallback, and license-TBD copy. Instrumented tests cover both flavor identities/defaults, switch/persistence, E-Ink off-list, and typography/reading-mode integration. Copy scans assert no hardware-mode strings and no accidental project-license claim. Record mode/menu/resume/rotation on real E-Ink; missing device is NOT_RUN.

## Dependencies

Requires REQ-0004 policy/flavor architecture, REQ-0005 mode behavior, REQ-0007 typography, T02 initial app/flavors, and T12 acceptance.

## Open Questions

- Formal application ID is owner-confirmed; current IDs are temporary. T12 About copy marks `about_app_id` temporary.
- Project license is an owner decision. About/README/CHANGELOG show TBD through `about_license_pending`; this does not block development.
- Both flavors currently retain Standard switching (`supportsStandardMode=true`), locked by T12 test. E-Ink-only requires a separate change.
- Both flavors currently set `supportsDynamicColor=false` in M0. If soft-wrap is unset, E-Ink defaults ON and Standard uses stored value via `effectiveCodeSoftWrap` (T09 behavior; T12 unchanged).
