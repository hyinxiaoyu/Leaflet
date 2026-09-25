# ADR-0005 — EInkController Abstraction

English | [中文](ADR-0005-eink-controller-abstraction.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

Leaflet treats E-Ink as a platform, not a theme (`docs/archive/init.md` §§21, 66). UI code must not scatter `if (isEink)` branches. A shared policy controls DisplayProfile (motion, scrolling, contrast, refresh policy, reader decorations), MotionPolicy (global animation semantics), and AppCapabilities (feature availability from flavor and runtime environment; distinct from visual policy).

Vendor refresh APIs such as Onyx `EpdController`, `EpdDeviceManager`, and UpdateMode are capable but depend on a private SDK whose distribution and license have unresolved questions. The MVP can provide an E-Ink experience with framework behavior, so a vendor-neutral intent interface separates what the app wants from what hardware can do.

### Decision

1. Define vendor-neutral `EInkController` intent methods without vendor types: enter/exit Reader (`onReaderEnter / onReaderExit`), page/scroll (`onPageTurn / onScrollStart / onScrollEnd`), and refresh requests (`requestPartialRefresh / requestFullRefresh`).
2. The MVP implements only `NoOpEInkController` and `GenericEInkController`. The generic implementation uses framework behavior: disable animations, smooth scrolling, overscroll, ripple, and crossfade; use viewport page turns and static throttled loading; simulate refresh cadence only as policy. Do not create a fake `OnyxEInkController`.
3. Reader never directly references a vendor SDK. `:core:reader` depends only on `EInkController`. A future real integration belongs in an `eink`-only source set; `full` must have zero references.
4. Do not claim hardware partial refresh (REGAL/GU) or full refresh (GC) from Generic/NoOp. Do not show hardware modes the implementation cannot deliver. In the MVP, `requestFullRefresh` means asking the system for a full redraw; ghosting claims require real E-Ink device tests.
5. Every animation must be globally disableable through DisplayProfile or MotionPolicy. On E-Ink, animation, smooth scroll, overscroll, ripple, and crossfade remain disabled, with no infinite loading animation.

### Alternatives considered

- **Scattered `isEink` Booleans:** quick but hard to test, causes Standard/E-Ink behavior drift, and leaks flavor differences. Rejected.
- **Integrate Onyx directly into the MVP:** rejected until M5; see ADR-0007.
- **Expose REGAL/GC/FAST refresh enums to Reader:** couples Reader to BOOX semantics and is meaningless on other devices. Intent names may be borrowed; capability claims may not.

### Consequences

- `:core:eink` depends only on Android base APIs, not app or storage. Reader calls it through the interface, allowing unit tests and replacement.
- T12 accepts display policy by verifying that disabled effects are actually off, not by judging refresh appearance. Actual refresh remains a device-matrix question.
- A GC-interval counter may simulate a system redraw cadence but must be documented as policy, not hardware waveform control.

### Follow-up

The conditions for real Onyx integration are in ADR-0007. Until all seven prerequisites are met, adding code named `OnyxEInkController` violates this ADR. The two DisplayProfile behavior sets are in `docs/architecture/eink.md`; MotionPolicy and AppCapabilities fields are frozen in the T01 contract (`docs/architecture/overview.md`).
