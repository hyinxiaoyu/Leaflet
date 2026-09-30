package io.leaflet.storage.scan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanStatus
import io.leaflet.storage.db.LeafletDatabase
import io.leaflet.storage.db.LibraryEntity
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.saf.ContentResolverAccess
import io.leaflet.storage.saf.StubTreeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Scanner instrumented tests against [StubTreeProvider] (T05 real-provider
 * pass, REQ-0001 Test Plan): the production [ContentResolverTreeLister] runs
 * over a live `ContentResolver` child-query path — no fake lister.
 *
 * Each test carries the acidic source-integrity probe (provider blob bytes
 * before == after) plus the no-stream proof: scans never call `openInput`,
 * so provider blobs are untouched by construction and checksummed here.
 * Process-rebuild persistence of a real system persistable grant remains
 * UNVERIFIED (a stub provider cannot issue one) — owned by the manual
 * real-provider checklist in `docs/development/eink-test-matrix.md` (T13).
 */
@RunWith(AndroidJUnit4::class)
class ScannerInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<LeafletDatabase>()

    @Before
    fun setUp() {
        StubTreeProvider.reset()
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
        StubTreeProvider.reset()
    }

    private fun newDb(): LeafletDatabase {
        val db =
            Room.inMemoryDatabaseBuilder(context, LeafletDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        databases.add(db)
        return db
    }

    private fun sourceFile(
        name: String,
        bytes: ByteArray,
    ): File {
        val dir = File(context.cacheDir, "t05-scan-sources").also { it.mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun harness(db: LeafletDatabase): LibraryScanner {
        // NOTE: no StubTreeProvider.reset() here — callers populate the tree
        // BEFORE newDb/harness, and setUp() already resets per test.
        val repo =
            LibraryRepositoryImpl(
                db = db,
                access = ContentResolverAccess(context.contentResolver),
            )
        return LibraryScanner(
            db = db,
            lister = ContentResolverTreeLister(context.contentResolver),
            repository = repo,
        )
    }

    private suspend fun seedLibrary(
        db: LeafletDatabase,
        libId: String,
        treeUri: String,
    ) {
        db.libraryDao().insert(LibraryEntity(libId, "Docs", treeUri, 1L, null))
    }

    @Test
    fun realListerDiscoversNestedTreeAndRescanReflectsChanges() =
        runTest {
            val probeA = sourceFile("scan-a.bin", "# alpha".toByteArray())
            val probeB = sourceFile("scan-b.bin", "# beta".toByteArray())
            val beforeA = sha256(probeA.readBytes())
            val beforeB = sha256(probeB.readBytes())
            StubTreeProvider.blobs["a.md"] = probeA.readBytes()
            StubTreeProvider.blobs["b.md"] = probeB.readBytes()
            StubTreeProvider.addFile("root", "a", "a.md", 7, 70)
            StubTreeProvider.addDir("root", "sub", "Notes")
            StubTreeProvider.addFile("sub", "b", "B.MD", 6, 60)
            StubTreeProvider.addFile("root", "img", "pic.png", 4, 40)

            val db = newDb()
            val treeUri = StubTreeProvider.treeUri("root")
            seedLibrary(db, "lib-scan", treeUri)
            val scanner = harness(db)

            val first = scanner.scan(LibraryId("lib-scan"))

            assertEquals(ScanStatus.SUCCEEDED, first.status)
            assertEquals(2, first.seen)
            assertEquals(2, first.inserted)
            val paths = db.documentDao().listByLibrary("lib-scan").map { it.relativePath }.sorted()
            assertEquals(listOf("Notes/B.MD", "a.md"), paths)
            // Metadata-only: title is the filename fallback until T06; no streams read.
            val titles = db.documentDao().listByLibrary("lib-scan").associate { it.relativePath to it.title }
            assertEquals("a", titles["a.md"])
            assertEquals("B", titles["Notes/B.MD"])

            // External change: add (c.txt) + modify (B.MD size) + delete (a.md),
            // then rescan.
            StubTreeProvider.addFile("root", "c", "c.txt", 3, 30)
            StubTreeProvider.removeFile("root", "a")
            StubTreeProvider.addFile("sub", "b", "B.MD", 8, 60) // size changed
            val second = scanner.scan(LibraryId("lib-scan"))

            assertEquals(ScanStatus.SUCCEEDED, second.status)
            assertEquals(1, second.inserted)
            assertEquals(1, second.updated)
            assertEquals(1, second.deleted)
            // Un-touched files with identical pairs skip on the third pass.
            val third = scanner.scan(LibraryId("lib-scan"))
            assertEquals(2, third.skipped)
            assertEquals(0, third.inserted + third.updated + third.deleted)

            assertEquals(beforeA, sha256(probeA.readBytes()))
            assertEquals(beforeB, sha256(probeB.readBytes()))
        }

    @Test
    fun realListerMidWalkGrantLossKeepsOldIndex() =
        runTest {
            val probe = sourceFile("scan-c.bin", "keep me".toByteArray())
            val checksumBefore = sha256(probe.readBytes())
            StubTreeProvider.blobs["keep.md"] = probe.readBytes()
            StubTreeProvider.addFile("root", "k", "keep.md", 7, 70)
            StubTreeProvider.addDir("root", "sub", "Sub")
            StubTreeProvider.addFile("sub", "s", "s.md", 1, 1)

            val db = newDb()
            val treeUri = StubTreeProvider.treeUri("root")
            seedLibrary(db, "lib-fail", treeUri)
            val scanner = harness(db)
            scanner.scan(LibraryId("lib-fail"))
            val rowsBefore = db.documentDao().listByLibrary("lib-fail").map { it.copy() }

            StubTreeProvider.childFailure["sub"] = SecurityException("stub grant revoked")
            val failed = scanner.scan(LibraryId("lib-fail"))

            assertEquals(ScanStatus.FAILED, failed.status)
            assertEquals(ScanFailureKind.PERMISSION, failed.failureKind)
            assertTrue(failed.failureReason!!.contains("授权已失效"))
            val rowsAfter = db.documentDao().listByLibrary("lib-fail").map { it.copy() }
            assertEquals(rowsBefore, rowsAfter)
            assertEquals(checksumBefore, sha256(probe.readBytes()))
        }

    @Test
    fun realListerNullCursorFailsWithoutCleanup() =
        runTest {
            StubTreeProvider.addFile("root", "k", "keep.md", 7, 70)
            StubTreeProvider.addDir("root", "sub", "Sub")

            val db = newDb()
            val treeUri = StubTreeProvider.treeUri("root")
            seedLibrary(db, "lib-null", treeUri)
            val scanner = harness(db)
            scanner.scan(LibraryId("lib-null"))
            val countBefore = db.documentDao().countByLibrary("lib-null")

            StubTreeProvider.nullCursorFor.add("sub")
            val failed = scanner.scan(LibraryId("lib-null"))

            assertEquals(ScanStatus.FAILED, failed.status)
            assertEquals(countBefore, db.documentDao().countByLibrary("lib-null"))
        }

    @Test
    fun realListerMissingColumnsAreEntryErrorsNotAbort() =
        runTest {
            StubTreeProvider.addFile("root", "ok", "ok.md", 2, 20)
            StubTreeProvider.addFile("root", "bad", "bad.md", 2, 20)
            StubTreeProvider.missingColumnsFor.add("bad")

            val db = newDb()
            val treeUri = StubTreeProvider.treeUri("root")
            seedLibrary(db, "lib-entry", treeUri)
            val scanner = harness(db)

            val result = scanner.scan(LibraryId("lib-entry"))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.seen)
            assertEquals(1, result.entryErrors.size)
            assertEquals(listOf("ok.md"), db.documentDao().listByLibrary("lib-entry").map { it.relativePath })
        }

    @Test
    fun scannerProgressReachesSucceededOnDevice() =
        runTest {
            StubTreeProvider.addFile("root", "p", "p.md", 1, 1)
            val db = newDb()
            val treeUri = StubTreeProvider.treeUri("root")
            seedLibrary(db, "lib-prog", treeUri)
            val scanner = harness(db)

            scanner.scan(LibraryId("lib-prog"))
            val progress = scanner.observeProgress(LibraryId("lib-prog")).first()

            assertEquals(ScanProgressState.SUCCEEDED, progress.state)
            assertEquals(1, progress.scannedFiles)
        }
}
