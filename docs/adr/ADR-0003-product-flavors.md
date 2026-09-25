# ADR-0003 — Product Flavors (full / eink)

English | [中文](ADR-0003-product-flavors.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

Leaflet has two independent concepts from day one (`docs/archive/init.md` §§3–6, 60; AGENTS.md): runtime mode (`STANDARD` / `EINK`, switchable in Settings without a restart in principle) and distribution flavor (`full` / `eink`, a build-time dimension that produces separate APKs/AABs).

The E-Ink edition is not a reskin. E-Ink devices have limited storage, do not need some Android-only animations or heavy features, benefit from a smaller dependency set, and may later need a BOOX SDK that must not contaminate the general package. M0/M1 must complete the Leaflet E-Ink edition while retaining a mode switch in Settings to validate the DisplayProfile architecture. The implementation plan prioritizes the explicit switching requirements in §§6 and 26 over an example in §42.

### Decision

1. Use one flavor dimension with `full` and `eink`. Shared behavior belongs in `src/main/` (target: 90%+ core-code sharing); `src/full/` and `src/eink/` contain only flavor-specific parts.
2. Centralize temporary application IDs, such as `io.leaflet.reader` and `io.leaflet.reader.eink`, until the owner confirms formal IDs. Keep them easy to replace and avoid hard-coding. Flavor-specific IDs, app names, and icons are allowed.
3. Flavors may differ only in real distribution concerns: dependencies, resources, manifests and permissions, platform adapters, and feature availability. Do not duplicate Reader business logic into separate `ReaderScreen` implementations.
4. Decouple runtime mode from flavor. DisplayProfile, MotionPolicy, AppCapabilities, and EInkController implement the behavior differences (ADR-0005 and `docs/architecture/eink.md`). Both M0/M1 flavors retain basic mode switching; `eink` defaults to EINK and `full` to STANDARD. An E-Ink-only lock-down later requires a separate requirement.
5. Keep the `eink` flavor lean: remove unnecessary animation resources, web runtime, heavy editing, AI, networking, analytics, and ad SDKs. A future Onyx SDK may appear only in the `eink` dependency block (ADR-0007); E-Ink-only dependencies must not enter the `full` artifact.

### Alternatives considered

- **One package with runtime branches only:** simpler releases, but cannot isolate APK size, permissions, or vendor SDKs and misses the goal of two independent APKs/AABs. Rejected.
- **Duplicate business logic per flavor:** initially faster, but doubles maintenance and lets fixes diverge. Explicitly prohibited.
- **Multiple dimensions (device × paid, etc.):** no MVP need for payment or channel variants; overdesigned. Rejected in favor of one dimension with two flavors.

### Consequences

- T02 must build both flavors and verify the actual `assembleFullDebug` / `assembleEinkDebug` tasks. CI covers both flavor builds, unit tests, and lint.
- T13 records byte size, build configuration, R8 settings, and dependencies/fonts/images for both release packages. Similar initial sizes are acceptable; do not add dependencies merely to manufacture a difference.
- Every new dependency must be checked for E-Ink necessity and recorded in `THIRD_PARTY_LICENSES` and `docs/performance/apk-size.md`.

### Follow-up

The owner decides formal application IDs, signing, license, and distribution before the M1 public release; agents must not choose these or submit production keys. If M3 expands flavor differences, document them in a new ADR rather than silently copying code.
