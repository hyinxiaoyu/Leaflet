package io.leaflet.storage.repo

import io.leaflet.model.Document
import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.model.ScanJobId
import io.leaflet.storage.LibraryRepository
import io.leaflet.storage.ScanRecord
import io.leaflet.storage.ScanState
import io.leaflet.storage.ScanStatus
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DirectoryDao
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.LibraryDao
import io.leaflet.storage.db.LibraryEntity
import io.leaflet.storage.db.ReadingPositionDao
import io.leaflet.storage.db.RecentReadDao
import io.leaflet.storage.db.fingerprintOf
import io.leaflet.storage.db.toEntity
import io.leaflet.storage.db.toModel
import io.leaflet.storage.saf.ContentAccess
import io.leaflet.storage.saf.TreeUris
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * T04 [LibraryRepository] foundation over Room + [ContentAccess].
 *
 * Owns: library add/list/remove-reference, scan-state bookkeeping for T05's
 * scanner (each library holds one slot: `IDLE` initial, `SCANNING` while a
 * refresh job runs, `SUCCEEDED`/`FAILED`/`CANCELLED` terminal until the next
 * refresh), reading-position save/load, and recent-read MRU with cap.
 *
 * Removal semantics (REQ-0001 FR-8/AC-7): [removeLibrary] deletes the library
 * row — Room cascades to its documents, indexed directory rows, and positions,
 * and this method deletes
 * the library's recent rows explicitly (recent rows carry no FK so dead
 * entries stay recoverable, REQ-0003 FR-6). Source files are never touched:
 * no method here opens a source stream, writes to a provider, or deletes
 * anything outside Room. The persisted grant is released ONLY when no
 * remaining library references the same normalized tree URI; releases never
 * throw out of this method (a failed release is a no-op — keeping a grant is
 * always safe, dropping one early would strand a sibling library).
 *
 * Cancellation and failure transparency: [CancellationException] propagates
 * untouched (never converted to `FAILED`, never swallowed); a denied grant
 * on add rolls the row back and throws [StorageException.PermissionDenied].
 * Blocking I/O runs on [ioDispatcher].
 *
 * Coroutine scope: this class launches nothing. T05's scanner owns the
 * refresh job and reports into [noteScanStarted]/[noteScanFinished]; this
 * class only guards the state transitions with a per-library [Mutex] so
 * concurrent refreshes of the same library serialize instead of interleaving
 * writes (REQ-0001 FR-6; real merging lives in T05).
 */
class LibraryRepositoryImpl(
    db: LeafletDatabaseHandle,
    private val access: ContentAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { java.util.UUID.randomUUID().toString() },
) : LibraryRepository {
    private val libraries: LibraryDao = db.libraryDao()
    private val documents: DocumentDao = db.documentDao()
    private val directories: DirectoryDao = db.directoryDao()
    private val positions: ReadingPositionDao = db.readingPositionDao()
    private val recents: RecentReadDao = db.recentReadDao()

    private val scanMutexes = mutableMapOf<String, Mutex>()
    private val scanMutexGuard = Mutex()
    private val scanStates = MutableStateFlow<Map<String, ScanRecord>>(emptyMap())

    override suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library =
        withContext(ioDispatcher) {
            val normalized = TreeUris.normalize(rootTreeUri)
            libraries.findByTreeUri(normalized)?.let { return@withContext it.toModel() }
            val now = clock()
            val row =
                LibraryEntity(
                    libraryId = idGenerator(),
                    displayName = displayName.ifBlank { normalized.substringAfterLast('/') },
                    rootTreeUri = normalized,
                    createdAt = now,
                    lastScannedAt = null,
                )
            libraries.insert(row)
            try {
                access.takeReadPermission(normalized)
            } catch (e: CancellationException) {
                libraries.deleteById(row.libraryId)
                throw e
            } catch (e: Exception) {
                // Denied/refused grant: roll the row back so a failed add
                // leaves nothing behind, then report the typed failure.
                libraries.deleteById(row.libraryId)
                throw StorageException.PermissionDenied(normalized, e)
            }
            row.toModel()
        }

    override fun observeLibraries(): Flow<List<Library>> = libraries.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun removeLibrary(libraryId: LibraryId): Unit =
        withContext(ioDispatcher) {
            val row = libraries.findById(libraryId.value) ?: return@withContext
            val sharedCount = libraries.list().count { it.rootTreeUri == row.rootTreeUri }
            libraries.deleteById(row.libraryId)
            recents.deleteByLibrary(row.libraryId)
            // Release the grant only when no remaining library shares it.
            // Stored URIs are already normalized; TreeUris.normalize is
            // idempotent, so raw-vs-normalized comparison is equivalent.
            if (sharedCount <= 1) {
                try {
                    access.releaseReadPermission(row.rootTreeUri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keeping a grant is always safe; never fail removal.
                }
            }
        }

    override suspend fun refreshLibrary(libraryId: LibraryId): ScanJobId =
        withContext(ioDispatcher) {
            libraries.findById(libraryId.value)
                ?: throw StorageException.LibraryNotFound(libraryId.value)
            // T05 owns the scanner. This foundation records the intent so
            // observers see SCANNING immediately; T05's scanner reports the
            // terminal state via noteScanFinished.
            noteScanStarted(libraryId.value)
            ScanJobId(idGenerator())
        }

    override fun observeScanState(libraryId: LibraryId): Flow<ScanState> =
        scanStates.map { states ->
            when (states[libraryId.value]?.status) {
                ScanStatus.SUCCEEDED -> ScanState.SUCCEEDED
                ScanStatus.FAILED -> ScanState.FAILED
                ScanStatus.CANCELLED -> ScanState.CANCELLED
                ScanStatus.RUNNING -> ScanState.SCANNING
                null -> ScanState.IDLE
            }
        }

    // -- Scan bookkeeping for T05 -------------------------------------------------

    /** Marks a library SCANNING; concurrent calls serialize on a per-library mutex. */
    suspend fun noteScanStarted(libraryId: String) {
        mutexFor(libraryId).withLock {
            val record = ScanRecord(libraryId, clock(), ScanStatus.RUNNING)
            scanStates.update { it + (libraryId to record) }
        }
    }

    /**
     * Records the terminal state of a scan. A cancelled scan keeps prior rows
     * (callers must NOT submit deletions on cancel); a failed scan likewise
     * submits nothing — only a succeeded full scan may commit removals
     * (REQ-0001 FR-5).
     */
    suspend fun noteScanFinished(
        libraryId: String,
        status: ScanStatus,
    ) {
        mutexFor(libraryId).withLock {
            val started = scanStates.value[libraryId]?.startedAt ?: clock()
            scanStates.update {
                it + (libraryId to ScanRecord(libraryId, started, status))
            }
        }
    }

    private suspend fun mutexFor(libraryId: String): Mutex =
        scanMutexGuard.withLock {
            scanMutexes.getOrPut(libraryId) { Mutex() }
        }

    // -- Documents (read side for T05/T07; writes arrive with the T05 scanner) --

    suspend fun listDocuments(libraryId: LibraryId): List<Document> =
        withContext(ioDispatcher) {
            documents.listByLibrary(libraryId.value).map { it.toModel() }
        }

    fun observeDocuments(libraryId: LibraryId): Flow<List<Document>> =
        documents.observeByLibrary(libraryId.value).map { rows -> rows.map { it.toModel() } }

    /**
     * Relative paths of the indexed directories of one library (REQ-0001 A1 /
     * LT-3). Path-only rows; no body text and no original-file access. Read
     * side for the browse screen, which unions these with the parent segments
     * derived from document paths so a pre-LT-3 index stays browsable.
     */
    suspend fun listDirectories(libraryId: LibraryId): List<String> =
        withContext(ioDispatcher) {
            directories.listByLibrary(libraryId.value).map { it.relativePath }
        }

    // -- Reading positions (T07 save/restore; fingerprint feeds staleness) --------

    suspend fun savePosition(
        position: ReadingPosition,
        lastModified: Long,
        size: Long,
    ): Unit =
        withContext(ioDispatcher) {
            positions.upsert(position.toEntity(clock(), fingerprintOf(lastModified, size)))
        }

    suspend fun loadPosition(documentId: DocumentId): ReadingPosition? =
        withContext(ioDispatcher) {
            positions.findByDocument(documentId.value)?.toModel()
        }

    /**
     * Position + the fingerprint RECORDED AT SAVE TIME (R2-05, REQ-0003
     * FR-3): the staleness comparison needs the historical fingerprint,
     * not a fresh one rebuilt from the current document row (which would
     * always equal the live fingerprint and make every restore "exact" —
     * F06). Null when no row was ever saved.
     */
    suspend fun loadPositionWithFingerprint(documentId: DocumentId): StoredPositionSnapshot? =
        withContext(ioDispatcher) {
            positions.findByDocument(documentId.value)?.let {
                StoredPositionSnapshot(it.toModel(), it.fingerprint)
            }
        }

    /** Reader-route helper (T07): raw index row for fingerprint/health checks. */
    suspend fun documentRowForReader(documentId: DocumentId): DocumentRowSnapshot? =
        withContext(ioDispatcher) {
            documents.findById(documentId.value)?.let {
                DocumentRowSnapshot(it.displayName, it.lastModified, it.size)
            }
        }

    /** Reader-route helper (T07): owning library display name for recent rows. */
    suspend fun libraryNameForReader(libraryId: LibraryId): String? =
        withContext(ioDispatcher) {
            libraries.findById(libraryId.value)?.displayName
        }

    /**
     * Reader-route helper (T07): index-row existence check feeding the recent
     * list's health probe. A present row means "indexed"; stream readability
     * (gone vs revoked) is probed by the app layer through the real
     * `DocumentSource`, whose typed exceptions propagate to the caller.
     */
    suspend fun hasDocumentRow(documentId: DocumentId): Boolean =
        withContext(ioDispatcher) {
            documents.findById(documentId.value) != null
        }

    // -- Recent reads (MRU, cap honored on every write) ---------------------------

    suspend fun recordRecent(entry: RecentRead): Unit =
        withContext(ioDispatcher) {
            recents.upsert(entry.toEntity())
            recents.trimToNewest(RECENT_CAP)
        }

    suspend fun listRecent(limit: Int = RECENT_CAP): List<RecentRead> =
        withContext(ioDispatcher) {
            recents.listRecent(limit.coerceAtLeast(1)).map { it.toModel() }
        }

    fun observeRecent(limit: Int = RECENT_CAP): Flow<List<RecentRead>> =
        recents.observeRecent(limit.coerceAtLeast(1)).map { rows -> rows.map { it.toModel() } }

    suspend fun removeRecent(documentId: DocumentId): Unit =
        withContext(ioDispatcher) {
            recents.deleteByDocument(documentId.value)
        }

    suspend fun clearRecent(): Unit =
        withContext(ioDispatcher) {
            recents.clearAll()
        }

    companion object {
        /**
         * Recent-read cap, initial value 30 (REQ-0003 FR-5/AC-5; follows the
         * mdreader-30 precedent cited in REQ-0003 open questions). UNVERIFIED:
         * T13 baselines may re-tune; the value is centralized here so a
         * re-tune is one line plus this comment.
         */
        const val RECENT_CAP = 30
    }
}

/**
 * Minimal index snapshot for the Reader route (T07): display name plus the
 * `mtime`/`size` halves of the document fingerprint. Carries no URI and no
 * body text — the app layer rebuilds the opaque fingerprint via
 * `fingerprintOf` and opens streams only through `DocumentSource`.
 */
data class DocumentRowSnapshot(
    val displayName: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Saved position + its save-time fingerprint (R2-05): the exact pair the
 * restore planner compares against the live document fingerprint.
 */
data class StoredPositionSnapshot(
    val position: ReadingPosition,
    val fingerprint: String?,
)

/**
 * Narrow DAO bundle consumed by [LibraryRepositoryImpl].
 *
 * Production passes a [io.leaflet.storage.db.LeafletDatabase] (which
 * implements this interface structurally via its abstract DAO accessors);
 * JVM unit tests pass an in-memory fake. The indirection exists so
 * repository logic is testable without Robolectric or a device — it is not a
 * second database abstraction.
 */
interface LeafletDatabaseHandle {
    fun libraryDao(): LibraryDao

    fun documentDao(): DocumentDao

    fun directoryDao(): DirectoryDao

    fun readingPositionDao(): ReadingPositionDao

    fun recentReadDao(): RecentReadDao

    /**
     * Runs [block] atomically (R2-04): production delegates to Room's
     * `withTransaction` (all-or-nothing commit); the JVM fake runs the
     * block directly (single-threaded tests have no concurrent writers,
     * and atomicity there is asserted by comparing full snapshots).
     */
    suspend fun <R> transaction(block: suspend () -> R): R = block()
}
