# R3-09 / R3-10 Readiness — Device Acceptance and Release Decisions

English | [中文](r3-09-10-readiness.zh.md)

Date: 2026-09-28
Packages: R3-09 (device matrix) and R3-10 (release decisions) in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md)
Status: **BLOCKED** — R3-09 needs physical hardware supplied by the owner; R3-10 needs owner decisions. Neither can be completed by an agent in this environment, and no emulator result may be recorded as hardware evidence (REQ-0004/0011, AGENTS.md).

## R3-09 — Device matrix (blocked)

Needed from the owner: at least one ordinary Android phone plus the target E-Ink categories
(≈6", ≈7", ≈10.3"). Emulator runs (`dev_arm64_api35`) cover software behaviour only; ghosting,
contrast, and hardware partial refresh cannot be produced or measured there.

When a device is available, record for each device/size (append to
[`eink-test-matrix.md`](../development/eink-test-matrix.md)):

| Item | How to record |
| --- | --- |
| Device identity | Model, size class, Android version, panel type |
| Install/upgrade | `adb install -r` over the previous build; state preserved or not |
| Cold start | One launch per display mode, static stopwatch or `am start -W` |
| Ghosting after turns | Ten page turns on dense text; photograph before/after, note residual image |
| Refresh behaviour | Observe region/full refresh cadence; note whether the generic controller is inert (expected for MVP) |
| Contrast/typography | Body, headings, code, tables at default and max font step |
| Menu/resume/rotation | Menu open/close, background→resume, rotation; no lost position |
| Rapid turns | Ten fast taps at the tail and head; no overshoot, no ANR |

Unsupplied rows stay `NOT_RUN`; they must not be inferred from the emulator.

## R3-10 — Release decisions (blocked on owner)

The owner must decide, and only the owner may authorize:

1. **Project license.** No license file may be chosen or added on the owner's behalf (AGENTS.md).
2. **Formal application ID.** `app/build.gradle.kts` currently carries the temporary IDs; changing
   them is a release decision, and it invalidates existing installs.
3. **Signing custody.** No keystore, key, or production signing configuration may be created here.
4. **Distribution channel.** Where the APK/AAB is published, and who approves the release.

Already prepared and safe to reuse:

- **Third-party inventory**: [`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md) lists shipped
  and test-only artifacts with licenses; it is updated whenever a dependency changes.
- **APK composition/size**: [`docs/performance/apk-size.md`](../performance/apk-size.md); refreshed by
  the R3-08 candidate report, including debug-only test resources (they are absent from release).
- **Build/verification commands**: [`docs/development/build.md`](../development/build.md) and
  [`docs/development/release.md`](../development/release.md).
- **Version/change notes**: [`CHANGELOG.md`](../../CHANGELOG.md) (English + Chinese pair).
- **Install/upgrade checks**: the R3-08 candidate report records debug/release install and
  database-upgrade evidence (schema 1 → 2).

Blocked actions until the owner decides: adding a `LICENSE` file, changing the application ID,
generating or using signing material, tagging a release, and uploading an artifact.

## Hand-off

Both packages stay open in [Remaining Tasks for the Initial MVP](remaining-mvp-tasks.md). R3-08
delivers a limited software candidate that does not depend on either decision; nothing here
authorizes a release.
