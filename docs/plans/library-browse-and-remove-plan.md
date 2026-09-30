# Library browse and removal

English | [中文](library-browse-and-remove-plan.zh.md)

Date: 2026-09-28
Status: LT-1, LT-2, LT-3, and LT-4 implemented.
Requirements: [REQ-0001](../requirements/REQ-0001-folder-library.md) additions A1 and A2.
Tasks: [LT-1–LT-4](library-browse-and-remove-tasks.md).

## What is true today

LT-1, LT-2, LT-4 replace the flat list and add Library removal; LT-3 adds directory metadata. Browse shows one directory level at a time. Besides each supported file's `relativePath`, the scanner stores one path-only row per visited subdirectory in the `directories` table (database version 2), so a folder with no Markdown or TXT descendant appears on its parent level. The browse level unions those rows with the parent segments derived from document paths, which keeps an index written before LT-3 browsable until its next successful scan.

The Library list opens a Library on tap and removes it on a confirmed long-press. `LibraryRepository.removeLibrary` deletes the Library row, relies on the Room cascade for documents and reading positions, deletes recent-read rows for that Library, and releases the persisted grant only when no other Library shares it.

Original files stay the source of truth. Removal must not delete or edit them.

## A1 — Keep the folder structure

Browse shows one directory level at a time.

- The root shows the selected folder's direct children.
- A subdirectory shows only that directory's direct children.
- Back returns to the parent level.
- At the current level, subdirectories are listed first, then files.
- Each group is ordered by its own name with case-insensitive order. The raw name breaks ties.
- A file opens the existing Reader route. This plan does not change reading, search, or positions.

LT-3 indexes one path-only row per visited subdirectory during the existing scan. A folder with no indexed Markdown or TXT file — empty, or holding only unsupported files — therefore appears on its parent level and uses the LT-2 empty placeholder when opened. Directory rows carry no file body, open no stream, and never copy an original. A scan that fails, overflows, or is cancelled commits no directory writes and keeps the previous rows, and a walk that cannot prove a subtree was enumerated deletes none.

## A2 — Long-press removes the selected Library

On the Library list, a long-press targets one Library: the folder the user added. Confirmation states that only the local reference and index are removed. After confirmation, the app calls the existing `removeLibrary` path.

Removed local data:

- the Library row
- indexed Document rows for that Library
- reading positions for those documents
- recent-read rows for that Library

The original files stay byte-for-byte. The grant is released only when it is not shared. Long-press on a subdirectory inside browse is not this feature.

## Decisions

- Drill-down, one level at a time, is the browse model. An indented dump of the whole tree is not.
- Sort is per level: directories, then files, each by name. It is not a single sort of every `relativePath` in the Library.
- Library removal reuses `removeLibrary`. It does not add a second delete path and does not delete SAF originals. The `directories` table joins the Room cascade, so removing a Library clears its folder rows with its other local rows.
- A1 and A2 can ship separately. A2 does not wait for the browse change.
- Both flavors share the screens. E-Ink keeps static text and a static confirmation, with no ripple required for the gesture.
- LT-3 stores directory paths only. Deriving folders on the fly was kept as the browse fallback so a pre-LT-3 index needs no rewritten rows to stay browsable.

## Out of scope

- Deleting or renaming original files.
- Removing one subdirectory from the index while keeping the rest of the Library.
- Sort by time, size, or title.
- A new database table before LT-3. `directories` was added only by LT-3, as its own migration.
- Editor, sync, search across the Library, or a new dependency.

## Stop

LT-1–LT-4 are implemented. Do not add time/size sorting, per-subdirectory removal, or an original-file delete path.
