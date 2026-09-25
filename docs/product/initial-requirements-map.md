# Initialization Requirements Migration Map

English | [中文](initial-requirements-map.zh.md)


Checked on 2026-09-25.

**Conclusion:** M0/M1 product capabilities, engineering constraints, research, and delivery requirements have been carried into current REQs, ADRs, architecture documents, and task plans. The root initialization prompt is archived at [the historical record](../archive/init.md) and is no longer a work entry point. **Requirements being mapped does not mean implementation or acceptance is complete.**

M2–M5 remain future directions from the initial prompt and have not been decomposed into READY implementation tasks. Do not claim that the full long-term roadmap has been broken down into tasks.

### Current entry points

- WHAT / WHY: [requirements index](../requirements/README.md), [product vision](vision.md), and [principles](principles.md).
- HOW: [architecture overview](../architecture/overview.md) and [ADR index](../adr/README.md).
- Initial task breakdown: [T00–T13](../plans/M0-M1-implementation-plan.md).
- Remaining MVP work: [R3-01–10](../plans/remaining-mvp-tasks.md) and [next handoff](../plans/r3-next-deliverables.md).
- Completion evidence: [R3 independent review](../plans/r3-01-04-independent-review.md) and [defect list](../plans/round-2-open-defects.md). Do not infer completion from the original plan alone.

### Where the original sections moved

Paths are relative to the repository root. Section numbers refer to the archived source and preserve historical traceability.

| Original section | Requirement | Current document / task |
| --- | --- | --- |
| Opening, 1–2 | Product position, temporary package name, M0 priorities | `product/vision.md`, `principles.md`; REQ-0004/0010; T01/02 |
| 3–6 | Runtime modes vs. flavors; E-Ink first release | REQ-0004/0010; ADR-0003; T02/12; R3-04/10 |
| 7 | Formal APK-size requirement | REQ-0011; `performance/apk-size.md`; T13; R3-08 |
| 8–9 | Technical direction, native parser, block rendering | `architecture/overview.md`, `reader.md`; ADR-0004/0006; T00/06 |
| 10 | File formats, Markdown syntax, fallback behavior | REQ-0002/0007/0009; T06/09/10; R3-03 |
| 11–13 | Multiple libraries, SAF, read-only originals, metadata scan | REQ-0001; ADR-0001/0002; `architecture/storage.md`; T04/05; R3-02 |
| 14–15 | Titles, Reader capabilities, reading position | REQ-0002/0003/0006/0007/0008/0009; T06–11; R3-03–07 |
| 16–17 | Viewport page turns, reading modes, tap zones | REQ-0005; T08; R3-05 |
| 18–19 | Controller isolation, Onyx research and deferred integration | REQ-0004; ADR-0005/0007; `architecture/eink.md`; `research/onyx-epd.md`; T00/12; M5 |
| 20 | Adapt to usable width, small-screen first, defer two-column layout | R3-05/09 viewport and device matrix; two columns remain deferred |
| 21–22 | E-Ink and Standard design systems | REQ-0004/0007/0010; `architecture/eink.md`; T02/09/12; R3-04/05/09 |
| 23–24 | Horizontal table scroll; code font, labels, copy, and wrapping | REQ-0007; T09; R3-03/05; WRAP/CARD and highlighting improvements deferred |
| 25 | Required in-document search; defer library-wide FTS | REQ-0008; T11; R3-07; M4 |
| 26–27 | 25 MVP capabilities and explicit non-goals | `product/vision.md`; REQ-0001–0011; T00–13; R3-01–10 |
| 28–29 | Few modules, Repository, StateFlow, manual DI | `architecture/overview.md`; T01/02; AGENTS.md |
| 30–33 | Documentation layout, root AGENTS, defer local rules | T01/02; AGENTS.md; README; no local AGENTS in phase one |
| 34–37 | Requirements as files, template, status, WHAT/HOW separation | `requirements/README.md`; AGENTS.md; T01 |
| 38 | ADR template and immutable Accepted history | `adr/README.md`; AGENTS.md; T01 |
| 39 | M0–M5 roadmap | `product/roadmap.md`; scope notes in `remaining-mvp-tasks.md` |
| 40–43 | README content and flavor distinction | README; T02; R3-08 checks documentation status |
| 44 | CHANGELOG | CHANGELOG; AGENTS DoD; T02/13; R3-08 |
| 45–46 | Research on six reference projects and comparison | `research/README.md`, `markdown-renderers.md`, `eink-readers.md`, `onyx-epd.md`; T00 |
| 47 | Project/dependency licensing and third-party notices | THIRD_PARTY_LICENSES; AGENTS; T00/02/13; R3-10; project license decision remains open |
| 48 | Unit/device tests and samples | REQ-0011; `testdata/README.md`; `development/testing.md`; T03; R3-03/07 |
| 49 | E-Ink device fields and real-world matrix | REQ-0011; `development/eink-test-matrix.md`; T13; R3-09 |
| 50–51 | Performance baselines, 100 KB–10 MB samples, no OOM/ANR/crash | REQ-0011; ADR-0006; performance docs; R3-07/08 |
| 52 | Dependency value, compatibility, license, and size | AGENTS dependency policy; T00/13; R3-08 |
| 53–54 | Coding-agent workflow and DoD | AGENTS; task handoffs and REQ/AC; R3-08 |
| 55 | Separate logical tasks and Conventional Commits recommendation | Plan §8; constraints below; does not authorize automatic commits |
| 56–57 | Two-flavor CI and actual build commands | `.github/workflows/ci.yml`; `development/build.md`, `testing.md`; T02; R3-08 |
| 58 | Offline by default; no accounts or tracking; explicit need for network | `product/principles.md`; AGENTS; REQ-0009/0011; R3-06/08 |
| 59–60 | AppCapabilities and shared implementation | REQ-0004/0010; ADR-0003/0005; `architecture/overview.md`; T02/12 |
| 61–62 | Implementation sequence and initialization stop condition | T00–13 dependency table; T02 initial boundary; historical steps are not repeated |
| 63 | Handoff report requirements | T13; R3-08; AGENTS DoD |
| 64–65 | First REQ / ADR topics | requirements and ADR indexes, using the current semantic mapping rather than example numbers |
| 66–67 | Ten product principles and core reading path | `product/principles.md`, `vision.md`; project position in AGENTS.md |

### Retained constraints and numbering

- Adapt to Window / actual usable width, not device brand or diagonal size. The MVP prioritizes a single column on small screens; two columns are deferred, while architecture must not assume a permanent single-column layout. R3-05/09 cover rotation and width changes. A later two-column feature requires a new REQ.
- Suggested commit types are `feat/fix/perf/refactor/docs/test/build/chore`. Do not combine unrelated tasks into one large commit or create meaningless tiny commits. A commit still requires user authorization; archiving does not authorize one by itself.
- Original §64 Reader Preferences is covered by current REQ-0007/0010; TOC by REQ-0006; Distribution Flavors by REQ-0004; local resources by REQ-0009; performance and delivery by REQ-0011. Topics are covered even though the original example numbers differ.
- Original §65 DisplayProfile is covered by ADR-0003/0005 and REQ-0004; rendering by ADR-0004; Reader architecture by ADR-0006; Onyx by ADR-0007. Do not recreate duplicate files to match old example numbering.
- Interpret release strategy and the M1/M2 boundary for tables and images using section 2 of the implementation plan and current Accepted ADRs. Archiving does not change product decisions.

### Archive boundary

The source remains at `docs/archive/init.md`; its original sections and prompt body are retained. An archive notice prevents the ending “start execution now” instruction from being treated as current authorization. References to it are historical only; daily development starts from current REQs, ADRs, plans, and AGENTS.md.

Open defects, real-device acceptance, formal licensing, signing, and later milestones remain open. Moving the root prompt did not close them. Historical source references may change path, but historical decisions and reports are not rewritten.
