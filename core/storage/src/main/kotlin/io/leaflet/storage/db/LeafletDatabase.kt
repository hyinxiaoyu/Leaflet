package io.leaflet.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Leaflet local database, version 2.
 *
 * Tables: [LibraryEntity] (library references), [DocumentEntity] (metadata
 * index only — never body text), [DirectoryEntity] (path-only folder rows so
 * an empty directory can appear while browsing, REQ-0001 A1),
 * [ReadingPositionEntity] (versioned position
 * + [ReadingPositionEntity.fingerprint] for T07 staleness checks),
 * [RecentReadEntity] (MRU snapshot; deliberately no FK so dead entries stay
 * visible with a recoverable prompt, REQ-0003 FR-6).
 *
 * Source files are never modified or deleted by anything in this module
 * (ADR-0001); cascade rules only clear local rows.
 *
 * ## Migration policy (frozen T04, extended by version 2)
 *
 * Version 1 is the first shipped schema; [MIGRATION_1_2] adds the
 * `directories` table. All future versions MUST follow these rules:
 *
 * 1. Never use `fallbackToDestructiveMigration()`: position/settings loss
 *    must not be hidden behind a silent wipe (ADR-0001 follow-up).
 * 2. Every schema change ships with a tested `Migration` (or
 *    `AutoMigrationSpec`) plus a migration test that opens the previous
 *    exported schema under `schemas/` with representative rows (including an
 *    unknown-future `ReadingPositionEntity.version` row) and asserts the rows
 *    survive.
 * 3. New columns MUST be nullable or declare a SQL default so old rows stay
 *    readable; unreadable old position rows are treated as absent (fall back
 *    to top), never crash (REQ-0003 edge case).
 */
@Database(
    entities = [
        LibraryEntity::class,
        DocumentEntity::class,
        DirectoryEntity::class,
        ReadingPositionEntity::class,
        RecentReadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LeafletDatabase : RoomDatabase(), io.leaflet.storage.repo.LeafletDatabaseHandle {
    abstract override fun libraryDao(): LibraryDao

    abstract override fun documentDao(): DocumentDao

    abstract override fun directoryDao(): DirectoryDao

    abstract override fun readingPositionDao(): ReadingPositionDao

    abstract override fun recentReadDao(): RecentReadDao

    /**
     * Production atomicity (R2-04): Room 2.8 writer transaction around the
     * suspend block. (The legacy `begin/setSuccessful/endTransaction` are
     * deprecated in 2.8.x; the KMP `useWriterConnection` path is the
     * supported one. Callers run on IO + serialize per library.)
     */
    override suspend fun <R> transaction(block: suspend () -> R): R =
        useWriterConnection { transactor ->
            transactor.withTransaction(androidx.room.Transactor.SQLiteTransactionType.IMMEDIATE) {
                block()
            }
        }

    companion object {
        /**
         * Version 1 -> 2 (LT-3): adds the path-only `directories` table.
         *
         * Additive and lossless: existing library/document/position/recent
         * rows are untouched, and old library indexes stay browsable because
         * the browse level still derives parent folders from document paths.
         * Directory rows arrive with the next successful scan. The foreign key
         * cascades local rows when a Library is removed; original folders on
         * disk are never affected (ADR-0001).
         */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `directories` (" +
                            "`libraryId` TEXT NOT NULL, " +
                            "`relativePath` TEXT NOT NULL, " +
                            "PRIMARY KEY(`libraryId`, `relativePath`), " +
                            "FOREIGN KEY(`libraryId`) REFERENCES `libraries`(`libraryId`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_directories_libraryId` " +
                            "ON `directories` (`libraryId`)",
                    )
                }
            }
    }
}
