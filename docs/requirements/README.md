# Requirements (REQ)

English | [中文](README.zh.md)



> Source: `docs/archive/init.md` §§34–37 (REQ management, template, status, and separation from implementation plans), §64 (first REQs), and implementation-plan task T01 (fixed IDs and acceptance fields). REQs define WHAT / WHY. HOW is documented in `docs/architecture/`, `docs/adr/`, and the implementation plan.

### REQ template

Copy the template below when adding a requirement. Include every field from §35 of the archived prompt and use filenames `REQ-XXXX-topic.md`.

```markdown
# REQ-XXXX — Title

Status:
Priority:
Milestone:

## Problem
## User Story
## Scope
## Non-goals
## User Experience
## Functional Requirements
## Acceptance Criteria
## Edge Cases
## E-Ink Considerations
## Standard Android Considerations
## Performance Considerations
## Storage / Data Impact
## Test Plan
## Dependencies
## Open Questions
```

Explain why the feature is needed in `Problem`; write the user value in “As a … / I want … / So that …” form. Define included and excluded work, user-visible behavior, numbered FRs and ACs, edge cases, platform/performance/data impacts, tests, dependencies, and unresolved questions.

### Status values

Use only `DRAFT`, `READY`, `IN_PROGRESS`, `BLOCKED`, `DONE`, or `DEFERRED`.

- Start implementation directly only from a `READY` REQ.
- An agent must not move a major `DRAFT` requirement to `DONE` on its own.
- The 11 REQs delivered in T01 were initially marked `READY`. Put unresolved evidence gaps in `Open Questions`; do not evade them by lowering status or pretending they were verified.

### REQ index

| ID | Title | File | Status |
| --- | --- | --- | --- |
| REQ-0001 | Folder-first Library | `REQ-0001-folder-library.md` | IN_PROGRESS |
| REQ-0002 | Markdown / TXT Reader | `REQ-0002-markdown-txt-reader.md` | IN_PROGRESS |
| REQ-0003 | Reading Position | `REQ-0003-reading-position.md` | IN_PROGRESS |
| REQ-0004 | Display Profiles | `REQ-0004-display-profiles.md` | IN_PROGRESS |
| REQ-0005 | Page Turn | `REQ-0005-page-turn.md` | IN_PROGRESS |
| REQ-0006 | Table of Contents | `REQ-0006-toc.md` | READY |
| REQ-0007 | Typography, Tables, and Code Blocks | `REQ-0007-typography-tables-code.md` | READY |
| REQ-0008 | In-document Search | `REQ-0008-in-document-search.md` | READY |
| REQ-0009 | Local Resources and Links | `REQ-0009-local-resources-links.md` | READY |
| REQ-0010 | Settings / About | `REQ-0010-settings-about.md` | IN_PROGRESS |
| REQ-0011 | Performance and Release Verification | `REQ-0011-performance-release.md` | IN_PROGRESS |
| REQ-0012 | Library Navigation (Breadcrumbs, Stable Scan Status, Row Targets) | `REQ-0012-library-navigation.md` | READY |
| REQ-0013 | Daily Reading Home and Settings | `REQ-0013-daily-reading-home.md` | IN_PROGRESS |
| REQ-0014 | Discoverable Reader Controls | `REQ-0014-reader-controls.md` | IN_PROGRESS |

IDs and ownership were fixed in T01 (implementation plan §5); all 11 filenames and assigned agents were confirmed. Every MVP requirement maps to one of these REQs and implementation-plan tasks. Keep requirements separate from implementation steps; this directory does not prescribe code-level implementation.

REQ-0012 was added on 2026-10-01 as a READY scoped supplement to REQ-0001/REQ-0004 for the UX-05 browsing slice (tasks NB-00–NB-05); it does not change the status of REQ-0001, which stays `IN_PROGRESS`.

REQ-0013 scopes the daily reading Home/settings wave; implementation is IN_PROGRESS and Android/device acceptance remains separate.

REQ-0014 scopes visible Reader navigation, bounded menus, repeatable help and pointer-boundary safety; Android/device acceptance remains pending.
