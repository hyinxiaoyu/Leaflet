# Product Roadmap

English | [中文](roadmap.zh.md)


> Source: `docs/archive/init.md` §39 (milestones and scope), and `docs/plans/M0-M1-implementation-plan.md` §§1 and 8 (goals, boundaries, and no schedule commitment). This roadmap sets priority and scope, not dates. Finish T00 and T02 first, then assess the remaining work against toolchain, renderer, and device evidence. Task count is not an effort estimate.

### M0 — Foundation

- Project skeleton: Gradle Wrapper, Kotlin DSL, version catalog, six-module plan frozen in T01, and manual DI.
- `AGENTS.md`, REQ / ADR templates and indexes, and architecture contracts.
- Buildable `full` / `eink` flavors, minimal Library / Settings, and basic DisplayProfile / MotionPolicy.
- Basic flow: “add folder → browse → read MD/TXT → exit → restore position” (T02–T07).
- Build commands are the ones verified against actual Gradle tasks in T02; until then, mark them pending verification.

### M1 — E-Ink MVP

- An E-Ink MVP Alpha release candidate that can be accepted. Signing and public release remain separate from development acceptance.
- Required MVP capabilities (`docs/archive/init.md` §26): folder Library management and browsing; Markdown/TXT reading and title extraction; TOC; reading position and recent documents; in-document search; font, line spacing, page margins, and basic font selection; two DisplayProfiles and mode switching; viewport page turns and continuous scrolling; tap zones; manual refresh; Settings and About.
- Baseline usable tables, code blocks, relative images, and links are included in M1 (`docs/archive/init.md` §§10, 15, 23, 24; implementation plan §2). M2 improves them; it does not defer them.
- Wire the E-Ink policy throughout: disable animation, smooth scrolling, overscroll effects, ripple, and crossfade; use generic refresh behavior. Do not claim unavailable hardware partial/full refresh, and do not integrate the Onyx SDK.

### M2 — Reader Quality

Improve large-document handling, tables, code blocks, relative images, TOC quality, and search quality. This work improves M1 baseline capabilities: M1 makes them usable; M2 makes them better. Establish performance baselines and regression gates only after repeatable thresholds exist.

### M3 — Full Android Edition

Polish Standard Android behavior, optional animation, dark theme, and conventional Android UX; then release the Full flavor. Standard polish must preserve the E-Ink constraints: DisplayProfile / MotionPolicy must still be able to disable animation globally.

### M4 — Search and Library Scale

Add library-wide FTS and large-library improvements. The MVP does not include library-wide search; until M4, search is limited to the open document (`vision.md`).

### M5 — Advanced E-Ink Integration

Consider Onyx EPD integration, advanced refresh control, and device-specific optimization. ADR-0007 (Proposed) is the decision entry point. Do not integrate or create a fake implementation before vendor SDK evidence is sufficient. Ghosting and refresh claims require real E-Ink hardware; missing device rows remain BLOCKED / NOT_RUN.
