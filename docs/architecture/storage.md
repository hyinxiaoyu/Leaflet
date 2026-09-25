# Storage Architecture

English | [中文](storage.zh.md)

Date: 2026-09-17
Upstream: `overview.md` §3 contracts 1/2/3/5; ADR-0001 and ADR-0002.
Requirements: REQ-0001 (Library/scanning), REQ-0003 (position/recent items), REQ-0010 (Settings/About DataStore portion).


## English Overview

This document defines storage contracts for ADR-0001/0002 and REQ-0001/0003/0010. Room stores Library metadata, Document metadata, versioned ReadingPosition, and a bounded MRU list. Original document bodies are never stored. Library removal cascades local rows and explicitly clears recent snapshots, but never touches files in SAF. Room may reserve schema room for future M4 FTS; M0/M1 creates no FTS table. DataStore stores display and reading preferences and lightweight UI state, not document content or large collections.

SAF access is read-only. Add a folder with `ACTION_OPEN_DOCUMENT_TREE` and persist its read grant. Release a grant only when no Library shares it. `DocumentSource` is the only Reader-facing document boundary: open a read-only stream by stable document ID and resolve relative resources within the authorized tree. Streams must be closed; revoked permissions, missing files, cancellation, and unknown schemes become recoverable states. Remote images never load automatically, and external links are handed to the OS only after a user tap.

The initial scan indexes metadata only and does not parse full text, titles, or TOC. A later scan may skip content-related work only when reliable mtime and size both match; missing/unknown metadata is never treated as proof that a file is unchanged. Delete missing index rows only after a complete successful scan. Partial failure, permission error, cancellation, or enumeration overflow preserves the prior index. Scans are cancellable, serialized per Library, and report static progress at intervals of at least 500 ms.

The scanner recursively discovers case-insensitive `.md`, `.markdown`, and `.txt`, preserves relative folder structure, and deduplicates normalized tree URIs. Current limits are 5,000 visited entries and 500 indexed documents; exceeding either fails explicitly and retains the old index. These limits remain unverified against performance baselines. Settings restore after process recreation; corrupted preferences fall back to defaults.

## 1. Room Tables

```text
Library
--------
libraryId     : String (PK, stable app-internal UUID)
displayName   : String (user label, e.g. "AI Documents")
rootTreeUri   : String (SAF tree URI; for display/grant restoration, not path concatenation)
createdAt     : Long
lastScannedAt : Long?

Document
--------
documentId   : String (PK, stable app-internal ID; see overview contract 1)
libraryId    : String (FK → Library.libraryId, CASCADE local rows only)
relativePath : String (in-library path for display/hierarchy)
uri          : String (content URI for access; not assumed stable after rename)
displayName  : String (filename)
title        : String? (parsed by title rules; may fall back)
extension    : String (md / markdown / txt, normalized lowercase)
size         : Long
lastModified : Long
contentHash  : String? (optional; null is allowed in M0/M1)

ReadingPosition (one versioned row per documentId)
--------
documentId  : String (PK/FK → Document.documentId)
version     : Int (contract version, default 1)
blockIndex  : Int
blockOffset : Int
progress    : Float (0..1)
anchor      : String? (heading anchor or quoted text)
updatedAt   : Long

RecentRead (MRU, cap 30 in T04; T03/T13 may revise from baseline)
--------
documentId   : String (PK, no FK; retain snapshot after source deletion/revocation)
libraryId    : String (used for cleanup, no FK)
titleSnapshot: String (title shown after deletion)
lastOpenedAt : Long
```

- Foreign keys: Document → Library CASCADE; ReadingPosition → Document CASCADE; RecentRead has no FK. Removing a Library cascades local rows and Repository explicitly clears recents; it **never touches SAF originals** (ADR-0001).
- Never store file bodies in any table. Database contains auxiliary index/progress/settings/UI state only.
- Reserve Room schema extensibility for future FTS (M4), but do not create an FTS table or library-wide index in M0/M1.
- Schema baseline is `core/storage/schemas/<db-fqn>/1.json`; every version change needs a tested Migration (see `LeafletDatabase` KDoc). For the noted KSP build issue, stop Gradle and clear module `build/` before a clean check.

## 2. Read-only SAF Access

- Add Library using `ACTION_OPEN_DOCUMENT_TREE` plus `takePersistableUriPermission(READ)`; save `rootTreeUri` string.
- Removing a Library clears only local references/cascade rows. Release a tree grant only when no other Library shares it.
- `DocumentSource` (overview contract 3) is the sole document access exit: open a read-only stream by ID (caller closes it); resolve relative resources within Library. Allow `..` inside tree, reject escape, do not concatenate content URIs, normalize/encode URI consistently, and never execute unknown schemes.
- Lost permission, missing file, cancelled picker, or denied access become recoverable `ReaderState.Failed` or scan `FAILED/CANCELLED`; never crash.
- Offline by default: remote images remain static placeholders; external links go to another app only after tap; missing external handler must not crash.

## 3. Scan Rules (T05)

### 3.1 Incremental Rules

- First scan reads only path/filename/size/mtime/URI and creates Document metadata; **no full-text parse**, title extraction, or TOC. Display title may initially fall back to filename without extension; T06/REQ-0002 extracts content title on open.
- Later scan may skip only when mtime is nonzero/present and size are both reliable and match stored values (`LibraryScanner.unchanged`). Missing/unreliable values are **not evidence of unchanged**; re-index metadata. Store unknown sentinels `size=-1`/`mtime=0`, which never qualify for skip.
- A future `contentHash` is a separate task; do not choose an algorithm here.

### 3.2 Deletion Commit Rule

- Clean disappeared entries only after a **complete successful scan** (remove local row; position cascades; original file remains). A malformed directory row lacking ID/name makes subtree evidence uncertain, so skip cleanup but report success with a notice.
- Partial failure, permission error, cancellation, or enumeration overflow must **not** treat unscanned entries as deleted. Failure/cancel/overflow performs zero index writes and retains old rows.

### 3.3 Cancellation and Concurrency

- Scan is cancellable; check coroutine cancellation per directory/entry. Close cursors in lister `use {}`. On cancel, status is CANCELLED and cleanup is not committed.
- Per-Library refreshes are serialized using a mutex (FR-6 permits merge or serial; implementation chose serial), with no interleaved writes.
- Progress uses static/throttled `ScanProgress` at ≥500 ms, with text/count and no animation.

### 3.4 Discovery and Browsing

- Recursively query child documents through `DocumentsContract` (BFS). Discover `.md/.markdown/.txt` case-insensitively and preserve hierarchy. Empty/unsupported-only tree succeeds with zero docs. Normalize/deduplicate tree URI and show “already exists” on duplicate.
- Sort names case-insensitively per directory and globally by relativePath before commit. T05 provider tests cover 18-level depth, mid-walk failure, slow queries, missing metadata; stub tree provider was exercised through real ContentResolver path.
- Enumeration caps (mdreader reference; UNVERIFIED until T03/T13 baseline): 5,000 visited entries and 500 indexed documents per scan. Exceeding caps explicitly fails, reports reason, retains old index, offers retry; never silently truncates.

## 4. DataStore Responsibilities

- Store mode, font size/line spacing/margins/font choice, page overlap (3–8%, default 5%), menu and other lightweight UI state.
- Do not store document bodies, a replacement for SAF grants, or large collections (full recent list belongs in Room; DataStore keeps small switches).
- Corrupt preferences fall back to empty/default values; authorization/settings remain available after process recreation.
