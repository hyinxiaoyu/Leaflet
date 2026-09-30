package io.leaflet.storage

/**
 * Explicit, recoverable storage failures (REQ-0001 failure states, REQ-0003 FR-6).
 *
 * Every failure a caller can recover from (re-authorize, retry, remove the
 * dead entry) surfaces as one of these typed exceptions — UI layers map them
 * to `ReaderState.Failed` / scan `FAILED` plus a recovery action, and MUST
 * never see an untyped crash for an anticipated condition.
 *
 * Coroutine cancellation is NEVER wrapped: [kotlinx.coroutines.CancellationException]
 * always propagates untouched so scans and loads stay cancellable (storage
 * architecture §3.3). Implementations rethrow it before any catch-all.
 */
sealed class StorageException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    /** `refreshLibrary` (or a lookup) named a library row that does not exist. */
    class LibraryNotFound(val libraryId: String) :
        StorageException("Library not found: $libraryId")

    /**
     * Persisting the read grant for a newly added tree failed (user denied,
     * picker canceled before grant, provider refused). The library row is
     * rolled back, so a denied add leaves nothing behind.
     */
    class PermissionDenied(val treeUri: String, cause: Throwable) :
        StorageException("Persistable read permission denied for tree: $treeUri", cause)

    /**
     * A previously granted tree can no longer be read (user revoked the grant
     * in system settings, provider withdrew it). Local rows are kept; the UI
     * offers re-authorization. The original files are unaffected.
     */
    class PermissionLost(val uri: String, cause: Throwable) :
        StorageException("Persisted permission no longer grants read access: $uri", cause)

    /** The indexed document can no longer be opened (deleted, renamed, moved away). */
    class DocumentGone(val documentId: String, cause: Throwable?) :
        StorageException("Document is no longer reachable: $documentId", cause)

    /** The stream failed for a reason that is neither loss of grant nor a gone file. */
    class ReadFailed(val documentId: String, cause: Throwable) :
        StorageException("Failed to read document: $documentId", cause)
}
