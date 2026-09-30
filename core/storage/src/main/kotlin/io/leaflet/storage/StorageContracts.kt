package io.leaflet.storage

import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.model.ScanJobId
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Library scan lifecycle. Progress (when SCANNING) is a static, throttled
 * snapshot — never a high-frequency animation driver (E-Ink constraint).
 */
enum class ScanState {
    IDLE,
    SCANNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** Terminal scan outcomes recorded per library (T05 scanner reports these). */
enum class ScanStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * One library's scan bookkeeping: when the current (or last) scan started
 * and where it stands. T05 extends this with file counts / current-dir
 * progress; the status enum is frozen here so observers never branch on a
 * string.
 */
data class ScanRecord(
    val libraryId: String,
    val startedAt: Long,
    val status: ScanStatus,
) {
    companion object {
        fun running(
            startedAt: Long,
            libraryId: String = "",
        ): ScanRecord = ScanRecord(libraryId, startedAt, ScanStatus.RUNNING)
    }
}

/**
 * Minimal T01 contract surface (frozen in `docs/architecture/overview.md` §3),
 * extended T04 with the scan record observers T05 builds on.
 *
 * Removing a library clears local references and auxiliary rows only — never
 * source files (ADR-0001, REQ-0001 FR-8).
 */
interface LibraryRepository {
    suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library

    fun observeLibraries(): Flow<List<Library>>

    suspend fun removeLibrary(libraryId: LibraryId)

    suspend fun refreshLibrary(libraryId: LibraryId): ScanJobId

    fun observeScanState(libraryId: LibraryId): Flow<ScanState>
}

/**
 * Reader-side document boundary: read-only stream access plus in-tree
 * relative resolution.
 *
 * Resolution rules (frozen): tree-internal `..` allowed, escaping the
 * authorized tree returns null, unknown schemes return null. Stream
 * ownership: the CALLER closes the returned stream (use `use {}`).
 */
interface DocumentSource {
    suspend fun openStream(documentId: DocumentId): InputStream

    suspend fun resolveRelative(
        fromDocumentId: DocumentId,
        relativeRef: String,
    ): ResolvedResource?
}

/** Result of in-library relative resolution; null means "not resolvable here". */
data class ResolvedResource(
    val documentId: DocumentId?,
    val displayName: String,
)
