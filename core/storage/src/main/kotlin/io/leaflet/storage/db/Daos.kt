package io.leaflet.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Library rows: observe, insert, dedupe lookup by normalized tree URI, and
 * delete-by-id (which cascades to local index rows via foreign keys).
 *
 * No query here touches source files; this DAO only manages references.
 */
@Dao
interface LibraryDao {
    @Query("SELECT * FROM libraries ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries ORDER BY displayName COLLATE NOCASE")
    suspend fun list(): List<LibraryEntity>

    @Query("SELECT * FROM libraries WHERE libraryId = :libraryId")
    suspend fun findById(libraryId: String): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE rootTreeUri = :normalizedTreeUri LIMIT 1")
    suspend fun findByTreeUri(normalizedTreeUri: String): LibraryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(library: LibraryEntity)

    @Update
    suspend fun update(library: LibraryEntity)

    @Query("DELETE FROM libraries WHERE libraryId = :libraryId")
    suspend fun deleteById(libraryId: String)
}

/**
 * Document index rows for one library.
 *
 * Queries stay metadata-only (path/filename/size/mtime/uri); opening file
 * content goes through [io.leaflet.storage.saf.DocumentSourceImpl] (T05 scan,
 * T06/T07 reader), never through this DAO.
 */
@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE libraryId = :libraryId ORDER BY relativePath COLLATE NOCASE")
    fun observeByLibrary(libraryId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE libraryId = :libraryId ORDER BY relativePath COLLATE NOCASE")
    suspend fun listByLibrary(libraryId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun findById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE libraryId = :libraryId AND relativePath = :relativePath LIMIT 1")
    suspend fun findByPath(
        libraryId: String,
        relativePath: String,
    ): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    /**
     * Field-level update (R2-04): replaces column values WITHOUT deleting
     * the row, so the CASCADE position children survive. Contrast with
     * [upsert] (REPLACE = delete + insert), which drops positions (F05).
     * The scanner routes all re-index writes through here.
     */
    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE documentId = :documentId")
    suspend fun deleteById(documentId: String)

    @Query("DELETE FROM documents WHERE libraryId = :libraryId")
    suspend fun deleteByLibrary(libraryId: String)

    @Query("SELECT COUNT(*) FROM documents WHERE libraryId = :libraryId")
    suspend fun countByLibrary(libraryId: String): Int
}

/**
 * Directory index rows for one library (REQ-0001 A1 / LT-3).
 *
 * Path-only metadata: a row exists so a folder with no indexed document can
 * appear on its parent's browse level. Writes are wholesale per scan inside
 * the scanner's commit transaction; no query here touches a source folder.
 */
@Dao
interface DirectoryDao {
    @Query("SELECT * FROM directories WHERE libraryId = :libraryId ORDER BY relativePath COLLATE NOCASE")
    fun observeByLibrary(libraryId: String): Flow<List<DirectoryEntity>>

    @Query("SELECT * FROM directories WHERE libraryId = :libraryId ORDER BY relativePath COLLATE NOCASE")
    suspend fun listByLibrary(libraryId: String): List<DirectoryEntity>

    /** Paths already indexed are identical rows; IGNORE keeps re-scans idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<DirectoryEntity>)

    @Query("DELETE FROM directories WHERE libraryId = :libraryId AND relativePath IN (:paths)")
    suspend fun deleteByPaths(
        libraryId: String,
        paths: List<String>,
    )

    @Query("DELETE FROM directories WHERE libraryId = :libraryId")
    suspend fun deleteByLibrary(libraryId: String)

    @Query("SELECT COUNT(*) FROM directories WHERE libraryId = :libraryId")
    suspend fun countByLibrary(libraryId: String): Int
}

/** Versioned reading positions, keyed by document (REQ-0003 FR-1/FR-2). */
@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE documentId = :documentId")
    suspend fun findByDocument(documentId: String): ReadingPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ReadingPositionEntity)

    @Query("DELETE FROM reading_positions WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)
}

/**
 * Recent reads in last-opened order (REQ-0003 FR-5).
 *
 * Rows intentionally survive source-file deletion so bad entries can show a
 * recoverable prompt (FR-6); only explicit user removal, clear-all, cap
 * eviction, or library removal deletes them.
 */
@Dao
interface RecentReadDao {
    @Query("SELECT * FROM recent_reads ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentReadEntity>>

    @Query("SELECT * FROM recent_reads ORDER BY lastOpenedAt DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<RecentReadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentReadEntity)

    @Query("DELETE FROM recent_reads WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: String)

    @Query("DELETE FROM recent_reads WHERE libraryId = :libraryId")
    suspend fun deleteByLibrary(libraryId: String)

    @Query("DELETE FROM recent_reads")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM recent_reads")
    suspend fun count(): Int

    /**
     * Keeps only the [keep] newest rows. Called after each upsert by the
     * repository so the table honors the RECENT_CAP without a trigger.
     */
    @Query(
        "DELETE FROM recent_reads WHERE documentId NOT IN " +
            "(SELECT documentId FROM recent_reads ORDER BY lastOpenedAt DESC LIMIT :keep)",
    )
    suspend fun trimToNewest(keep: Int)
}
