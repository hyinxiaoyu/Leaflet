package io.leaflet.storage

import io.leaflet.storage.db.DirectoryDao
import io.leaflet.storage.db.DirectoryEntity
import io.leaflet.storage.db.DocumentDao
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LibraryDao
import io.leaflet.storage.db.LibraryEntity
import io.leaflet.storage.db.ReadingPositionDao
import io.leaflet.storage.db.ReadingPositionEntity
import io.leaflet.storage.db.RecentReadDao
import io.leaflet.storage.db.RecentReadEntity
import io.leaflet.storage.repo.LeafletDatabaseHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * JVM-testable fakes for the Room DAOs: in-memory maps honoring the same
 * conflict semantics the annotations declare (ABORT on library insert,
 * REPLACE on document/position/recent upsert, cascades on library delete).
 *
 * [failAfter] counts down one per DAO call and then throws [failWith] — used
 * to prove partial-failure handling: prior rows survive, nothing is treated
 * as deleted.
 */
class FakeLibraryDao(
    private val documents: FakeDocumentDao? = null,
    private val positions: FakeReadingPositionDao? = null,
) : LibraryDao {
    /**
     * Directory rows to cascade on delete. Assigned by [FakeDatabase] so the
     * existing two-argument construction sites keep working while the fake
     * mirrors Room's `ON DELETE CASCADE` for version 2.
     */
    var cascadeDirectories: FakeDirectoryDao? = null

    val rows = mutableMapOf<String, LibraryEntity>()
    var failAfter: Int? = null
    var failWith: RuntimeException = RuntimeException("injected DAO failure")

    private fun maybeFail() {
        val remaining = failAfter ?: return
        if (remaining <= 0) throw failWith
        failAfter = remaining - 1
    }

    override fun observeAll(): Flow<List<LibraryEntity>> = flowOf(allSorted())

    override suspend fun list(): List<LibraryEntity> {
        maybeFail()
        return allSorted()
    }

    override suspend fun findById(libraryId: String): LibraryEntity? {
        maybeFail()
        return rows[libraryId]
    }

    override suspend fun findByTreeUri(normalizedTreeUri: String): LibraryEntity? {
        maybeFail()
        return rows.values.firstOrNull { it.rootTreeUri == normalizedTreeUri }
    }

    override suspend fun insert(library: LibraryEntity) {
        maybeFail()
        check(!rows.containsKey(library.libraryId)) { "duplicate libraryId ${library.libraryId}" }
        check(rows.values.none { it.rootTreeUri == library.rootTreeUri }) {
            "duplicate rootTreeUri ${library.rootTreeUri}"
        }
        rows[library.libraryId] = library
    }

    override suspend fun update(library: LibraryEntity) {
        maybeFail()
        rows[library.libraryId] = library
    }

    override suspend fun deleteById(libraryId: String) {
        maybeFail()
        rows.remove(libraryId)
        // Mirror the Room CASCADE: documents + positions + directories of this
        // library go too.
        documents?.deleteByLibraryForTest(libraryId)
        positions?.deleteByLibraryForTest(libraryId, documents)
        cascadeDirectories?.deleteByLibraryForTest(libraryId)
    }

    private fun allSorted(): List<LibraryEntity> = rows.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
}

class FakeDocumentDao : DocumentDao {
    val rows = mutableMapOf<String, DocumentEntity>()
    var failAfter: Int? = null
    var failWith: RuntimeException = RuntimeException("injected DAO failure")
    var failDeletes = false

    private fun maybeFail() {
        val remaining = failAfter ?: return
        if (remaining <= 0) throw failWith
        failAfter = remaining - 1
    }

    override fun observeByLibrary(libraryId: String): Flow<List<DocumentEntity>> = flowOf(byLibrary(libraryId))

    override suspend fun listByLibrary(libraryId: String): List<DocumentEntity> {
        maybeFail()
        return byLibrary(libraryId)
    }

    override suspend fun findById(documentId: String): DocumentEntity? {
        maybeFail()
        return rows[documentId]
    }

    override suspend fun findByPath(
        libraryId: String,
        relativePath: String,
    ): DocumentEntity? {
        maybeFail()
        return rows.values.firstOrNull { it.libraryId == libraryId && it.relativePath == relativePath }
    }

    override suspend fun upsert(document: DocumentEntity) {
        maybeFail()
        rows[document.documentId] = document
    }

    // R2-04: field-level update keeps the row identity (positions survive).
    override suspend fun update(document: DocumentEntity) {
        maybeFail()
        rows[document.documentId] = document
    }

    override suspend fun deleteById(documentId: String) {
        if (failDeletes) throw failWith
        maybeFail()
        rows.remove(documentId)
    }

    override suspend fun deleteByLibrary(libraryId: String) {
        if (failDeletes) throw failWith
        maybeFail()
        rows.entries.removeIf { it.value.libraryId == libraryId }
    }

    override suspend fun countByLibrary(libraryId: String): Int {
        maybeFail()
        return rows.values.count { it.libraryId == libraryId }
    }

    /** Non-failing cascade helper used by [FakeLibraryDao.deleteById]. */
    fun deleteByLibraryForTest(libraryId: String) {
        rows.entries.removeIf { it.value.libraryId == libraryId }
    }

    private fun byLibrary(libraryId: String): List<DocumentEntity> =
        rows.values.filter { it.libraryId == libraryId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
}

class FakeDirectoryDao : DirectoryDao {
    val rows = mutableMapOf<String, DirectoryEntity>()
    var failAfter: Int? = null
    var failWith: RuntimeException = RuntimeException("injected DAO failure")

    private fun maybeFail() {
        val remaining = failAfter ?: return
        if (remaining <= 0) throw failWith
        failAfter = remaining - 1
    }

    private fun key(
        libraryId: String,
        relativePath: String,
    ): String = "$libraryId\u0000$relativePath"

    override fun observeByLibrary(libraryId: String): Flow<List<DirectoryEntity>> = flowOf(byLibrary(libraryId))

    override suspend fun listByLibrary(libraryId: String): List<DirectoryEntity> {
        maybeFail()
        return byLibrary(libraryId)
    }

    override suspend fun insertAll(rows: List<DirectoryEntity>) {
        maybeFail()
        for (row in rows) this.rows.putIfAbsent(key(row.libraryId, row.relativePath), row)
    }

    override suspend fun deleteByPaths(
        libraryId: String,
        paths: List<String>,
    ) {
        maybeFail()
        paths.forEach { rows.remove(key(libraryId, it)) }
    }

    override suspend fun deleteByLibrary(libraryId: String) {
        maybeFail()
        rows.entries.removeIf { it.value.libraryId == libraryId }
    }

    override suspend fun countByLibrary(libraryId: String): Int {
        maybeFail()
        return rows.values.count { it.libraryId == libraryId }
    }

    /** Non-failing cascade helper used by [FakeLibraryDao.deleteById]. */
    fun deleteByLibraryForTest(libraryId: String) {
        rows.entries.removeIf { it.value.libraryId == libraryId }
    }

    private fun byLibrary(libraryId: String): List<DirectoryEntity> =
        rows.values.filter { it.libraryId == libraryId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.relativePath })
}

/** Versioned reading positions, keyed by document (REQ-0003 FR-1/FR-2). */
class FakeReadingPositionDao : ReadingPositionDao {
    val rows = mutableMapOf<String, ReadingPositionEntity>()

    override suspend fun findByDocument(documentId: String): ReadingPositionEntity? = rows[documentId]

    override suspend fun upsert(position: ReadingPositionEntity) {
        rows[position.documentId] = position
    }

    override suspend fun deleteByDocument(documentId: String) {
        rows.remove(documentId)
    }

    /** Test-only cascade helper used by [FakeLibraryDao.deleteById]. */
    fun deleteByLibraryForTest(
        libraryId: String,
        documents: FakeDocumentDao?,
    ) {
        // NOTE: FakeLibraryDao.deleteById cascades documents FIRST, so by the
        // time this runs the document rows are already gone and `ids` would be
        // empty. Positions therefore cascade by document id AND by the test id
        // scheme ("<libraryId>/..." prefix), covering both orderings.
        val ids =
            documents?.rows?.values
                ?.filter { it.libraryId == libraryId }
                ?.map { it.documentId }
                .orEmpty()
                .toSet()
        rows.entries.removeIf { it.key in ids || it.key.startsWith("$libraryId/") }
    }
}

class FakeRecentReadDao : RecentReadDao {
    val rows = mutableMapOf<String, RecentReadEntity>()

    override fun observeRecent(limit: Int): Flow<List<RecentReadEntity>> = flowOf(newest(limit))

    override suspend fun listRecent(limit: Int): List<RecentReadEntity> = newest(limit)

    override suspend fun upsert(entry: RecentReadEntity) {
        rows[entry.documentId] = entry
    }

    override suspend fun deleteByDocument(documentId: String) {
        rows.remove(documentId)
    }

    override suspend fun deleteByLibrary(libraryId: String) {
        rows.entries.removeIf { it.value.libraryId == libraryId }
    }

    override suspend fun clearAll() {
        rows.clear()
    }

    override suspend fun count(): Int = rows.size

    override suspend fun trimToNewest(keep: Int) {
        val keepIds =
            rows.values.sortedByDescending { it.lastOpenedAt }
                .take(keep).map { it.documentId }.toSet()
        rows.entries.removeIf { it.key !in keepIds }
    }

    private fun newest(limit: Int): List<RecentReadEntity> = rows.values.sortedByDescending { it.lastOpenedAt }.take(limit.coerceAtLeast(1))
}

/** Wires the fakes into a [LeafletDatabaseHandle] without Robolectric or a device. */
class FakeDatabase(
    libraryDao: FakeLibraryDao,
    documentDao: FakeDocumentDao,
    positionDao: FakeReadingPositionDao,
    recentDao: FakeRecentReadDao,
    directoryDao: FakeDirectoryDao = FakeDirectoryDao(),
) : LeafletDatabaseHandle {
    private val lib = libraryDao
    private val doc = documentDao
    private val pos = positionDao
    private val rec = recentDao
    private val dir = directoryDao

    init {
        // Room cascades directories with the library row; keep the fake equal.
        lib.cascadeDirectories = dir
    }

    override fun libraryDao(): LibraryDao = lib

    override fun documentDao(): DocumentDao = doc

    override fun directoryDao(): DirectoryDao = dir

    override fun readingPositionDao(): ReadingPositionDao = pos

    override fun recentReadDao(): RecentReadDao = rec

    /**
     * Snapshot transaction (R2-04): deep-copies library + document + directory
     * + position rows before [block] and restores them if it throws — the
     * same all-or-nothing guarantee production gets from Room (which rolls
     * back the whole txn, including `lastScannedAt`). Recents are outside
     * the scanner's write set and intentionally not snapshotted.
     */
    override suspend fun <R> transaction(block: suspend () -> R): R {
        val libSnap = lib.rows.mapValues { it.value.copy() }
        val docSnap = doc.rows.mapValues { it.value.copy() }
        val dirSnap = dir.rows.mapValues { it.value.copy() }
        val posSnap = pos.rows.mapValues { it.value.copy() }
        try {
            return block()
        } catch (e: Exception) {
            lib.rows.clear()
            lib.rows.putAll(libSnap)
            doc.rows.clear()
            doc.rows.putAll(docSnap)
            dir.rows.clear()
            dir.rows.putAll(dirSnap)
            pos.rows.clear()
            pos.rows.putAll(posSnap)
            throw e
        }
    }
}
