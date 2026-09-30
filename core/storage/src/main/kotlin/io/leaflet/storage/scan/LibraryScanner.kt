package io.leaflet.storage.scan

import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanStatus
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DirectoryDao
import io.leaflet.storage.db.DirectoryEntity
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LibraryDao
import io.leaflet.storage.repo.LeafletDatabaseHandle
import io.leaflet.storage.repo.LibraryRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Recursive metadata-only library scanner (REQ-0001 FR-2–FR-7, T05).
 *
 * Walks one library's SAF tree breadth-first through [TreeLister], indexes
 * `.md` / `.markdown` / `.txt` files (case-insensitive) into Room, and
 * reports static throttled progress. The scanner NEVER opens a file stream:
 * only path / name / size / mtime / URI metadata crosses the boundary
 * (REQ-0001 FR-3 — first scan and refreshes never parse full text; the
 * filename-without-extension stored in [DocumentEntity.title] is a
 * lightweight display fallback until T06/REQ-0002 extracts real titles).
 *
 * Rules, each pinned by a JVM test:
 * - Incremental skip (FR-4): an entry is skipped only when the fresh size AND
 *   mtime are both known (mtime `0`/missing never counts) and both equal a
 *   previously stored reliable pair. Anything missing or unreliable is
 *   re-indexed, never declared "unchanged". Unknown metadata is stored with
 *   [UNKNOWN_SIZE] / [UNKNOWN_MTIME] sentinels so it can never match a skip.
 * - Disappearance cleanup (FR-5): rows absent from the walk are deleted ONLY
 *   after a fully completed walk with no failure, no cancellation, no
 *   overflow, and no un-enumerable subdirectory. Any abort path commits zero
 *   index writes — failed/cancelled scans keep the previous complete index.
 * - Folder index (LT-3, REQ-0001 A1): every visited subdirectory is stored as
 *   a path-only row, so a folder with no indexed Markdown/TXT descendant still
 *   appears on its parent browse level. Directory rows obey the same commit
 *   and cleanup rules as documents (one transaction; no cleanup when the walk
 *   cannot prove absence) and never open a stream or copy an original.
 * - Entry tolerance (FR-9): malformed rows (blank id/name) are recorded as
 *   [EntryError] and skipped without aborting the walk; a malformed
 *   *directory* additionally suppresses cleanup (its subtree is unprovable).
 *   A [TreeLister] throw (grant loss, provider failure) aborts the whole run
 *   as FAILED with zero writes (AC-6).
 * - Enumeration bound: [MAX_VISITED_ENTRIES] bounds total visited tree
 *   entries (directories, files, and malformed rows). Exceeding it aborts as
 *   FAILED with an explicit reason — never a silent truncation — keeps the
 *   old index, and offers retry/cancel. There is no separate document quota:
 *   every supported file found inside that bound is indexed. The former
 *   500-document cap was an unverified copy of mdreader `MAX_FOLDER_FILES`
 *   and rejected ordinary libraries before the traversal guard.
 * - Cancellation (§3.3): `ensureActive()` is checked per directory and per
 *   entry; cancel rethrows untouched, commits nothing, reports CANCELLED via
 *   [LibraryRepositoryImpl.noteScanFinished], and releases cursors (the
 *   lister closes its cursor in `use {}` on every path).
 * - Concurrency (FR-6): whole-run serialization on a per-library [Mutex] —
 *   concurrent refreshes of one library queue instead of interleaving writes.
 *   (FR-6 allows merge-or-serialize; this implementation serializes.)
 * - Progress (FR-7): [ScanProgress] snapshots throttled to
 *   [PROGRESS_THROTTLE_MS] for static text display; no animation driver.
 *
 * Entry point [scan] records intent through
 * [LibraryRepositoryImpl.refreshLibrary] first (validates existence with a
 * typed [StorageException.LibraryNotFound], marks SCANNING) so the frozen
 * contract-2 path stays the single state-marking route; the scanner's own
 * mutex then serializes the actual work.
 */
class LibraryScanner(
    private val db: LeafletDatabaseHandle,
    private val lister: TreeLister,
    private val repository: LibraryRepositoryImpl,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val libraries: LibraryDao = db.libraryDao()
    private val documents: DocumentDao = db.documentDao()
    private val directories: DirectoryDao = db.directoryDao()

    private val runMutexes = mutableMapOf<String, Mutex>()
    private val runMutexGuard = Mutex()
    private val progressStates = MutableStateFlow<Map<String, ScanProgress>>(emptyMap())
    private var lastEmitMs = 0L

    /** Latest throttled progress snapshot for one library (IDLE before any scan). */
    fun observeProgress(libraryId: LibraryId): Flow<ScanProgress> =
        progressStates.map { it[libraryId.value] ?: ScanProgress(libraryId.value) }

    /**
     * Runs a full refresh of [libraryId], serialized per library.
     * Throws [StorageException.LibraryNotFound] for an unknown library and
     * [CancellationException] untouched on cancel (terminal state already
     * recorded as CANCELLED). Never touches source files.
     */
    suspend fun scan(libraryId: LibraryId): ScanResult = mutexFor(libraryId.value).withLock { runScan(libraryId.value) }

    /** Room-backed detail for the browse screen (null for an unknown library). */
    suspend fun libraryDetail(libraryId: LibraryId): LibraryDetail? =
        withContext(ioDispatcher) {
            val row = libraries.findById(libraryId.value) ?: return@withContext null
            LibraryDetail(
                libraryId = row.libraryId,
                displayName = row.displayName,
                rootTreeUri = row.rootTreeUri,
                lastScannedAt = row.lastScannedAt,
                documentCount = documents.countByLibrary(row.libraryId),
            )
        }

    private suspend fun runScan(libraryId: String): ScanResult =
        withContext(ioDispatcher) {
            repository.refreshLibrary(LibraryId(libraryId))
            val row =
                libraries.findById(libraryId)
                    ?: throw StorageException.LibraryNotFound(libraryId)
            setProgress(ScanProgress(libraryId, ScanProgressState.SCANNING))
            try {
                val walk = walkTree(libraryId, row.rootTreeUri)
                if (walk.fatal != null) {
                    return@withContext finishFailed(libraryId, walk, walk.fatal)
                }
                commit(libraryId, walk)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    repository.noteScanFinished(libraryId, ScanStatus.CANCELLED)
                }
                setProgress(
                    ScanProgress(libraryId, ScanProgressState.CANCELLED, message = "已取消，显示上次完整结果"),
                )
                throw e
            } catch (e: Exception) {
                if (e is StorageException) throw e
                return@withContext finishFailed(
                    libraryId,
                    WalkOutcome(emptyList(), emptyList(), subtreeSkipped = false),
                    WalkFailure(ScanFailureKind.UNKNOWN, "扫描失败：${e.message ?: e::class.simpleName}"),
                )
            }
        }

    // -- Walk ------------------------------------------------------------------

    private suspend fun walkTree(
        libraryId: String,
        treeUri: String,
    ): WalkOutcome {
        val errors = mutableListOf<EntryError>()
        val files = mutableListOf<ScannedFile>()
        // LT-3 (REQ-0001 A1): every visited subdirectory is recorded as a
        // path-only row so a folder with no indexed document still shows on
        // its parent level. No stream is opened and no name is read beyond
        // the display name already required for the walk.
        val directories = mutableListOf<String>()
        var subtreeSkipped = false
        // O(1) duplicate-path guard (R2-04 scale note): the old
        // `files.any { it.relativePath == ... }` was O(N²) over the walk.
        val seenPaths = mutableSetOf<String>()
        val rootId =
            try {
                lister.treeDocumentId(treeUri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                return WalkOutcome(files, errors, subtreeSkipped = false, permissionFailure(treeUri, e), directories)
            } catch (e: Exception) {
                return WalkOutcome(files, errors, subtreeSkipped = false, providerFailure("无法打开目录树：${e.message}", e), directories)
            }
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootId to "")
        val enqueuedDirs = mutableSetOf(rootId)
        var visited = 0
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (parentId, relDir) = queue.removeFirst()
            val children =
                try {
                    lister.listChildren(treeUri, parentId).sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    return WalkOutcome(files, errors, subtreeSkipped = false, permissionFailure(treeUri, e), directories)
                } catch (e: Exception) {
                    return WalkOutcome(
                        files,
                        errors,
                        subtreeSkipped = false,
                        providerFailure("无法列举目录“$relDir”：${e.message}", e),
                        directories,
                    )
                }
            for (entry in children) {
                currentCoroutineContext().ensureActive()
                visited++
                if (visited > MAX_VISITED_ENTRIES) {
                    return WalkOutcome(
                        files,
                        errors,
                        subtreeSkipped = true,
                        WalkFailure(
                            ScanFailureKind.OVERFLOW,
                            "目录条目超过 $MAX_VISITED_ENTRIES，已保留上次完整结果，未删除任何条目",
                        ),
                        directories,
                    )
                }
                if (entry.documentId.isBlank() || entry.displayName.isBlank()) {
                    errors.add(
                        EntryError(
                            path = if (relDir.isEmpty()) entry.displayName else "$relDir/${entry.displayName}",
                            reason = "缺少元数据列，已跳过该条目",
                            isDirectory = entry.isDirectory,
                        ),
                    )
                    if (entry.isDirectory) subtreeSkipped = true
                    throttledProgress(libraryId, files.size, relDir, errors.size)
                    continue
                }
                if (entry.isDirectory) {
                    if (enqueuedDirs.add(entry.documentId)) {
                        val childDir = if (relDir.isEmpty()) entry.displayName else "$relDir/${entry.displayName}"
                        directories.add(childDir)
                        queue.add(entry.documentId to childDir)
                    } else {
                        errors.add(
                            EntryError(
                                path = relDir,
                                reason = "目录重复出现，已跳过防循环",
                                isDirectory = true,
                            ),
                        )
                    }
                    continue
                }
                val ext = extensionOf(entry.displayName) ?: continue
                if (ext !in SUPPORTED_DOCUMENT_EXTENSIONS) continue
                val relativePath =
                    if (relDir.isEmpty()) entry.displayName else "$relDir/${entry.displayName}"
                if (!seenPaths.add(relativePath)) {
                    // Same path twice in one walk (provider emitted a duplicate
                    // row, or an id was re-added): first wins, record and skip.
                    errors.add(
                        EntryError(
                            path = relativePath,
                            reason = "同一路径在本次扫描中出现多次，已保留首次记录",
                            isDirectory = false,
                        ),
                    )
                    throttledProgress(libraryId, files.size, relDir, errors.size)
                    continue
                }
                files.add(
                    ScannedFile(
                        relativePath = relativePath,
                        displayName = entry.displayName,
                        extension = ext,
                        size = entry.size,
                        lastModified = entry.lastModified,
                        documentUri = entry.documentUri,
                    ),
                )
                throttledProgress(libraryId, files.size, relDir, errors.size)
            }
        }
        return WalkOutcome(files, errors, subtreeSkipped, directories = directories)
    }

    // -- Commit ------------------------------------------------------------------

    // R2-04: the commit is ONE Room transaction (see `commitInTransaction`).
    // The walk phase above performs zero index writes; this branch either
    // commits the complete new index or throws with the old index intact.
    // Cancellation is checked BEFORE entering (a cancel during the walk
    // already returned via the walk's own checks); inside the transaction
    // the code runs to completion so a cancel cannot leave a half index.
    private suspend fun commit(
        libraryId: String,
        walk: WalkOutcome,
    ): ScanResult {
        currentCoroutineContext().ensureActive()
        return commitInTransaction(libraryId, walk)
    }

    private suspend fun commitInTransaction(
        libraryId: String,
        walk: WalkOutcome,
    ): ScanResult {
        val previous = documents.listByLibrary(libraryId).associateBy { it.relativePath }
        val previousDirs = directories.listByLibrary(libraryId).map { it.relativePath }.toSet()
        var inserted = 0
        var updated = 0
        var skipped = 0
        val ordered =
            walk.files.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath },
            )
        // Staged writes: applied inside ONE withTransaction below. UPDATEs
        // use field-level @Update (never REPLACE) so the document row keeps
        // its primary key and the CASCADE position children survive (F05).
        val toUpsert = ArrayList<DocumentEntity>()
        val toDeleteIds = ArrayList<String>()
        for (found in ordered) {
            val old = previous[found.relativePath]
            val size = found.size ?: UNKNOWN_SIZE
            val mtime = found.lastModified ?: UNKNOWN_MTIME
            if (old != null && unchanged(old, found)) {
                skipped++
                continue
            }
            val title = found.displayName.substringBeforeLast('.').ifBlank { found.displayName }
            if (old == null) {
                toUpsert.add(
                    DocumentEntity(
                        documentId = idGenerator(),
                        libraryId = libraryId,
                        relativePath = found.relativePath,
                        uri = found.documentUri,
                        displayName = found.displayName,
                        title = title,
                        extension = found.extension,
                        size = size,
                        lastModified = mtime,
                        contentHash = null,
                    ),
                )
                inserted++
            } else {
                toUpsert.add(
                    old.copy(
                        uri = found.documentUri,
                        displayName = found.displayName,
                        title = title,
                        extension = found.extension,
                        size = size,
                        lastModified = mtime,
                        contentHash = null,
                    ),
                )
                updated++
            }
        }
        var deleted = 0
        val deleteIds: List<String> =
            if (!walk.subtreeSkipped) {
                val seen = walk.files.map { it.relativePath }.toSet()
                previous.values.filter { it.relativePath !in seen }.map { it.documentId }
                    .also { deleted = it.size }
            } else {
                emptyList()
            }
        toDeleteIds.addAll(deleteIds)
        // LT-3: the directory index follows the same all-or-nothing and
        // absence-evidence rules as documents — a skipped subtree means the
        // walk cannot prove a folder vanished, so nothing is deleted.
        val seenDirs = walk.directories.toSet()
        val dirsToInsert = seenDirs - previousDirs
        val dirsToDelete = if (!walk.subtreeSkipped) previousDirs - seenDirs else emptySet()
        val oldIds = previous.values.map { it.documentId }.toSet()
        db.transaction<Unit> {
            for (row in toUpsert) {
                if (row.documentId in oldIds) {
                    documents.update(row)
                } else {
                    documents.upsert(row)
                }
            }
            for (id in toDeleteIds) documents.deleteById(id)
            if (dirsToInsert.isNotEmpty()) {
                directories.insertAll(dirsToInsert.map { DirectoryEntity(libraryId, it) })
            }
            if (dirsToDelete.isNotEmpty()) {
                // One bind variable per path: chunk so removing a large folder
                // tree cannot hit SQLite's variable limit and fail the scan.
                dirsToDelete.chunked(DIRECTORY_DELETE_CHUNK).forEach { chunk ->
                    directories.deleteByPaths(libraryId, chunk)
                }
            }
            val now = clock()
            libraries.findById(libraryId)?.let { libraries.update(it.copy(lastScannedAt = now)) }
        }
        repository.noteScanFinished(libraryId, ScanStatus.SUCCEEDED)
        setProgress(
            ScanProgress(
                libraryId = libraryId,
                state = ScanProgressState.SUCCEEDED,
                scannedFiles = walk.files.size,
                entryErrors = walk.entryErrors.size,
                message = if (walk.subtreeSkipped) "部分目录未能枚举，未删除任何旧条目" else null,
            ),
        )
        return ScanResult(
            libraryId = libraryId,
            status = ScanStatus.SUCCEEDED,
            seen = walk.files.size,
            inserted = inserted,
            updated = updated,
            skipped = skipped,
            deleted = deleted,
            entryErrors = walk.entryErrors.toList(),
        )
    }

    /**
     * Skip only when the fresh pair is fully known and equals a stored
     * reliable pair (REQ-0001 FR-4). `mtime == 0` counts as unknown on either
     * side (provider-missing marker); `size == 0` is legitimate data (empty
     * files exist) and participates normally once mtime is known.
     */
    private fun unchanged(
        old: DocumentEntity,
        found: ScannedFile,
    ): Boolean {
        val freshSize = found.size ?: return false
        val freshMtime = found.lastModified ?: return false
        if (freshMtime == UNKNOWN_MTIME) return false
        if (old.size == UNKNOWN_SIZE || old.lastModified == UNKNOWN_MTIME) return false
        return old.size == freshSize && old.lastModified == freshMtime
    }

    private suspend fun finishFailed(
        libraryId: String,
        walk: WalkOutcome,
        failure: WalkFailure,
    ): ScanResult {
        withContext(NonCancellable) {
            repository.noteScanFinished(libraryId, ScanStatus.FAILED)
        }
        setProgress(
            ScanProgress(
                libraryId = libraryId,
                state = ScanProgressState.FAILED,
                scannedFiles = walk.files.size,
                entryErrors = walk.entryErrors.size,
                message = failure.reason,
            ),
        )
        return ScanResult(
            libraryId = libraryId,
            status = ScanStatus.FAILED,
            seen = walk.files.size,
            entryErrors = walk.entryErrors.toList(),
            failureReason = failure.reason,
            failureKind = failure.kind,
        )
    }

    private fun permissionFailure(
        treeUri: String,
        cause: SecurityException,
    ): WalkFailure =
        WalkFailure(
            ScanFailureKind.PERMISSION,
            "无法访问该目录（授权已失效），原文不受影响 [$treeUri]: ${cause.message}",
        )

    private fun providerFailure(
        reason: String,
        cause: Exception,
    ): WalkFailure {
        val kind = if (cause is IOException) ScanFailureKind.PROVIDER else ScanFailureKind.UNKNOWN
        return WalkFailure(kind, "本次扫描未完成，已保留上次完整结果，未删除任何条目。$reason")
    }

    // -- Progress ------------------------------------------------------------------

    private fun setProgress(snapshot: ScanProgress) {
        progressStates.update { it + (snapshot.libraryId to snapshot) }
        lastEmitMs = clock()
    }

    private fun throttledProgress(
        libraryId: String,
        scanned: Int,
        dir: String,
        errors: Int,
    ) {
        val now = clock()
        if (now - lastEmitMs < PROGRESS_THROTTLE_MS) return
        progressStates.update {
            it + (libraryId to ScanProgress(libraryId, ScanProgressState.SCANNING, scanned, dir, errors))
        }
        lastEmitMs = now
    }

    private suspend fun mutexFor(libraryId: String): Mutex =
        runMutexGuard.withLock {
            runMutexes.getOrPut(libraryId) { Mutex() }
        }

    private data class WalkOutcome(
        val files: List<ScannedFile>,
        val entryErrors: List<EntryError>,
        val subtreeSkipped: Boolean,
        val fatal: WalkFailure? = null,
        /** Path-only folder rows for LT-3 (REQ-0001 A1); never committed on a failed walk. */
        val directories: List<String> = emptyList(),
    )

    private data class WalkFailure(val kind: ScanFailureKind, val reason: String)

    companion object {
        /**
         * Traversal safety bound (total visited entries, dirs + files +
         * malformed rows). mdreader uses the same guard of 5000
         * (`docs/research/eink-readers.md` §2). UNVERIFIED against a Leaflet
         * device baseline.
         *
         * R2-04 (2026-09-18): the walk is O(entries) with O(1) per-entry work
         * (seen-set dedup) and the commit is one transaction. This guard is
         * an explicit overflow stop, not a document quota.
         */
        const val MAX_VISITED_ENTRIES = 5000

        /** Progress emission throttle (REQ-0001 FR-7: ≥ 500 ms, static text). */
        const val PROGRESS_THROTTLE_MS = 500L

        /**
         * Paths per directory-cleanup DELETE (LT-3). SQLite binds one variable
         * per path and older hosts cap variables below 1,000, so a scan that
         * removes a large folder tree deletes in bounded batches instead of
         * failing the whole run.
         */
        const val DIRECTORY_DELETE_CHUNK = 500

        /** Stored size when the provider reported none (never matches a skip). */
        const val UNKNOWN_SIZE = -1L

        /** Stored mtime when the provider reported none/zero (never matches a skip). */
        const val UNKNOWN_MTIME = 0L
    }
}
