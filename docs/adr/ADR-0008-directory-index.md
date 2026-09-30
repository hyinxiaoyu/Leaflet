# ADR-0008 — Indexed Directory Metadata for Browsing

English | [中文](ADR-0008-directory-index.zh.md)

Status: Accepted
Date: 2026-09-28

## Context

REQ-0001 A1 requires Library browse to keep the folder structure and to show a folder even when it holds no indexed Markdown or TXT document. The version 1 index stored document rows only, so the browse level derived folders from the parent segments of `Document.relativePath`. That derivation cannot represent a folder with no indexed descendant: an empty folder, or one holding only unsupported files, simply disappeared. The LT-3 task in [Library browse and removal](../plans/library-browse-and-remove-tasks.md) is the follow-up authorized to store directory metadata, and it must not read file bodies or copy originals (ADR-0001).

Constraints:

- Original files stay the single source of truth; the database is auxiliary metadata only (ADR-0001).
- A failed, cancelled, or overflowing scan must commit nothing and keep the previous index (REQ-0001 FR-5/FR-6).
- The scan is metadata-only and bounded by the existing 5,000-visited-entry guard.
- Existing installs must upgrade without losing Libraries, documents, reading positions, or recent entries.

## Decision

1. Add a path-only `directories` table (`libraryId` + `relativePath` composite key, foreign key to `libraries` with `ON DELETE CASCADE`) and raise the database to version 2 with a tested `MIGRATION_1_2`. The migration is additive and creates an empty table; no existing row changes.
2. During a scan, record one row for every subdirectory the walk visits, whether or not it contains a supported document. The walk already enumerates those entries, so this adds no traversal and opens no stream.
3. Commit directory rows inside the same Room transaction as document rows, under the same rules: a failed, cancelled, or overflowing walk commits nothing, and a walk whose subtree evidence is incomplete deletes nothing.
4. Build the browse level from the union of the indexed directory rows and the parent segments derived from document paths. The derivation stays as a fallback so an index written before LT-3 remains browsable without a forced rescan.
5. Removing a Library clears its directory rows through the same Room cascade that clears its documents and positions. Removal never touches the source folders.

## Alternatives Considered

- **Derive folders from document paths only (no schema change):** keeps version 1 and needs no migration, but cannot represent a folder with no indexed document, which A1 explicitly requires. Rejected.
- **Mark directory rows inside `documents` with a sentinel extension or an `isDirectory` flag:** avoids a new table but puts non-documents into every document query (counts, reader routing, recent entries) and weakens the document invariant. Rejected.
- **Enumerate folders on demand when the browse screen opens:** would work without persistence but makes the UI depend on a live provider query per level, adds a second enumeration path beside the scanner, and loses the offline/stale-index behavior of browse. Rejected.
- **Store folder metadata plus file counts, mtimes, or previews:** more data than the requirement needs and more to invalidate. Rejected for path-only rows.

## Consequences

- Database version 2; `core/storage/schemas/**/1.json` and `2.json` are the reviewed baselines, and `DirectoryMigrationTest` opens a v1 database, migrates it, and asserts every representative row (including an unknown-future position version) survives.
- One row per visited folder, bounded by the existing visit guard. Folder rows add no file-body storage and no network or original-file access.
- The scanner's commit now writes two tables atomically; a directory write failure rolls back the whole index update.
- Per-subdirectory removal, folder sorting by time/size, and library-wide FTS remain out of scope.

## Follow-up

- Real-device provider behavior for folders that exist but cannot be listed stays unverified until the T13 baseline; such a walk reports an entry error and suppresses cleanup.
- If M4 adds library-wide search or a larger browse model, revisit whether directory rows need extra columns; do not add them pre-emptively.
- The Room 2.8.5 + KSP schema-export limitation is recorded in `core/storage/build.gradle.kts`; regenerating a schema uses the documented opt-in `-Proom.schemaExport=true` run.
