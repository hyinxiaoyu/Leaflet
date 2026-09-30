package io.leaflet.storage.repo

import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.storage.FakeContentAccess
import io.leaflet.storage.FakeDatabase
import io.leaflet.storage.FakeDirectoryDao
import io.leaflet.storage.FakeDocumentDao
import io.leaflet.storage.FakeLibraryDao
import io.leaflet.storage.FakeReadingPositionDao
import io.leaflet.storage.FakeRecentReadDao
import io.leaflet.storage.ScanState
import io.leaflet.storage.ScanStatus
import io.leaflet.storage.StorageException
import io.leaflet.storage.db.DirectoryEntity
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.db.fingerprintOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Repository contract tests (REQ-0001 FR-1/FR-8, REQ-0003 FR-1/FR-5):
 * add/dedupe/remove-reference, grant sharing, positions, recent MRU+cap.
 *
 * Each test is also an "acidic" source-integrity probe in miniature: the
 * repository under test never opens a source stream (the fake blobs are only
 * touched by [io.leaflet.storage.saf.DocumentSourceImplTest]), so the only
 * observable source side-effect would be a grant take/release, asserted
 * explicitly below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepositoryTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun setup(
        clock: () -> Long = { 1_000L },
        ids: Iterator<String>? = null,
    ): Fixture {
        Dispatchers.setMain(dispatcher)
        val docs = FakeDocumentDao()
        val positions = FakeReadingPositionDao()
        val libs = FakeLibraryDao(docs, positions)
        val recents = FakeRecentReadDao()
        val dirs = FakeDirectoryDao()
        val access = FakeContentAccess()
        val repo =
            LibraryRepositoryImpl(
                db = FakeDatabase(libs, docs, positions, recents, dirs),
                access = access,
                ioDispatcher = dispatcher,
                clock = clock,
                idGenerator = { ids?.next() ?: "id-${System.nanoTime()}" },
            )
        return Fixture(repo, libs, docs, dirs, positions, recents, access)
    }

    @Test
    fun addPersistsNormalizedUriAndTakesGrant() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1").iterator())

            val lib = f.repo.addLibrary(" AI Docs ", "content://tree/primary%3ADocs//")

            assertEquals("lib-1", lib.id.value)
            assertEquals("content://tree/primary%3ADocs", lib.rootTreeUri)
            assertEquals(listOf("content://tree/primary%3ADocs"), f.access.takenPermissions)
            assertEquals(1, f.libs.rows.size)
        }

    @Test
    fun duplicateAddReturnsExistingWithoutSecondGrant() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1", "lib-2").iterator())

            val first = f.repo.addLibrary("Docs", "content://tree/docs")
            val second = f.repo.addLibrary("Renamed", "content://tree/docs/")

            assertEquals(first.id, second.id)
            assertEquals("Docs", second.displayName)
            assertEquals(1, f.libs.rows.size)
            assertEquals(1, f.access.takenPermissions.size)
        }

    @Test
    fun deniedGrantRollsBackRow() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1").iterator())
            f.access.takePermissionFailure = SecurityException("denied by user")

            try {
                f.repo.addLibrary("Docs", "content://tree/docs")
                fail("expected PermissionDenied")
            } catch (e: StorageException.PermissionDenied) {
                assertEquals("content://tree/docs", e.treeUri)
            }
            assertTrue(f.libs.rows.isEmpty())
            assertTrue(f.repo.observeLibraries().first().isEmpty())
        }

    @Test
    fun refreshUnknownLibraryThrowsTypedError() =
        runTest(dispatcher) {
            val f = setup()

            try {
                f.repo.refreshLibrary(LibraryId("nope"))
                fail("expected LibraryNotFound")
            } catch (e: StorageException.LibraryNotFound) {
                assertEquals("nope", e.libraryId)
            }
        }

    @Test
    fun refreshMarksScanningThenTerminalStates() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1", "job-1").iterator())
            val lib = f.repo.addLibrary("Docs", "content://tree/docs")

            f.repo.refreshLibrary(lib.id)
            assertEquals(ScanState.SCANNING, f.repo.observeScanState(lib.id).first())

            f.repo.noteScanFinished(lib.id.value, ScanStatus.SUCCEEDED)
            assertEquals(ScanState.SUCCEEDED, f.repo.observeScanState(lib.id).first())

            f.repo.noteScanFinished(lib.id.value, ScanStatus.CANCELLED)
            assertEquals(ScanState.CANCELLED, f.repo.observeScanState(lib.id).first())

            f.repo.noteScanFinished(lib.id.value, ScanStatus.FAILED)
            assertEquals(ScanState.FAILED, f.repo.observeScanState(lib.id).first())
        }

    @Test
    fun removeClearsLocalRowsAndReleasesUnsharedGrant() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1").iterator())
            val lib = f.repo.addLibrary("Docs", "content://tree/docs")
            f.docs.upsert(doc(lib.id.value, "a.md", "content://doc/a"))
            f.dirs.insertAll(listOf(DirectoryEntity(lib.id.value, "Empty"), DirectoryEntity(lib.id.value, "notes/drafts")))
            f.repo.savePosition(ReadingPosition(DocumentId("${lib.id.value}/a.md")), 10L, 5L)
            f.repo.recordRecent(RecentRead(DocumentId("${lib.id.value}/a.md"), "A", lib.id, 100L))

            assertEquals(listOf("Empty", "notes/drafts"), f.repo.listDirectories(lib.id).sorted())

            f.repo.removeLibrary(lib.id)

            assertTrue(f.libs.rows.isEmpty())
            assertTrue(f.docs.rows.isEmpty())
            assertTrue(f.dirs.rows.isEmpty())
            assertTrue(f.positions.rows.isEmpty())
            assertTrue(f.recents.rows.isEmpty())
            assertEquals(listOf("content://tree/docs"), f.access.releasedPermissions)
        }

    @Test
    fun removeKeepsGrantWhenSiblingSharesTree() =
        runTest(dispatcher) {
            // Two libraries cannot share one normalized URI via addLibrary (dedupe),
            // so the shared row is inserted directly to simulate a provider that
            // issued two textually different URIs for the same grant (see
            // TreeUris KNOWN LIMIT) which T05 later normalizes to one.
            val f = setup(ids = listOf("lib-1", "lib-2").iterator())
            val first = f.repo.addLibrary("Docs", "content://tree/docs")
            f.libs.rows["lib-2"] =
                f.libs.rows.getValue("lib-1").copy(
                    libraryId = "lib-2",
                    displayName = "Docs alias",
                )

            f.repo.removeLibrary(first.id)

            assertEquals(1, f.libs.rows.size)
            assertTrue(f.access.releasedPermissions.isEmpty())
            assertTrue(f.access.grantedPermissions.contains("content://tree/docs"))
        }

    @Test
    fun removeUnknownLibraryIsNoop() =
        runTest(dispatcher) {
            val f = setup()

            f.repo.removeLibrary(LibraryId("ghost"))

            assertTrue(f.access.releasedPermissions.isEmpty())
        }

    @Test
    fun releaseFailureNeverFailsRemoval() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1").iterator())
            val lib = f.repo.addLibrary("Docs", "content://tree/docs")
            f.access.releasePermissionFailure = SecurityException("provider gone")

            f.repo.removeLibrary(lib.id)

            assertTrue(f.libs.rows.isEmpty())
        }

    @Test
    fun failedDaoLeavesPriorRowsUntreatedAsDeleted() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1", "lib-2").iterator())
            f.repo.addLibrary("Docs", "content://tree/docs")
            f.libs.failAfter = 0

            try {
                f.repo.addLibrary("Other", "content://tree/other")
                fail("expected injected failure")
            } catch (e: RuntimeException) {
                assertEquals("injected DAO failure", e.message)
            }
            // The pre-existing library is untouched — a failed add is not a delete.
            assertEquals(1, f.libs.rows.size)
            assertEquals("Docs", f.libs.rows.getValue("lib-1").displayName)
        }

    @Test
    fun positionRoundTripCarriesFingerprint() =
        runTest(dispatcher) {
            val f = setup()
            val position =
                ReadingPosition(
                    documentId = DocumentId("doc-1"),
                    version = 1,
                    blockIndex = 7,
                    blockOffset = 3,
                    progress = 0.25f,
                    anchor = "Heading",
                )

            f.repo.savePosition(position, lastModified = 10L, size = 5L)

            assertEquals(position, f.repo.loadPosition(DocumentId("doc-1")))
            assertEquals(fingerprintOf(10L, 5L), f.positions.rows.getValue("doc-1").fingerprint)
            assertNull(f.repo.loadPosition(DocumentId("missing")))
        }

    @Test
    fun positionSnapshotCarriesSaveTimeFingerprint() =
        runTest(dispatcher) {
            // R2-05 F06: the restore planner needs the HISTORICAL fingerprint.
            val f = setup()
            val position =
                ReadingPosition(
                    documentId = DocumentId("doc-1"),
                    version = 1,
                    blockIndex = 2,
                    blockOffset = 0,
                    progress = 0.1f,
                    anchor = null,
                )
            f.repo.savePosition(position, lastModified = 10L, size = 5L)
            val snap = f.repo.loadPositionWithFingerprint(DocumentId("doc-1"))
            assertEquals(position, snap?.position)
            assertEquals(fingerprintOf(10L, 5L), snap?.fingerprint)
            assertNull(f.repo.loadPositionWithFingerprint(DocumentId("missing")))
        }

    @Test
    fun recentHonorsMruDedupeCapAndRemoval() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1").iterator())
            val lib = f.repo.addLibrary("Docs", "content://tree/docs")
            for (i in 0 until LibraryRepositoryImpl.RECENT_CAP + 5) {
                f.repo.recordRecent(RecentRead(DocumentId("doc-$i"), "T$i", lib.id, i.toLong()))
            }

            val recent = f.repo.listRecent()
            assertEquals(LibraryRepositoryImpl.RECENT_CAP, recent.size)
            assertEquals(DocumentId("doc-${LibraryRepositoryImpl.RECENT_CAP + 4}"), recent.first().documentId)
            assertTrue(recent.none { it.documentId == DocumentId("doc-0") })

            // Re-open moves the entry to the front (REPLACE + newest-first order).
            f.repo.recordRecent(RecentRead(DocumentId("doc-10"), "T10", lib.id, 10_000L))
            assertEquals(DocumentId("doc-10"), f.repo.listRecent().first().documentId)
            assertEquals(LibraryRepositoryImpl.RECENT_CAP, f.repo.listRecent().size)

            f.repo.removeRecent(DocumentId("doc-10"))
            assertTrue(f.repo.listRecent().none { it.documentId == DocumentId("doc-10") })

            f.repo.clearRecent()
            assertTrue(f.repo.listRecent().isEmpty())
        }

    @Test
    fun concurrentRefreshSerializesState() =
        runTest(dispatcher) {
            val f = setup(ids = listOf("lib-1", "j1", "j2").iterator())
            val lib = f.repo.addLibrary("Docs", "content://tree/docs")

            launch { f.repo.refreshLibrary(lib.id) }.join()
            launch { f.repo.refreshLibrary(lib.id) }.join()
            f.repo.noteScanFinished(lib.id.value, ScanStatus.SUCCEEDED)

            assertEquals(ScanState.SUCCEEDED, f.repo.observeScanState(lib.id).first())
        }

    private fun doc(
        libraryId: String,
        path: String,
        uri: String,
    ): DocumentEntity =
        DocumentEntity(
            documentId = "$libraryId/$path",
            libraryId = libraryId,
            relativePath = path,
            uri = uri,
            displayName = path.substringAfterLast('/'),
            title = null,
            extension = "md",
            size = 5L,
            lastModified = 10L,
            contentHash = null,
        )

    private class Fixture(
        val repo: LibraryRepositoryImpl,
        val libs: FakeLibraryDao,
        val docs: FakeDocumentDao,
        val dirs: FakeDirectoryDao,
        val positions: FakeReadingPositionDao,
        val recents: FakeRecentReadDao,
        val access: FakeContentAccess,
    )
}
