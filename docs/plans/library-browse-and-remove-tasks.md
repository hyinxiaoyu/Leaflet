# Library browse and removal tasks

English | [中文](library-browse-and-remove-tasks.zh.md)

Date: 2026-09-28
Status: LT-1, LT-2, LT-3, and LT-4 are implemented.
Plan: [Library browse and removal](library-browse-and-remove-plan.md).
Requirement: [REQ-0001](../requirements/REQ-0001-folder-library.md) A1 and A2.

## Order

LT-1 and LT-4 have no dependency on each other. LT-2 waits for LT-1. LT-3 waits for LT-2 and is the only task that may add directory metadata.

| ID | Task | Depends on | Delivers |
| --- | --- | --- | --- |
| LT-1 | Level ordering | None | A tested function that, for one directory prefix, returns subdirectories then files by name |
| LT-2 | Drill-down browse | LT-1 | `LibraryBrowseScreen` shows one level, enters a directory, and returns |
| LT-3 | Empty directories | LT-2 | Directory metadata so a folder with no indexed document can appear |
| LT-4 | Long-press removal | None | Library-list long-press confirms and calls `removeLibrary` |

## LT-1 — Level ordering

Build a pure function over the documents already indexed. Input is the current relative prefix and the document list. Output is the direct child directories, then the direct child files.

Ordering inside each group uses case-insensitive name order, then the raw name. The directory name is the next `relativePath` segment. The file name is the last segment. Do not sort the whole Library by the full path.

Tests cover: root and nested prefixes, files beside directories, Chinese and mixed-case names, a tie broken by raw name, and a prefix that hides deeper files from the current level. No Room change. No UI.

Stop when the function and its JVM tests pass. Do not open the browse screen.

## LT-2 — Drill-down browse

Replace the flat `groupedEntries` list. The screen shows the LT-1 rows for the current prefix. Tapping a directory sets that prefix. Back at the root leaves the Library. Back below the root moves up one segment. Tapping a file still opens the Reader by document id.

Empty state, refresh, cancel, and failure text stay. Both flavors use this screen. E-Ink stays static: no new animation. Add a focused UI test or JVM test of the prefix transitions if the project already has that harness; do not add a new test framework.

Stop when one level renders in directory-then-file order and navigation returns to the parent. Do not store empty directories here.

## LT-3 — Empty directories

Index directory entries as metadata during the existing scan: relative path only, no file body, no copy of the original. A directory with no Markdown or TXT descendant still appears as a directory row and uses the LT-2 empty placeholder.

Keep the current visit guard, cancel behavior, and the rule that a failed scan does not drop the previous index. Original bytes stay unchanged.

Delivered as: the `directories` table (database version 2, `MIGRATION_1_2`), the scanner's path-only rows, and the browse union with document-derived parents.

Stop condition met when an empty directory survives a successful scan and shows on its parent level. Do not start this task in the same change as LT-1 or LT-2.

## LT-4 — Long-press removal

On the Library list, long-press one row. Show a confirmation that only the local reference and index are removed. Confirm calls `LibraryRepository.removeLibrary`. Dismiss leaves every row in place.

Prove, with the existing repository tests or one new UI test, that documents, reading positions, and recent-read rows for that Library are gone, a shared grant is kept, an unshared grant is released, and the original bytes are unchanged. E-Ink uses a static confirmation and does not require a ripple.

Stop when the list updates after a confirmed removal. Do not delete SAF originals. Do not remove a subdirectory from inside browse.

## Handoff

Each task reports changed files, the command that was run, and what was not run. Finishing one task does not start the next. LT-1–LT-4 have met their stop conditions; REQ-0001 A1 and A2 are no longer `DRAFT`.
