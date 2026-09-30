package io.leaflet.storage.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Version 1 -> 2 migration test (LT-3 / REQ-0001 A1, LeafletDatabase policy
 * rule 2): opens the checked-in v1 schema (`schemas/.../1.json`, shipped as an
 * androidTest asset through `sourceSets`), writes representative rows
 * including an unknown-future reading-position version, migrates with the real
 * [LeafletDatabase.MIGRATION_1_2], validates the resulting schema against
 * `2.json`, and asserts every pre-existing row survived byte-for-byte.
 *
 * Only the `directories` table is new; the migration is additive and must
 * never touch source files (nothing here reads one).
 */
@RunWith(AndroidJUnit4::class)
class DirectoryMigrationTest {
    private val dbName = "leaflet-migration-test"

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LeafletDatabase::class.java,
        )

    @Test
    fun migrate1To2KeepsEveryRowAndAddsDirectories() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO libraries (libraryId, displayName, rootTreeUri, createdAt, lastScannedAt) " +
                    "VALUES ('lib-1', 'Docs', 'content://tree/docs', 11, 22)",
            )
            db.execSQL(
                "INSERT INTO documents (documentId, libraryId, relativePath, uri, displayName, title, " +
                    "extension, size, lastModified, contentHash) " +
                    "VALUES ('doc-1', 'lib-1', 'notes/a.md', 'content://doc/a', 'a.md', 'a', 'md', 5, 99, NULL)",
            )
            db.execSQL(
                "INSERT INTO reading_positions (documentId, version, blockIndex, blockOffset, progress, " +
                    "anchor, updatedAt, fingerprint, contentHash) " +
                    "VALUES ('doc-1', 99, 3, 1, 0.25, 'H', 44, '99:5', NULL)",
            )
            db.execSQL(
                "INSERT INTO recent_reads (documentId, libraryId, titleSnapshot, lastOpenedAt) " +
                    "VALUES ('doc-1', 'lib-1', 'Snapshot', 55)",
            )
        }

        helper.runMigrationsAndValidate(dbName, 2, true, LeafletDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT displayName, lastScannedAt FROM libraries WHERE libraryId = 'lib-1'").use { cursor ->
                assertTrue("library row survived", cursor.moveToFirst())
                assertEquals("Docs", cursor.getString(0))
                assertEquals(22L, cursor.getLong(1))
            }
            db.query(
                "SELECT libraryId, relativePath, extension, size, lastModified FROM documents " +
                    "WHERE documentId = 'doc-1'",
            ).use { cursor ->
                assertTrue("document row survived", cursor.moveToFirst())
                assertEquals("lib-1", cursor.getString(0))
                assertEquals("notes/a.md", cursor.getString(1))
                assertEquals("md", cursor.getString(2))
                assertEquals(5L, cursor.getLong(3))
                assertEquals(99L, cursor.getLong(4))
            }
            db.query(
                "SELECT version, blockIndex, blockOffset, progress, anchor, fingerprint " +
                    "FROM reading_positions WHERE documentId = 'doc-1'",
            ).use { cursor ->
                assertTrue("position row survived", cursor.moveToFirst())
                // Unknown future version stays readable and is treated as
                // absent by the reader, never dropped by the migration.
                assertEquals(99, cursor.getInt(0))
                assertEquals(3, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0.25f, cursor.getFloat(3))
                assertEquals("H", cursor.getString(4))
                assertEquals("99:5", cursor.getString(5))
            }
            db.query("SELECT titleSnapshot, lastOpenedAt FROM recent_reads WHERE documentId = 'doc-1'").use { cursor ->
                assertTrue("recent row survived", cursor.moveToFirst())
                assertEquals("Snapshot", cursor.getString(0))
                assertEquals(55L, cursor.getLong(1))
            }

            // The new table starts empty: directory rows arrive with the next
            // successful scan, and old libraries stay browsable meanwhile.
            db.query("SELECT COUNT(*) FROM directories").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            // Path-only rows insert, and the library foreign key cascades.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO directories (libraryId, relativePath) VALUES ('lib-1', 'Empty')")
            db.execSQL("INSERT INTO directories (libraryId, relativePath) VALUES ('lib-1', 'notes/drafts')")
            db.query("SELECT COUNT(*) FROM directories").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            db.execSQL("DELETE FROM libraries WHERE libraryId = 'lib-1'")
            db.query("SELECT COUNT(*) FROM directories").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("removing a library cascades its directory rows", 0, cursor.getInt(0))
            }
        }
    }
}
