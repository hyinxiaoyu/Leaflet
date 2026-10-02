# REQ-0001 — Folder-first Library

English | [中文](REQ-0001-folder-library.zh.md)

Status: IN_PROGRESS
Priority: P0
Milestone: M0 (T04 storage foundation → T05 scanning and browsing; entry point for the M0 flow)

Source: `docs/archive/init.md` §11 (Library model), §12 (do not copy originals), §13 (folder scan), §26 (MVP library operations); implementation-plan T04/T05; `docs/research/eink-readers.md` §5 (folder-first workflow).

## Problem

Users already keep large Markdown/TXT collections in device folders and continue updating them with Syncthing, Git, Termux, PCs, agents, and scripts. A reader that requires importing files as books or copying them into app storage conflicts with this workflow: copies become stale and users cannot trust what the reader displays. Leaflet needs a folder-first model in which a Library is a reference to a user folder plus a metadata index, while original files remain authoritative.

## User Stories

- As a document organizer, I want to add an Android folder as a Library and browse its existing hierarchy, so that I can read Markdown/TXT without importing or copying files.
- As a user with multiple sources, I want separate Libraries for sources such as AI Documents, Work Notes, and Research, so that each can be refreshed or removed independently.
- As a user whose files are synchronized externally, I want a manual refresh to show added, changed, and deleted files, so that the reader stays aligned with the originals.

## Scope

- Manage multiple Libraries with `ACTION_OPEN_DOCUMENT_TREE`, persistent URI permission via `takePersistableUriPermission`, display, and reference removal.
- Recursively enumerate metadata for `.md`, `.markdown`, and `.txt` files (case-insensitive). Store `relativePath`, `uri`, `displayName`, `extension`, `size`, and `lastModified`; preserve folder hierarchy for browsing.
- Skip content-related work only when both mtime and size are reliable and match the index. Missing or unreliable metadata must not be treated as proof that a file is unchanged.
- Provide explicit Library-level or whole-library manual refresh. The initial scan indexes metadata only and does not parse full text.
- Expose scan state and static/throttled progress (scanned file count and current directory); allow cancellation. Concurrent refreshes for one Library are merged or serialized so scans cannot interleave index writes.
- Remove missing entries only after a complete successful scan. Partial failure, permission error, or cancellation must not treat unscanned entries as deleted.
- Represent revoked permission and missing files as recoverable errors with retry or reauthorization.

## Non-goals

All implementation-plan §1 exclusions apply: editor, accounts, cloud sync/storage, AI Chat, RAG/vector database, online AI, PDF/EPUB/CBZ, handwriting, OCR, advanced annotation, backlinks/knowledge base, plugins, WebDAV/Git/Syncthing clients, full tagging, online store, background FileObserver, library-wide FTS (MVP search is only the open document; FTS is M4), default copies of originals, two-column layout in M0/M1, and complex animation. Architecture must not assume the app will always be single-column.

## User Experience

1. Empty Library list shows a static “No libraries yet” message and an “Add Library” button. E-Ink uses static text and count progress, not a spinning loader.
2. Add flow: tap Add → system folder picker → confirm → show the Library under an editable `displayName` (default: selected folder name) with “Scanning (n/m)” → show file count and `lastScannedAt` on completion.
3. Adding the same normalized tree URI again does not create another Library. Navigate to the existing one and show “Already exists”; renaming its `displayName` does not change the deduplication key.
4. Browse Library → folder tree at the original hierarchy → document entries show the REQ-0002 title plus relative path. Empty folders and folders without supported files show explicit placeholders.
5. Refresh is available on the Library row and detail page. During scanning, update static progress at intervals of at least 500 ms and show Cancel. Cancellation retains the last complete index and says so.
6. Revoked permission offers reauthorization; partial failure says the last complete results were retained and no entries were deleted, with a retry action.
7. Removing a Library requires confirmation that only local references/indexes are removed. Delete local rows and release a persisted grant only when no other Library shares it.

## Functional Requirements

- **FR-1 Add:** obtain a tree URI with `ACTION_OPEN_DOCUMENT_TREE`, persist read permission, and create `Library{id, displayName, rootTreeUri, createdAt, lastScannedAt}`. Normalize and deduplicate repeated tree URIs.
- **FR-2 Discover recursively:** enumerate metadata (BFS/DFS is implementation-defined) for `.md/.markdown/.txt`, case-insensitively. Create `Document{libraryId, relativePath, uri, displayName, extension, size, lastModified}`. `relativePath` is rooted at the selected tree and uses `/` separators.
- **FR-3 No full-text parse on initial scan:** initial scan and normal refresh read only path, filename, size, mtime, and URI. Do not open streams to parse Markdown, extract titles/headings, or build FTS.
- **FR-4 Incremental scan:** when both mtime and size are available and exactly match a stored record, skip that file. If either is missing or unreliable (for example, provider returns zero), treat the file as potentially changed and reread as needed.
- **FR-5 Missing-entry cleanup:** delete stored Document rows absent from the scan only after traversal completes with no permission error or cancellation. Otherwise retain the old index.
- **FR-6 Manual refresh:** provide a cancellable Library-level scan that stops enumeration and index writes within one second after cancellation. Concurrent requests for one Library must be merged or serialized.
- **FR-7 Progress:** expose `Idle/Scanning(fileCount, currentDir)/Cancelling/Succeeded/Failed(reason)/Cancelled`; throttle callbacks to at most one update per 500 ms for static UI display.
- **FR-8 Removal:** delete only local Library/Document/position references. Do not modify or delete originals. Before releasing a grant, check whether another Library still uses it.
- **FR-9 Missing metadata:** a failed query for an individual entry must not abort the entire scan. Record the entry-level error and continue; expose count and reasons in the completion state.
- **FR-10 Read-only boundary:** scanning is read-only, never writes or copies originals into private storage, and never requests `MANAGE_EXTERNAL_STORAGE`.

## Acceptance Criteria

- **AC-1:** Given a tree with three nested levels, 20 Markdown files, 5 TXT files, 3 PNGs, and one empty folder, adding it creates 25 Document rows and preserves browsing hierarchy/placeholders; no full-text parse is called.
- **AC-2:** Adding the same tree URI again leaves the Library count unchanged, opens the existing Library, and reports that it already exists.
- **AC-3:** After external addition of two Markdown files, modification of one file, deletion of one file, and manual refresh, the list reflects all changes.
- **AC-4:** A test provider may skip a file whose content changed while mtime and size stayed identical; if mtime is unavailable, the file must be reread rather than marked unchanged.
- **AC-5:** Mid-scan cancellation stops new index writes within one second, retains the complete prior index, reports Cancelled, and shows the prior completion time.
- **AC-6:** A provider that throws a permission error midway reports Failed, deletes zero old rows, and shows retry.
- **AC-7:** Removing a Library clears its local Document/position rows while originals remain byte-identical. Release a persisted grant only when no other Library references it.
- **AC-8:** ACs assert observable rows, lists, states, and checksums; do not use unmeasurable statements such as “supports X.”

## Edge Cases

Handle empty or unsupported-only folders as successful zero-document Libraries; deep trees (15+ levels) and 5,000+ entries without ANR or uncancellable work; filenames with spaces, Chinese, emoji, and mixed-case extensions; the same tree granted with different names; persisted access after process recreation; permission revocation without crash; and providers that return size zero or missing mtime as unreliable metadata.

## E-Ink Considerations

Use static text, item counts, and a fine progress bar throttled to at least 500 ms. No spinner, shimmer, or crossfade. Use high-contrast solid text and static hit areas for refresh/cancel; no ripple or only a very weak ripple, according to DisplayProfile (REQ-0004).

## Standard Android Considerations

Standard mode may use a subtle ripple and normal scrolling, but scan progress still must not use complex transitions. Both modes share the same scan state machine; only visual intensity differs through DisplayProfile.

## Performance Considerations

Scan time is linear in entry count. Never enumerate on the main thread. Close each `ContentResolver` cursor. Propagate cancellation through coroutines. Numeric thresholds for entry count/time are unverified; fill them from `docs/performance` after T03/T13. This REQ freezes cancellability, no ANR, and no interleaved writes, not an unmeasured speed target.

## Storage / Data Impact

Room stores `Library` and `Document` metadata with a `libraryId` foreign key. Store URI strings and metadata only; never document bodies or absolute filesystem paths. Resolve through the tree URI plus `relativePath`. DataStore does not store scan results. Original bytes must remain unchanged after database deletion, uninstall, or Library removal (AC-7).

## Test Plan

- JVM tests with a fake provider for AC-1–AC-4 and AC-6, including deduplication, incremental rules, cleanup, slow queries, missing metadata, deep trees, and mid-scan errors.
- Instrumented test against at least one real SAF provider for add → browse → refresh → remove, with checksums before and after.
- Recreate the process after persisting permission, then verify the Library remains visible and refreshable.
- Do not test library-wide FTS or real-time monitoring; both are non-goals.

## Dependencies

Requires T02 project foundation, T04 read-only SAF/persisted grants/DAO contract, and T03 samples/performance template. Enables T07 folder-to-Reader navigation, REQ-0002 on-demand title parsing, and REQ-0003 position/recent-read foreign keys.

## Additions (2026-09-28)

A1 and A2 are implemented, including the LT-3 directory metadata, in [Library browse and removal tasks](../plans/library-browse-and-remove-tasks.md).

### A1 — Browse keeps the folder structure

The Library browse screen shows one directory level at a time, matching the selected folder's hierarchy. It does not lay every indexed document out in a single flat list.

At the current level, subdirectories come first and files come after them. Each group is ordered by its own name using case-insensitive order, with the raw name as a tie-break. Entering a subdirectory shows only that directory's children. Back returns to the parent. The Library root shows the root's children.

This addition does not change which files are indexed. Besides each supported file, a scan stores one path-only row per visited subdirectory, so a folder with no indexed Markdown or TXT descendant (empty, or holding only unsupported files) appears on its parent level. Directory rows carry no file body and no copy of an original, are replaced only by a complete successful scan, and show the browse screen's empty placeholder when opened.

### A2 — Long-press removes the selected Library

On the Library list, a long-press on one Library asks for confirmation and then removes that Library. The Library is the folder the user selected when adding it.

Removal deletes the local Library row, its indexed Document rows, its indexed directory rows, the reading positions that belong to those documents, and recent-read rows for that Library. It does not delete or modify the original files. A persisted grant is released only when no other Library still uses it. The confirmation states that only the local reference and index are removed.

Long-press inside the browse tree does not remove a subdirectory. That gesture is outside this addition.

## Code review round-2 follow-up (2026-10-01)

The [round-2 review](code-review-2026-10-01-round-2.md) reproduced a scan-safety defect against FR-9
and the "absence evidence" rule.

- **CR-20261001-05 — incomplete directory metadata authorized a cleanup.** A provider row that still
  declared the directory MIME type but had lost its display name was rebuilt as a "file" with an
  empty identity, so the next scan treated the folder as vanished and deleted its previously indexed
  documents (source files untouched; the auxiliary position rows cascade with them). Two rules now
  close it: row classification reads the columns that ARE present (`classifyTreeRow` in
  `TreeLister` — the MIME type decides the directory bit even when the name or id is missing), and
  `LibraryScanner` treats ANY row it could not fully classify as insufficient absence evidence for
  the walk, so cleanup is skipped while the run still succeeds and reports the row as an entry error.
  A row that states no MIME type at all is marked `TreeEntry.typeUnknown` and is neither indexed as a
  document nor accepted as absence evidence even when its id and display name are intact (the
  independent acceptance's missing-MIME-only boundary). The parser's row → entry projection
  (`readTreeEntry`) derives the emitted entry from the classifier, so the marker reaches the scanner
  through the production cursor path instead of being rebuilt field by field.
  Regressions: `TreeRowClassificationTest` (nameless directory, identity-less directory,
  unclassifiable row, unknown-MIME classification, and the scan scenarios — including
  missing-MIME-only — asserting `deleted = 0` with the old document kept) and the existing
  `LibraryScannerTest` suite. Real-provider/cursor and instrumented verification remain
  NOT_RUN.

## Open Questions

- **Enumeration cap:** the 500 indexed-document cap was removed. Scans index every supported document they find. A 5,000-entry visit guard remains (directories, files, and malformed rows). Exceeding it fails explicitly and retains the old index (`LibraryScanner` tests). The guard value is unverified against a device baseline.
- **`contentHash`:** not introduced in M0/M1; use mtime + size only. `mtime=0`/missing and `size=-1` are unknown sentinels and never qualify for skip. Hash algorithm and cost are unverified.
- **Tree URI aliases:** normalized identical URIs are deduplicated; textually different aliases to the same grant remain distinct Libraries (known limitation). Removal checks shared grants. Real-device provider alias behavior remains unverified (T13).
- **Performance thresholds:** cold-start, scan duration, and peak memory await T03 samples and T13 real-device measurements.
