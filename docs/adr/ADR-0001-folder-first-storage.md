# ADR-0001 — Folder-first Storage

English | [中文](ADR-0001-folder-first-storage.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

Leaflet is a local-first, folder-first Android reader (`docs/archive/init.md` §§1, 11–13). Users already organize Markdown/TXT files in folders and update them with AI tools, scripts, and sync tools. Leaflet should read a chosen Android folder as a Library without asking users to import files as books.

The alternative is an import-copy model that copies files into app-private storage. It creates two sources of truth, doubles storage use (especially costly on E-Ink devices), and hides external updates. Research on Markor (`docs/research/eink-readers.md` §3) supports the folder-as-library workflow; T05 turns it into scan and browse acceptance criteria.

### Decision

1. Original files are the single source of truth. The Leaflet database stores only indexes, reading position, future favorites, recent documents, settings, search indexes, and UI state (`docs/archive/init.md` §1).
2. By default, do not import or copy originals into app storage. Read the original files through SAF; do not store their contents in the database or create internal document copies (`docs/archive/init.md` §12).
3. Deleting the Leaflet database, removing a Library reference, clearing app data, or uninstalling must not affect original Markdown/TXT files. Removing a Library deletes only local references and auxiliary data. T04/T05 acceptance verifies this behavior.
4. Original content can be checked across devices or tools with external methods such as a file manager, Git, or Syncthing. T13 must verify that originals remain unchanged after removing a Library, clearing the database, or uninstalling.

### Alternatives considered

- **Import-copy:** predictable reads and stronger offline isolation, but conflicts with folder-first behavior, doubles storage, and hides external changes. Rejected; §12 of the initialization prompt explicitly disallows it by default.
- **Hybrid copy/reference:** copying small files and referencing large ones creates two consistency models and doubles the reasoning and bug surface. Not used in the MVP. If offline caching is needed later, a new ADR must define an invalidatable cache rather than treating a copy as owned content.

### Consequences

- `:core:storage` must provide read-only access, persistent authorization, and recoverable states for revoked permissions or missing files (ADR-0002).
- Scanning indexes metadata and parses documents on demand (`docs/archive/init.md` §13); the first scan does not parse full text. Position data and caches must be invalidated when a document changes (T07).
- T13 must measure original-file invariance before and after operations so “deleting the library does not delete files” is evidence-backed.
- Reading reliability depends on external files and permission state. Reader must handle missing files and revoked permissions as explicit error states.

### Follow-up

If private caching or offline snapshots are introduced, create a new ADR covering invalidation and storage limits and reaffirm that removing the database does not affect originals. Room schema evolution must not use clearing the database to hide lost reading positions or settings (implementation plan §8).
