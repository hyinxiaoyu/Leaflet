package io.leaflet.storage.saf

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.leaflet.model.DocumentId
import io.leaflet.storage.PermissionDeniedProbe
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.LeafletDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * SAF instrumented tests against [StubProvider] (no system picker, no real
 * user grant): read + caller-close, revoked grant, vanished file, picker
 * cancel/denial probes, unknown-scheme rejection — each with the acidic
 * source-integrity check (source bytes before == after).
 *
 * Process-rebuild persistence of a real persistable grant is UNVERIFIED here
 * (a stub provider cannot issue one); owned by T05's real-provider pass.
 */
@RunWith(AndroidJUnit4::class)
class SafInstrumentedTest {
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
        StubProvider.blobs.clear()
        StubProvider.revoked = false
    }

    private fun sourceFile(
        name: String,
        bytes: ByteArray,
    ): File {
        val dir = File(context.cacheDir, "t04-saf-sources").also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private suspend fun seedLibrary(db: LeafletDatabase) {
        db.libraryDao().insert(io.leaflet.storage.db.LibraryEntity("lib-saf", "Docs", "content://tree/saf", 1L, null))
    }

    private fun docRow(
        id: String,
        uri: String,
    ): DocumentEntity = DocumentEntity(id, "lib-saf", "note.md", uri, "note.md", null, "md", 11L, 5L, null)

    private fun source(db: LeafletDatabase): DocumentSourceImpl =
        DocumentSourceImpl(db.documentDao(), ContentResolverAccess(context.contentResolver))

    @Test
    fun openStreamReadsStubBytesAndCallerCloses() =
        runTest {
            val bytes = "stub provider bytes".toByteArray()
            val probe = sourceFile("saf-a.bin", bytes)
            val checksumBefore = sha256(probe.readBytes())
            StubProvider.blobs["saf-a.bin"] = probe.readBytes()

            val db = newDb()
            seedLibrary(db)
            db.documentDao().upsert(docRow("d1", StubProvider.uri("saf-a.bin")))
            val text =
                source(db).openStream(DocumentId("d1")).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }

            assertEquals("stub provider bytes", text)
            assertEquals(checksumBefore, sha256(probe.readBytes()))
            assertArrayEquals(bytes, probe.readBytes())
        }

    @Test
    fun revokedGrantSurfacesAsPermissionLost() =
        runTest {
            val probe = sourceFile("saf-b.bin", "revocable".toByteArray())
            val checksumBefore = sha256(probe.readBytes())
            StubProvider.blobs["saf-b.bin"] = probe.readBytes()
            StubProvider.revoked = true

            val db = newDb()
            seedLibrary(db)
            db.documentDao().upsert(docRow("d1", StubProvider.uri("saf-b.bin")))

            try {
                source(db).openStream(DocumentId("d1")).use { it.readBytes() }
                fail("expected PermissionLost")
            } catch (e: StorageException.PermissionLost) {
                assertTrue(e.uri.isNotEmpty())
            }
            assertEquals(checksumBefore, sha256(probe.readBytes()))
        }

    @Test
    fun vanishedFileSurfacesAsDocumentGone() =
        runTest {
            val db = newDb()
            seedLibrary(db)
            // No blob registered: the provider throws FileNotFoundException.
            db.documentDao().upsert(docRow("d1", StubProvider.uri("never-there.bin")))

            try {
                source(db).openStream(DocumentId("d1")).use { it.readBytes() }
                fail("expected DocumentGone")
            } catch (e: StorageException.DocumentGone) {
                assertEquals("d1", e.documentId)
            }
        }

    @Test
    fun callerCancelBeforePickLeavesNoRows() =
        runTest {
            // Dismissing the system folder picker (T05) never calls the repo.
            val probe = PermissionDeniedProbe()
            probe.simulateUserCancel()

            assertTrue(probe.librariesCreated().isEmpty())
            assertTrue(probe.grantsTaken().isEmpty())
            assertTrue(probe.grantsReleased().isEmpty())
        }

    @Test
    fun deniedGrantLeavesNoRows() =
        runTest {
            // Denied persistence rolls the add back (unit-tested in
            // LibraryRepositoryTest); the picker-side outcome is zero rows/grants.
            val probe = PermissionDeniedProbe()
            probe.simulateDenial()

            assertTrue(probe.librariesCreated().isEmpty())
            assertTrue(probe.grantsTaken().isEmpty())
        }

    @Test
    fun unknownSchemeResolvesToNullWithoutTouchingSource() =
        runTest {
            val probe = sourceFile("saf-c.bin", "untouched".toByteArray())
            val checksumBefore = sha256(probe.readBytes())

            val db = newDb()
            seedLibrary(db)
            db.documentDao().upsert(docRow("d1", StubProvider.uri("saf-c.bin")))
            val resolved = source(db).resolveRelative(DocumentId("d1"), "foo://bar")

            assertNull(resolved)
            assertEquals(checksumBefore, sha256(probe.readBytes()))
        }
}
