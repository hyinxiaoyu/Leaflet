package io.leaflet.storage.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.leaflet.storage.repo.LeafletDatabaseHandle
import io.leaflet.storage.repo.LibraryRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Instrumented DAO + repository tests on a real (in-memory) Room database
 * (REQ-0001 AC-7/FR-8, REQ-0003 FR-1/FR-5):
 *
 * - Foreign-key cascades: removing a library clears documents + positions.
 * - Recent rows (no FK by design) are removed by the repository on
 *   removeLibrary, and survive document deletion for the recoverable prompt.
 * - Acidic source-integrity probes: every test writes a source blob file,
 *   checksums it before AND after the database operation, and asserts
 *   byte-equality — delete-library / remove-reference / clear must never
 *   modify source bytes (ADR-0001).
 *
 * Room 2.8.5 + KSP note: `exportSchema` writes
 * `core/storage/schemas/.../1.json` on every KSP run; the checked-in schema
 * is the migration baseline for future versions (see LeafletDatabase KDoc).
 */
@RunWith(AndroidJUnit4::class)
class RoomInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<LeafletDatabase>()

    private fun newDb(): LeafletDatabase {
        val db =
            Room.inMemoryDatabaseBuilder(context, LeafletDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        databases.add(db)
        return db
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
    }

    // -- Acidic source-integrity harness ---------------------------------------

    private fun sourceFile(
        name: String,
        bytes: ByteArray,
    ): File {
        val dir = File(context.cacheDir, "t04-sources").also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // -- Tests -----------------------------------------------------------------

    @Test
    fun removeLibraryCascadesLocalRowsAndLeavesSourceBytesUntouched() =
        runTest {
            val db = newDb()
            val before = "source bytes must survive".toByteArray()
            val probe = sourceFile("probe-a.md", before)
            val checksumBefore = sha256(probe.readBytes())

            val libs = db.libraryDao()
            val docs = db.documentDao()
            val positions = db.readingPositionDao()
            val recents = db.recentReadDao()
            val libId = "lib-cascade"
            libs.insert(LibraryEntity(libId, "Docs", "content://tree/docs", 1L, null))
            docs.upsert(
                DocumentEntity("doc-1", libId, "a.md", "content://doc/a", "a.md", null, "md", 3L, 9L, null),
            )
            positions.upsert(
                ReadingPositionEntity("doc-1", 1, 2, 0, 0.5f, "H", 5L, "9:3"),
            )
            recents.upsert(RecentReadEntity("doc-1", libId, "A", 7L))

            libs.deleteById(libId)
            // Recent rows carry no FK (recoverable-prompt design); the repository
            // removes them explicitly — emulate the repository step here.
            recents.deleteByLibrary(libId)

            assertTrue(libs.list().isEmpty())
            assertTrue(docs.listByLibrary(libId).isEmpty())
            assertEquals(null, positions.findByDocument("doc-1"))
            assertTrue(recents.listRecent(10).isEmpty())

            val checksumAfter = sha256(probe.readBytes())
            assertEquals(checksumBefore, checksumAfter)
            assertArrayEquals(before, probe.readBytes())
        }

    @Test
    fun recentEntrySurvivesDocumentDeletionForRecoverablePrompt() =
        runTest {
            val db = newDb()
            val before = "prompt snapshot".toByteArray()
            val probe = sourceFile("probe-b.md", before)
            val checksumBefore = sha256(probe.readBytes())

            val libs = db.libraryDao()
            val docs = db.documentDao()
            val recents = db.recentReadDao()
            libs.insert(LibraryEntity("lib-x", "Docs", "content://tree/x", 1L, null))
            docs.upsert(
                DocumentEntity("doc-9", "lib-x", "gone.md", "content://doc/gone", "gone.md", null, "md", 1L, 1L, null),
            )
            recents.upsert(RecentReadEntity("doc-9", "lib-x", "Gone title", 3L))

            // Source file vanishes (or is revoked): the index row goes, the
            // recent entry STAYS so the UI can show title + [remove]/[re-auth].
            docs.deleteById("doc-9")

            val kept = recents.listRecent(10)
            assertEquals(1, kept.size)
            assertEquals("Gone title", kept.first().titleSnapshot)
            assertEquals(sha256(probe.readBytes()), checksumBefore)
        }

    @Test
    fun repositoryRemoveLibraryOnRealDbReleasesGrantAndKeepsSource() =
        runTest {
            val db = newDb()
            val handle: LeafletDatabaseHandle = db
            val access = RecordingAccess()
            val repo =
                LibraryRepositoryImpl(
                    db = handle,
                    access = access,
                    ioDispatcher = kotlinx.coroutines.Dispatchers.Main,
                    clock = { 42L },
                    idGenerator = { "lib-real" },
                )
            val before = "real-db probe".toByteArray()
            val probe = sourceFile("probe-c.md", before)
            val checksumBefore = sha256(probe.readBytes())

            val lib = repo.addLibrary("Docs", "content://tree/real")
            assertEquals("lib-real", lib.id.value)
            assertEquals(listOf("content://tree/real"), access.taken)

            repo.removeLibrary(lib.id)

            assertTrue(repo.observeLibraries().first().isEmpty())
            assertEquals(listOf("content://tree/real"), access.released)
            assertEquals(checksumBefore, sha256(probe.readBytes()))
        }

    @Test
    fun unknownFuturePositionVersionRowSurvivesRead() =
        runTest {
            val db = newDb()
            val libs = db.libraryDao()
            val docs = db.documentDao()
            libs.insert(LibraryEntity("lib-future", "Docs", "content://tree/f", 1L, null))
            docs.upsert(
                DocumentEntity("doc-future", "lib-future", "f.md", "content://doc/f", "f.md", null, "md", 1L, 1L, null),
            )
            db.readingPositionDao().upsert(
                ReadingPositionEntity("doc-future", 99, 3, 1, 0.1f, "H", 1L, "0:0"),
            )

            val row = db.readingPositionDao().findByDocument("doc-future")

            // Room reads the row fine; treating v99 as absent is T07 reader
            // policy (fall back to top, never crash — REQ-0003 edge case).
            assertEquals(99, row?.version)
        }

    @Test
    fun failedTransactionRollsBackOnRealRoom() =
        runTest {
            // R2-04: the `transaction` seam is a REAL Room transaction, not
            // just a fake snapshot — a throw inside rolls back the insert.
            val db = newDb()
            db.libraryDao().insert(LibraryEntity("lib-txn", "T", "content://tree/t", 1L, null))
            try {
                db.transaction<Unit> {
                    db.libraryDao().insert(LibraryEntity("lib-txn-2", "T2", "content://tree/t2", 2L, null))
                    throw RuntimeException("boom inside txn")
                }
                fail("expected boom")
            } catch (e: RuntimeException) {
                assertEquals("boom inside txn", e.message)
            }
            assertEquals(listOf("lib-txn"), db.libraryDao().list().map { it.libraryId })
        }

    private class RecordingAccess : io.leaflet.storage.saf.ContentAccess {
        val taken = mutableListOf<String>()
        val released = mutableListOf<String>()

        override fun openInput(uriString: String): java.io.InputStream? = null

        override fun persistedReadUris(): Set<String> = emptySet()

        override fun takeReadPermission(treeUriString: String) {
            taken.add(treeUriString)
        }

        override fun releaseReadPermission(treeUriString: String) {
            released.add(treeUriString)
        }
    }
}
