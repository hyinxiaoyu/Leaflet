# Architecture Decision Records (ADR)

English | [中文](README.zh.md)



> Source: `docs/archive/init.md` §38 (ADR directory, naming, and template) and implementation-plan task T01 (decision list). REQs describe WHAT / WHY; this directory, `docs/architecture/`, and the implementation plan describe HOW.

### ADR template

Copy this template when adding an ADR. Keep every field. Use filenames in the form `ADR-XXXX-title.md`.

```markdown
# ADR-XXXX — Title

Status:
Date:

## Context

## Decision

## Alternatives Considered

## Consequences

## Follow-up
```

Field meanings:

- `Status`: `Accepted` means the decision is final and implementation follows it; `Proposed` means evidence is insufficient and research is still needed.
- `Date`: decision or proposal date.
- `Context`: triggering background and constraints.
- `Decision`: what was decided.
- `Alternatives Considered`: options considered and why they were rejected.
- `Consequences`: benefits and costs.
- `Follow-up`: items that still need verification.

### Accepted / Proposed rules

- Keep an ADR `Proposed` when evidence is insufficient; do not present it as `Accepted` (T01 acceptance criterion).
- Once an ADR is `Accepted`, do not rewrite its history. Record a material change in a new ADR that supersedes it.
- Change a related `Proposed` ADR to `Accepted` only after research is complete (`docs/archive/init.md` §65).

### ADR index

| ID | Title | File | Status |
| --- | --- | --- | --- |
| ADR-0001 | Folder-first Local Storage | `ADR-0001-folder-first-storage.md` | Accepted |
| ADR-0002 | SAF Document Access | `ADR-0002-saf-document-access.md` | Accepted |
| ADR-0003 | Product Flavors | `ADR-0003-product-flavors.md` | Accepted |
| ADR-0004 | Native Markdown Rendering | `ADR-0004-native-markdown-rendering.md` | Accepted |
| ADR-0005 | EInkController Abstraction | `ADR-0005-eink-controller-abstraction.md` | Accepted |
| ADR-0006 | Block Rendering, Position and Large Files | `ADR-0006-block-rendering-position-large-files.md` | Accepted |
| ADR-0007 | Onyx SDK Integration | `ADR-0007-onyx-sdk-integration.md` | Proposed |
| ADR-0008 | Indexed Directory Metadata for Browsing | `ADR-0008-directory-index.md` | Accepted |

ADR-0001–0006 and ADR-0008 are Accepted: folder-first storage, read-only SAF, separation of flavors/capabilities, native rendering, EInkController abstraction, block rendering with position recovery and large-file fallback, and path-only directory metadata for browsing have sufficient evidence. ADR-0007 remains Proposed: vendor SDK integration is an M5 item and must wait for the required evidence. Do not integrate it or create a fake implementation before then.
