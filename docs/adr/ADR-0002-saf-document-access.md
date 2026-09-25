# ADR-0002 — SAF Document Access

English | [中文](ADR-0002-saf-document-access.zh.md)

Status: Accepted
Date: 2026-09-17



### Context

Storage Access Framework (SAF) is Android's standard mechanism for accessing user-selected folders. Leaflet adds a Library through `ACTION_OPEN_DOCUMENT_TREE` and retains a URI permission so access survives process recreation (`docs/archive/init.md` §11). It must not replace content URIs with absolute filesystem paths or rely on `MANAGE_EXTERNAL_STORAGE` (T04 boundary).

Research in `docs/research/eink-readers.md` records a complete tree-grant → recursive metadata enumeration → cap → sort → stale-entry cleanup flow in mdreader-android, and a `FileRepository` boundary plus `takePersistableUriPermission` reopen pattern in Pilcrow.

### Decision

1. **Read-only SAF:** Request read permission only. Do not write, create, delete, or rename user files. The only permission-related operations on the selected tree are taking or releasing the grant. Release it only after its Library reference is removed and no other Library shares it; never release a still-shared grant (T04 acceptance).
2. **Persistent permission:** Store `rootTreeUri` and the persisted grant with the Library. It must remain usable after process recreation. Cancellation, denied or revoked permission, and missing files must have recoverable states; they must not crash or silently discard data.
3. **`DocumentSource` boundary:** Reader accesses documents only through this interface. It opens read-only streams by document ID and resolves relative Markdown links and images from the authorized tree and parent directory. Paths may use `..` only while remaining inside the tree; paths escaping the authorized root are rejected. Do not concatenate content URIs as filesystem paths. Use consistent URI encoding/normalization, and do not execute unknown schemes (T10 security boundary).
4. **Reader does not access SAF directly:** `:core:reader` must not access SAF, Room, or vendor SDKs (implementation plan §3 and AGENTS.md). App obtains document streams and metadata through Repository, then gives cancellable load work to Reader.
5. **Streams and coroutines:** Always close opened streams. Scan and load jobs must be cancellable and release resources on cancellation (T04/T06 acceptance).

### Alternatives considered

- **`MANAGE_EXTERNAL_STORAGE` / direct absolute paths:** simpler, but overly broad permission, higher privacy and Play-policy cost, and conflicts with scoped storage. Rejected.
- **MediaStore:** designed for media files, not arbitrary Markdown/TXT folders. Rejected.
- **Expose SAF to Reader:** fewer forwarding layers, but breaks module boundaries, prevents pure JVM Reader tests, and leaks permission semantics into UI. Rejected in favor of `DocumentSource`.

### Consequences

- `:core:storage` owns SAF, permission lifecycle, content-URI resolution, and traversal prevention. `:core:reader` depends only on `DocumentSource` (`docs/architecture/storage.md`).
- SAF instrumented tests must cover slow queries, missing metadata, deep folders, and mid-scan failures; at least one real SAF provider must be tested (T05).
- Relative-resource resolution is a security boundary. T10 tests must cover spaces and Chinese paths, nested folders, `../` escape attempts, duplicate heading fragments, and missing targets.

### Follow-up

Real-time background monitoring with FileObserver is out of M0/M1 (T05 stop condition). If needed, a new ADR must assess E-Ink power use and stability. REQ-0001 defines the recovery UX after permission loss; this ADR requires the state to be recoverable.
