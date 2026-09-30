# Leaflet agent overrides

This file is an agent instruction. It stays English-only and is not paired with a Chinese translation.

It narrows the documentation language policy in `AGENTS.md` for this repository. If this file and `AGENTS.md` disagree about pairing, follow this file.

## Bilingual documents

English is the primary text. Simplified Chinese is the paired secondary text. Each file must stand alone. Do not leave the Chinese file as a summary, a stub, or a `TODO`.

Create or update both versions in the same change whenever you add or edit:

- `README.md` at the repository root, and any other `README.md` under the project
- Plans in `docs/plans/`
- Tasks: task lists, task cards, and task sections inside a plan
- Other project documents, including requirements, architecture notes, ADRs, changelogs, runbooks, and reports

Pairing rules:

1. Write the English file first and keep it complete without the Chinese file.
2. Store the pair side by side as `foo.md` and `foo.zh.md`.
3. Immediately under the H1, the English file says `English | [中文](foo.zh.md)` and the Chinese file says `[English](foo.md) | 中文`.
4. Mirror heading depth and order, list kinds, table shape, link targets, and code fences.
5. Do not translate code, identifiers, commands, paths, logs, or API names. On first use in Chinese, write the term as `中文术语 (English term)`.
6. Planning work ships the plan and the task split in both languages. Writing those documents does not authorize product implementation.
7. Agent instruction files stay English-only and are not paired. That includes `AGENTS.md`, `AGENTS.override.md`, `CLAUDE.md`, and `GEMINI.md`.
