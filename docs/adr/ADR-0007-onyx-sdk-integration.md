# ADR-0007 — Onyx SDK Integration

English | [中文](ADR-0007-onyx-sdk-integration.zh.md)

Status: Proposed
Date: 2026-09-17



### Context

The official BOOX / Onyx reference is `onyx-intl/OnyxAndroidDemo` (`docs/archive/init.md` §§19, 46). T00's research records the low-level EpdController API, the `EpdDeviceManager` wrapper (including GC interval counting and paired `enter/exitAnimationUpdate` semantics), UpdateMode values such as DU/GU/GC/REGAL, UpdateOption profiles, ABI/native artifacts, a private HTTP repository, and a license discrepancy (the repository says Apache-2.0 while mvnrepository says BSD 3-Clause; packaged license text has not been verified).

M0–M1, including T12, does not integrate an Onyx SDK. The private repository and transitive dependencies would affect dependency isolation, ABI, package size, Play compatibility, and licensing, none of which has been verified. Without real hardware, refresh-waveform claims cannot be accepted. Framework behavior is enough for MVP. This Proposed ADR records future isolation prerequisites and is not to be reconsidered before M5.

### Tentative decision (revisit only after prerequisites)

1. Target M5: Onyx EPD, advanced refresh control, and device-specific optimization (`docs/product/roadmap.md`; `docs/archive/init.md` §39). Do not create an empty `OnyxEInkController` before then.
2. All seven prerequisites in `docs/research/onyx-epd.md` §6 must be met:
   - **Source isolation:** implementation only in an `eink` flavor source set and/or an Onyx subpackage/module under `:core:eink`; zero `full` references; Reader still depends only on `EInkController`.
   - **Dependency isolation:** declare the SDK only in the `eink` dependency block. Prove `fullRelease` contains no Onyx bytecode or native libraries by APK analysis (T13).
   - **Pinned version and supply chain:** pin a verified `onyxsdk-device` version, recheck currently unverified API facts against the packaged API, use an HTTPS/proxied repository, make CI reproducible, and record every transitive dependency.
   - **License closure:** inspect packaged LICENSE/NOTICE, update `THIRD_PARTY_LICENSES`, and explain any discrepancy with the repository's Apache-2.0 statement.
   - **ABI / size / permissions:** install on both ABIs, record size differences, and review merged-manifest permissions.
   - **Behavior contract:** wrap primitives in intent methods with Generic-equivalent semantics; pair Fast Mode entry/exit; make GC interval configurable and conservative by default; detect capability and fall back to Generic on non-BOOX devices without a model allowlist.
   - **Acceptance:** run real E-Ink tests in 6", 7", and 10.3" categories for ghosting, flashing, and page turns. On failure, disable the implementation and return to Generic without blocking the Full package.
3. Until integration, ADR-0005 remains in force: Reader never directly references the SDK, generic behavior makes no hardware partial-refresh claim, and unavailable hardware modes are not shown.

### Alternatives considered

- **Integrate in MVP:** rejected for the reasons above and in `onyx-epd.md` §5.
- **Never integrate:** valid if real-device tests show generic behavior is sufficient. However, BOOX waveform capabilities are proprietary, so “never needed” is not yet supported by evidence. Let M5 device evidence decide.
- **Include SDK in all flavors:** pollutes the general package and conflicts with ADR-0003 dependency isolation. Rejected.

### Consequences

While Proposed, adding Onyx coordinates, repository configuration, or implementation code violates this decision; T12's “no Onyx SDK” acceptance remains active. Track SDK version and documentation changes in `onyx-intl/OnyxAndroidDemo` (pinned commits are listed in `onyx-epd.md` §0).

### Follow-up / open questions

- Is `onyxsdk-device` actually hosted on Maven Central? Direct search.maven.org access returned 403; unverified.
- What license text is distributed in the SDK's LICENSE/NOTICE? It must be downloaded and checked.
- APK size delta, full native `.so` inventory, and Play compatibility are unverified and must be measured during M5.
- Only after all questions close may this ADR move from Proposed to Accepted. If the design changes, supersede this ADR with a new one; do not rewrite its history.
