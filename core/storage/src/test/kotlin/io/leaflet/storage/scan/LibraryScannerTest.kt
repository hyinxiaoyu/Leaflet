package io.leaflet.storage.scan

import io.leaflet.model.LibraryId
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
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.repo.LibraryRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Scanner rule tests (REQ-0001 FR-2–FR-7, AC-1–AC-6) against an in-memory
 * [TreeLister] fake: discovery/sort/hierarchy, incremental skip, cleanup
 * gating, unknown-library, entry tolerance, deep trees, slow queries,
 * mid-walk failure, overflow caps, cancellation, concurrency.
 *
 * No test opens a source stream — the scanner never calls `openInput` — so
 * the fake file table stays untouched by construction; the mid-walk failure
 * and cancel tests additionally assert the pre-existing index is byte-equal
 * before/after (rows + access side-effect lists).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScannerTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Fixture -------------------------------------------------------------

    private class FakeLister(
        val roots: MutableMap<String, String> = mutableMapOf(),
        val children: MutableMap<String, List<TreeEntry>> = mutableMapOf(),
        var rootFailure: Exception? = null,
        val childFailures: MutableMap<String, Exception> = mutableMapOf(),
        var delayMs: Long = 0,
        var listCalls: Int = 0,
    ) : TreeLister {
        override suspend fun treeDocumentId(treeUri: String): String {
            rootFailure?.let { throw it }
            return roots[treeUri] ?: throw FileNotFoundException("no root: $treeUri")
        }

        override suspend fun listChildren(
            treeUri: String,
            parentDocumentId: String,
        ): List<TreeEntry> {
            listCalls++
            if (delayMs > 0) delay(delayMs)
            childFailures[parentDocumentId]?.let { throw it }
            return children[parentDocumentId] ?: emptyList()
        }
    }

    private class Fixture(
        val scanner: LibraryScanner,
        val repo: LibraryRepositoryImpl,
        val lister: FakeLister,
        val libs: FakeLibraryDao,
        val docs: FakeDocumentDao,
        val dirs: FakeDirectoryDao,
        val access: FakeContentAccess,
        val libId: String,
        val treeUri: String,
    )

    private fun setup(
        treeEntries: Map<String, List<TreeEntry>> = emptyMap(),
        clock: () -> Long = { 9_000L },
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
        val lister = FakeLister()
        val scanner =
            LibraryScanner(
                db = FakeDatabase(libs, docs, positions, recents, dirs),
                lister = lister,
                repository = repo,
                ioDispatcher = dispatcher,
                clock = clock,
                idGenerator = { ids?.next() ?: "doc-${System.nanoTime()}" },
            )
        return Fixture(scanner, repo, lister, libs, docs, dirs, access, "", "")
    }

    private suspend fun Fixture.addLibrary(treeUri: String = "content://tree/docs"): String {
        lister.roots[treeUri] = "root"
        val lib = repo.addLibrary("Docs", treeUri)
        return lib.id.value
    }

    private fun dir(
        id: String,
        name: String,
    ): TreeEntry = TreeEntry(id, name, true, null, null, "content://doc/$id")

    private fun file(
        id: String,
        name: String,
        size: Long? = 10,
        mtime: Long? = 100,
    ): TreeEntry = TreeEntry(id, name, false, size, mtime, "content://doc/$id")

    // -- AC-1: discovery / sort / hierarchy / filtering -----------------------

    @Test
    fun discoveryKeepsHierarchySortsCaseInsensitivelyAndFilters() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    file("d", "delta.MD", 1, 1),
                    dir("sub", "Notes"),
                    file("p", "photo.png", 1, 1),
                    file("e", "empty"),
                    file("b", "Beta.txt", 1, 1),
                    file("a", "alpha.markdown", 1, 1),
                )
            f.lister.children["sub"] =
                listOf(
                    file("c", "Gamma.Md", 1, 1),
                    file("z", "zeta.MARKDOWN", 1, 1),
                )

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(5, result.seen)
            assertEquals(5, result.inserted)
            val paths =
                f.docs.rows.values.map { it.relativePath }.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { it },
                )
            assertEquals(
                listOf("alpha.markdown", "Beta.txt", "delta.MD", "Notes/Gamma.Md", "Notes/zeta.MARKDOWN"),
                paths,
            )
            val exts = f.docs.rows.values.associate { it.relativePath to it.extension }
            assertEquals("md", exts["delta.MD"])
            assertEquals("markdown", exts["Notes/zeta.MARKDOWN"])
            assertEquals("txt", exts["Beta.txt"])
            // First scan is metadata-only: filename fallback titles, no stream reads.
            assertEquals("delta", f.docs.rows.values.first { it.relativePath == "delta.MD" }.title)
            assertTrue(f.access.blobs.isEmpty())
            assertEquals(ScanState.SUCCEEDED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    @Test
    fun duplicatePathInOneWalkKeepsFirstAndRecordsError() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    file("a", "a.md", 1, 1),
                    file("b", "a.md", 2, 2),
                )

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.seen)
            assertEquals(1, result.inserted)
            assertEquals(1, result.entryErrors.size)
            assertEquals(1, f.docs.rows.size)
        }

    @Test
    fun emptyAndUnsupportedOnlyTreesSucceedWithZeroDocs() =
        runTest(dispatcher) {
            val f = setup()
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    dir("empty", "EmptyDir"),
                    file("p", "pic.png", 5, 5),
                    file("b", "archive.zip", 5, 5),
                )
            f.lister.children["empty"] = emptyList()

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(0, result.seen)
            assertTrue(f.docs.rows.isEmpty())
            // LT-3: the empty folder is still indexed as a path-only row so it
            // can appear while browsing, even though it holds no document.
            assertEquals(listOf("EmptyDir"), f.dirs.rows.values.map { it.relativePath })
            assertEquals(ScanState.SUCCEEDED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    // -- LT-3 (REQ-0001 A1): indexed folder rows ------------------------------

    @Test
    fun directoryRowsIncludeEmptyAndUnsupportedOnlyFolders() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(dir("empty", "Empty"), dir("photos", "Photos"), dir("docs", "Docs"))
            f.lister.children["empty"] = emptyList()
            f.lister.children["photos"] = listOf(file("p", "pic.png", 1, 1))
            f.lister.children["docs"] = listOf(dir("deep", "Deep"), file("a", "a.md", 1, 1))
            f.lister.children["deep"] = emptyList()

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(listOf("Docs/a.md"), f.docs.rows.values.map { it.relativePath })
            assertEquals(
                listOf("Docs", "Docs/Deep", "Empty", "Photos"),
                f.dirs.rows.values.map { it.relativePath }.sorted(),
            )
        }

    @Test
    fun rescanKeepsDirectoryRowsAndCleansUpVanishedOnes() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("keep", "Keep"), dir("doomed", "Doomed"))
            f.lister.children["keep"] = listOf(file("k", "k.md", 1, 1))
            f.lister.children["doomed"] = emptyList()
            f.scanner.scan(LibraryId(lib))
            assertEquals(listOf("Doomed", "Keep"), f.dirs.rows.values.map { it.relativePath }.sorted())

            // Identical tree: the directory index is idempotent, not duplicated.
            f.scanner.scan(LibraryId(lib))
            assertEquals(listOf("Doomed", "Keep"), f.dirs.rows.values.map { it.relativePath }.sorted())

            // The folder is gone from the tree: a complete successful walk
            // deletes its row like it deletes vanished documents.
            f.lister.children["root"] = listOf(dir("keep", "Keep"))
            val third = f.scanner.scan(LibraryId(lib))
            assertEquals(ScanStatus.SUCCEEDED, third.status)
            assertEquals(listOf("Keep"), f.dirs.rows.values.map { it.relativePath })
        }

    @Test
    fun failedScanKeepsPreviousDirectoryRows() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("sub", "Sub"), dir("empty", "Empty"))
            f.lister.children["sub"] = listOf(file("a", "a.md", 1, 1))
            f.lister.children["empty"] = emptyList()
            f.scanner.scan(LibraryId(lib))
            val before = f.dirs.rows.values.map { it.copy() }.sortedBy { it.relativePath }

            f.lister.childFailures["sub"] = SecurityException("grant revoked mid-walk")
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.FAILED, result.status)
            assertEquals(before, f.dirs.rows.values.map { it.copy() }.sortedBy { it.relativePath })
        }

    @Test
    fun malformedDirectorySuppressesDirectoryCleanup() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("gone", "Gone"))
            f.lister.children["gone"] = emptyList()
            f.scanner.scan(LibraryId(lib))
            assertEquals(listOf("Gone"), f.dirs.rows.values.map { it.relativePath })

            // A malformed directory row means absence is unprovable: the
            // vanished folder row must survive this run.
            f.lister.children["root"] = listOf(TreeEntry("", "", true, null, null, ""))
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(listOf("Gone"), f.dirs.rows.values.map { it.relativePath })
        }

    @Test
    fun removingOneLibraryCascadesOnlyItsDirectoryRows() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val a = f.addLibrary("content://tree/a")
            val b = f.addLibrary("content://tree/b")
            f.lister.roots["content://tree/a"] = "rootA"
            f.lister.roots["content://tree/b"] = "rootB"
            f.lister.children["rootA"] = listOf(dir("a-empty", "AEmpty"))
            f.lister.children["a-empty"] = emptyList()
            f.lister.children["rootB"] = listOf(dir("b-empty", "BEmpty"))
            f.lister.children["b-empty"] = emptyList()
            f.scanner.scan(LibraryId(a))
            f.scanner.scan(LibraryId(b))
            assertEquals(2, f.dirs.rows.size)

            f.repo.removeLibrary(LibraryId(a))

            assertEquals(listOf(b), f.dirs.rows.values.map { it.libraryId })
            assertEquals(listOf("BEmpty"), f.dirs.rows.values.map { it.relativePath })
        }

    @Test
    fun largeDirectoryCleanupDeletesInBoundedBatches() =
        runTest(dispatcher) {
            // 1,200 folders vanish in one run. The fake DAO cannot reproduce
            // SQLite's bind-variable limit, so this pins the chunk constant and
            // proves a large cleanup completes as one SUCCEEDED scan.
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = (1..1200).map { dir("d$it", "folder$it") }
            for (i in 1..1200) f.lister.children["d$i"] = emptyList()
            assertEquals(ScanStatus.SUCCEEDED, f.scanner.scan(LibraryId(lib)).status)
            assertEquals(1200, f.dirs.rows.size)

            f.lister.children["root"] = emptyList()
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertTrue(f.dirs.rows.isEmpty())
            assertTrue(LibraryScanner.DIRECTORY_DELETE_CHUNK < 1000)
        }

    @Test
    fun scanUnknownLibraryThrowsTypedError() =
        runTest(dispatcher) {
            val f = setup()

            try {
                f.scanner.scan(LibraryId("ghost"))
                fail("expected LibraryNotFound")
            } catch (e: StorageException.LibraryNotFound) {
                assertEquals("ghost", e.libraryId)
            }
        }

    // -- AC-3/FR-4: incremental skip + rescan add/update/delete --------------

    @Test
    fun rescanAddsUpdatesDeletesWithMtimeSizeRule() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    file("a", "a.md", 10, 100),
                    file("b", "b.md", 10, 100),
                    file("d", "doomed.md", 10, 100),
                )
            f.scanner.scan(LibraryId(lib))
            val bIdBefore = f.docs.rows.values.first { it.relativePath == "b.md" }.documentId

            f.lister.children["root"] =
                listOf(
                    // identical pair -> skipped
                    file("a", "a.md", 10, 100),
                    // size changed -> re-indexed, same id
                    file("b", "b.md", 11, 100),
                    // added
                    file("n", "new.md", 10, 100),
                )
            val second = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, second.status)
            assertEquals(3, second.seen)
            assertEquals(1, second.inserted)
            assertEquals(1, second.updated)
            assertEquals(1, second.skipped)
            assertEquals(1, second.deleted)
            assertEquals(bIdBefore, f.docs.rows.values.first { it.relativePath == "b.md" }.documentId)
            assertTrue(f.docs.rows.values.none { it.relativePath == "doomed.md" })
        }

    @Test
    fun unreliableMetadataIsNeverDeclaredUnchanged() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            // AC-4 first half: identical reliable pair may skip.
            f.lister.children["root"] =
                listOf(
                    file("k", "kept.md", 7, 70),
                    file("m", "missing.md", null, null),
                    file("z", "zero.md", 0, null),
                )
            f.scanner.scan(LibraryId(lib))
            // AC-4 second half: missing mtime forces re-read, never "unchanged".
            val second = f.scanner.scan(LibraryId(lib))

            assertEquals(1, second.skipped) // only kept.md
            assertEquals(2, second.updated) // missing.md + zero.md re-indexed
            // size==0 is real data (empty files exist), not an unknown marker:
            // a fresh (0, nonzero-mtime) pair over a stored reliable pair may skip.
            f.lister.children["root"] =
                listOf(
                    file("k", "kept.md", 7, 70),
                    file("m", "missing.md", null, null),
                    file("z", "zero.md", 0, 5),
                )
            val third = f.scanner.scan(LibraryId(lib))
            assertEquals(1, third.skipped) // kept.md
            assertEquals(2, third.updated) // missing.md still unknown; zero.md now known
            f.lister.children["root"] =
                listOf(
                    file("k", "kept.md", 7, 70),
                    file("m", "missing.md", null, null),
                    file("z", "zero.md", 0, 5),
                )
            val fourth = f.scanner.scan(LibraryId(lib))
            assertEquals(2, fourth.skipped)
            assertEquals(1, fourth.updated)
        }

    // -- FR-5/AC-6: failed scans commit zero writes ------------------------------

    @Test
    fun midWalkFailureKeepsOldIndexWithZeroWrites() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("sub", "Sub"), file("a", "a.md", 1, 1))
            f.lister.children["sub"] = listOf(file("b", "b.md", 1, 1))
            f.scanner.scan(LibraryId(lib))
            val before = f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            val takenBefore = f.access.takenPermissions.toList()

            f.lister.childFailures["sub"] = SecurityException("grant revoked mid-walk")
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.FAILED, result.status)
            assertEquals(ScanFailureKind.PERMISSION, result.failureKind)
            assertTrue(result.failureReason!!.contains("授权已失效"))
            val after = f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            assertEquals(before, after) // AC-6: zero deletions, zero upserts
            assertEquals(takenBefore, f.access.takenPermissions)
            assertTrue(f.access.releasedPermissions.isEmpty())
            assertEquals(ScanState.FAILED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    @Test
    fun providerFailureMidWalkFailsWithoutCleanup() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("sub", "Sub"), file("keep.md", "keep.md", 1, 1))
            f.scanner.scan(LibraryId(lib))
            val countBefore = f.docs.rows.size

            f.lister.childFailures["sub"] = IOException("provider exploded")
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.FAILED, result.status)
            assertEquals(ScanFailureKind.PROVIDER, result.failureKind)
            assertEquals(countBefore, f.docs.rows.size)
        }

    @Test
    fun malformedDirectorySuppressesCleanupButCompletes() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(file("old.md", "old.md", 1, 1))
            f.scanner.scan(LibraryId(lib))

            // The subdirectory row is malformed (blank id): its absence evidence
            // is unprovable, so the vanished old.md must survive this run.
            f.lister.children["root"] =
                listOf(
                    TreeEntry("", "", true, null, null, ""),
                    file("new.md", "new.md", 1, 1),
                )
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(0, result.deleted)
            assertEquals(1, result.entryErrors.size)
            assertTrue(result.entryErrors.first().isDirectory)
            assertTrue(f.docs.rows.values.any { it.relativePath == "old.md" })
        }

    @Test
    fun malformedFileRowsAreRecordedAndSkipped() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    TreeEntry("", "noname.md", false, 1, 1, "content://doc/x"),
                    TreeEntry("noid", "", false, 1, 1, "content://doc/y"),
                    file("ok", "ok.md", 1, 1),
                )

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.seen)
            assertEquals(2, result.entryErrors.size)
            assertEquals(listOf("ok.md"), f.docs.rows.values.map { it.relativePath })
        }

    @Test
    fun visitedOverflowFailsExplicitlyWithOldIndexKept() =
        runTest(dispatcher) {
            val f = setup(ids = (1..9000).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            // Seed a pre-existing complete index: overflow must keep it.
            f.docs.upsert(doc(lib, "old.md", 1, 1))
            // Unsupported entries still count as visited tree entries, so the
            // visit guard is what fails this walk.
            val children =
                (1..(LibraryScanner.MAX_VISITED_ENTRIES + 10)).map {
                    file("f$it", "file$it.png", 1, 1)
                }
            f.lister.children["root"] = children

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.FAILED, result.status)
            assertEquals(ScanFailureKind.OVERFLOW, result.failureKind)
            assertEquals(5000, LibraryScanner.MAX_VISITED_ENTRIES)
            assertTrue(result.failureReason!!.contains(LibraryScanner.MAX_VISITED_ENTRIES.toString()))
            // Overflow commits zero index writes: the seeded row survives and
            // nothing of the aborted walk is persisted.
            assertEquals(listOf("old.md"), f.docs.rows.values.map { it.relativePath })
            assertEquals(ScanState.FAILED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    @Test
    fun documentsAboveFormerFiveHundredCapAreIndexedCompletely() =
        runTest(dispatcher) {
            // The removed cap failed the 501st supported file and wrote
            // nothing. A library past that size must commit every document.
            val count = 800
            val f = setup(ids = (1..(count + 5)).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                (1..count).map { file("g$it", "doc$it.md", 1, 1) }

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertNull(result.failureKind)
            assertEquals(count, result.inserted)
            assertEquals(count, f.docs.rows.size)
        }

    // -- R3-02: precise matrix / multi-library / caps / metadata-only -----------

    @Test
    fun preciseDiscoveryMatrixCounts() =
        runTest(dispatcher) {
            // 20 documents across 5 directories + 3 empty directories + 1
            // document in a nested directory (the matrix the verdict lists as
            // PARTIAL): exact counts, hierarchy preserved, empty dirs produce
            // no documents and no phantom rows.
            val f = setup(ids = (1..200).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            val root = ArrayList<TreeEntry>()
            for (d in 1..5) {
                val dirId = "dir$d"
                root.add(dir(dirId, "folder$d"))
                f.lister.children[dirId] =
                    (1..4).map { file("f$d-$it", "doc$d-$it.md", 10, 100) }
            }
            for (e in 1..3) {
                val emptyId = "empty$e"
                root.add(dir(emptyId, "empty$e"))
                f.lister.children[emptyId] = emptyList()
            }
            root.add(dir("nested", "nested"))
            f.lister.children["nested"] = listOf(dir("nested-sub", "sub"))
            f.lister.children["nested-sub"] = listOf(file("deep-1", "deep.md", 10, 100))
            f.lister.children["root"] = root

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals("20 + 1 nested documents", 21, result.inserted)
            assertEquals(21, f.docs.rows.size)
            assertEquals(
                "hierarchy preserved (4 docs in folder1)",
                listOf(
                    "folder1/doc1-1.md",
                    "folder1/doc1-2.md",
                    "folder1/doc1-3.md",
                    "folder1/doc1-4.md",
                ),
                f.docs.rows.values
                    .map { it.relativePath }
                    .sorted()
                    .filter { it.startsWith("folder1") },
            )
            assertTrue(
                "nested document indexed",
                f.docs.rows.values.any { it.relativePath == "nested/sub/deep.md" },
            )
            assertTrue(
                "no row for empty directories",
                f.docs.rows.values.none { it.relativePath.contains("empty") },
            )
            // LT-3: 5 document folders + 3 empty folders + nested + nested/sub
            // are all indexed as path-only rows, in one successful scan.
            assertEquals(
                listOf(
                    "empty1",
                    "empty2",
                    "empty3",
                    "folder1",
                    "folder2",
                    "folder3",
                    "folder4",
                    "folder5",
                    "nested",
                    "nested/sub",
                ),
                f.dirs.rows.values.map { it.relativePath }.sorted(),
            )
        }

    @Test
    fun multipleLibrariesStayIsolated() =
        runTest(dispatcher) {
            // Two libraries exposing the SAME relative path must not collide,
            // and removing one must leave the other's rows intact (REQ-0001).
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val a = f.addLibrary("content://tree/a")
            val b = f.addLibrary("content://tree/b")
            // Distinct tree roots (the helper maps every URI to "root").
            f.lister.roots["content://tree/a"] = "rootA"
            f.lister.roots["content://tree/b"] = "rootB"
            f.lister.children["rootA"] = listOf(file("a1", "same.md", 10, 100))
            f.lister.children["rootB"] = listOf(file("b1", "same.md", 10, 100))

            assertEquals(ScanStatus.SUCCEEDED, f.scanner.scan(LibraryId(a)).status)
            assertEquals(ScanStatus.SUCCEEDED, f.scanner.scan(LibraryId(b)).status)

            val paths = f.docs.rows.values.filter { it.relativePath == "same.md" }
            assertEquals("one row per library", 2, paths.size)
            assertEquals(2, paths.map { it.libraryId }.toSet().size)

            f.repo.removeLibrary(LibraryId(a))
            val remaining = f.docs.rows.values.filter { it.relativePath == "same.md" }
            assertEquals("removing A leaves B's row", 1, remaining.size)
            assertEquals(b, remaining.single().libraryId)
            assertTrue("B's grant is not released", "content://tree/b" in f.access.grantedPermissions)
        }

    @Test
    fun scanIsMetadataOnlyAndNeverReadsFileContent() =
        runTest(dispatcher) {
            // REQ-0001 FR-1/AC-1: the first scan indexes metadata only. The
            // scanner holds no ContentAccess at all — the only SAF surface it
            // touches is the tree lister — so the access fake must stay
            // untouched (no openInput, no grant churn) even with many files.
            val f = setup(ids = (1..200).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = (1..25).map { file("m$it", "m$it.md", 10, 100) }

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(25, f.docs.rows.size)
            assertTrue("no stream opened", f.access.closedUris.isEmpty())
            assertTrue("only the library grant was taken", f.access.takenPermissions.size == 1)
            assertTrue("no inline parse stored a hash", f.docs.rows.values.all { it.contentHash == null })
        }

    // -- Deep trees + slow queries ------------------------------------------------

    @Test
    fun deepTreeIsWalkedFully() =
        runTest(dispatcher) {
            val f = setup(ids = (1..100).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            var parent = "root"
            val depth = 18
            for (level in 1..depth) {
                val dirId = "dir$level"
                f.lister.children[parent] = listOf(dir(dirId, "level$level"))
                parent = dirId
            }
            f.lister.children[parent] = listOf(file("deep", "deep.md", 3, 3))

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.seen)
            val row = f.docs.rows.values.single()
            assertTrue(row.relativePath.endsWith("deep.md"))
            assertEquals(depth + 1, row.relativePath.split('/').size)
        }

    @Test
    fun cycleIsBrokenWithoutHanging() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            // Same document id enqueued twice: second visit is skipped, no loop.
            f.lister.children["root"] = listOf(dir("loop", "Loop"))
            f.lister.children["loop"] = listOf(dir("root", "BackToRoot"), file("x", "x.md", 1, 1))

            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.seen)
        }

    // -- Cancellation ---------------------------------------------------------------

    @Test
    fun cancelCommitsNothingAndReportsCancelled() =
        runTest(dispatcher) {
            val f = setup(ids = (1..500).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(file("old.md", "old.md", 1, 1))
            f.scanner.scan(LibraryId(lib))
            val before = f.docs.rows.values.map { it.copy() }

            // Slow lister + many dirs so the scan is still walking when cancelled.
            var parent = "root"
            for (level in 1..30) {
                val dirId = "c$level"
                val existing = f.lister.children[parent].orEmpty()
                f.lister.children[parent] = existing + dir(dirId, "d$level")
                parent = dirId
            }
            f.lister.delayMs = 50
            val job = async(dispatcher) { f.scanner.scan(LibraryId(lib)) }
            delay(120)
            job.cancel()
            try {
                job.await()
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                // Expected: cancellation propagates untouched.
            }

            val after = f.docs.rows.values.map { it.copy() }
            assertEquals(before, after)
            // LT-3: the folders discovered before the cancel are not committed.
            assertTrue(f.dirs.rows.isEmpty())
            assertEquals(ScanState.CANCELLED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    // -- Concurrency -----------------------------------------------------------------

    @Test
    fun concurrentRefreshesOfOneLibrarySerialize() =
        runTest(dispatcher) {
            val f = setup(ids = (1..100).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(file("a", "a.md", 1, 1))
            val order = mutableListOf<String>()
            val slowLister =
                object : TreeLister {
                    override suspend fun treeDocumentId(treeUri: String): String = "root"

                    override suspend fun listChildren(
                        treeUri: String,
                        parentDocumentId: String,
                    ): List<TreeEntry> {
                        order.add("enter")
                        delay(80)
                        order.add("exit")
                        return listOf(TreeEntry("a", "a.md", false, 1, 1, "content://doc/a"))
                    }
                }
            val slowScanner =
                LibraryScanner(
                    db = FakeDatabase(f.libs, f.docs, FakeReadingPositionDao(), FakeRecentReadDao()),
                    lister = slowLister,
                    repository = f.repo,
                    ioDispatcher = dispatcher,
                    clock = { 9_000L },
                    idGenerator = { "doc-x-${System.nanoTime()}" },
                )

            val first: Job = launch(dispatcher) { slowScanner.scan(LibraryId(lib)) }
            delay(10)
            val second: Job = launch(dispatcher) { slowScanner.scan(LibraryId(lib)) }
            first.join()
            second.join()

            // Serialized: enter/exit pairs never interleave.
            assertEquals(listOf("enter", "exit", "enter", "exit"), order)
            assertEquals(ScanState.SUCCEEDED, f.repo.observeScanState(LibraryId(lib)).first())
        }

    // -- Duplicate add is still the repository's job (regression pin) ------------------

    @Test
    fun duplicateAddStillDedupesViaRepository() =
        runTest(dispatcher) {
            val f = setup()
            f.addLibrary("content://tree/docs")
            val second = f.repo.addLibrary("Renamed", "content://tree/docs/")

            assertEquals(1, f.libs.rows.size)
            assertEquals("Docs", second.displayName)
        }

    // -- Progress + throttle ------------------------------------------------------------

    @Test
    fun progressEmitsTerminalSuccessSnapshot() =
        runTest(dispatcher) {
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(file("a", "a.md", 1, 1))

            f.scanner.scan(LibraryId(lib))
            val progress = f.scanner.observeProgress(LibraryId(lib)).first()

            assertEquals(ScanProgressState.SUCCEEDED, progress.state)
            assertEquals(1, progress.scannedFiles)
        }

    @Test
    fun frozenClockSuppressesInWalkEmissions() =
        runTest(dispatcher) {
            var now = 0L
            val f = setup(clock = { now }, ids = (1..100).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = (1..10).map { file("e$it", "f$it.md", 1, 1) }

            // With a frozen clock every in-walk emission is throttled away; only
            // the forced terminal snapshot lands.
            f.scanner.scan(LibraryId(lib))
            val progress = f.scanner.observeProgress(LibraryId(lib)).first()

            assertEquals(ScanProgressState.SUCCEEDED, progress.state)
            assertEquals(10, progress.scannedFiles)
            assertEquals(0, progress.entryErrors)
        }

    // -- Pure helpers ---------------------------------------------------------------------

    @Test
    fun extensionMatchingIsCaseInsensitive() {
        assertTrue(isSupportedDocument("note.MD"))
        assertTrue(isSupportedDocument("note.Markdown"))
        assertTrue(isSupportedDocument("note.TXT"))
        assertFalse(isSupportedDocument("note.png"))
        assertFalse(isSupportedDocument("note"))
        assertFalse(isSupportedDocument("note."))
        assertEquals("md", extensionOf("A.MD"))
    }

    @Test
    fun defaultDisplayNameDecodesTreeUri() {
        assertEquals("Docs", defaultDisplayNameForTree("content://tree/primary%3ADocs"))
        assertEquals("Docs", defaultDisplayNameForTree("content://tree/primary%3ADocs/"))
        assertEquals("Library", defaultDisplayNameForTree("   "))
        assertEquals("Library", defaultDisplayNameForTree("content://tree/"))
    }

    // -- R2-04 regressions: atomic commit + position preservation -----------

    @Test
    fun failedCommitLeavesIndexByteIdentical() =
        runTest(dispatcher) {
            // Baseline index + a saved position, then a walk whose COMMIT
            // throws on the second write: the transaction rolls everything
            // back — documents, directories, positions, AND lastScannedAt
            // stay identical.
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] =
                listOf(
                    file("a", "a.md", 10, 100),
                    file("b", "b.md", 10, 100),
                    dir("sub", "Sub"),
                )
            f.lister.children["sub"] = listOf(file("c", "c.md", 10, 100))
            f.scanner.scan(LibraryId(lib))
            val docId = f.docs.rows.values.first { it.relativePath == "a.md" }.documentId
            f.repo.savePosition(
                io.leaflet.model.ReadingPosition(
                    documentId = io.leaflet.model.DocumentId(docId),
                    version = 1,
                    blockIndex = 3,
                    blockOffset = 1,
                    progress = 0.5f,
                    anchor = null,
                ),
                lastModified = 100,
                size = 10,
            )
            val docsBefore = f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            val dirsBefore = f.dirs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            val posBefore = f.repo.loadPosition(io.leaflet.model.DocumentId(docId))
            val scannedBefore = f.libs.rows.values.first { it.libraryId == lib }.lastScannedAt
            // Change both files (forces two staged writes), drop the indexed
            // folder (would delete its row), fail on 2nd write.
            f.lister.children["root"] =
                listOf(
                    file("a", "a.md", 11, 100),
                    file("b", "b.md", 12, 100),
                )
            f.docs.failAfter = 1
            val result = f.scanner.scan(LibraryId(lib))
            assertEquals(ScanStatus.FAILED, result.status)
            val docsAfter = f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            assertEquals(docsBefore, docsAfter)
            assertEquals(dirsBefore, f.dirs.rows.values.map { it.copy() }.sortedBy { it.relativePath })
            assertEquals(posBefore, f.repo.loadPosition(io.leaflet.model.DocumentId(docId)))
            assertEquals(
                scannedBefore,
                f.libs.rows.values.first { it.libraryId == lib }.lastScannedAt,
            )
        }

    @Test
    fun failedDirectoryCommitRollsBackTheWholeIndex() =
        runTest(dispatcher) {
            // The directory insert is the last staged write of the commit. If
            // it throws, the ONE transaction rolls back everything, so the
            // document update and `lastScannedAt` from this run do not survive
            // either (REQ-0001 FR-5, LT-3).
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(dir("known", "Known"), file("a", "a.md", 10, 100))
            f.lister.children["known"] = emptyList()
            f.scanner.scan(LibraryId(lib))
            val docsBefore = f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath }
            val scannedBefore = f.libs.rows.values.first { it.libraryId == lib }.lastScannedAt

            // A new folder appears and a.md changes; the insertAll throws.
            f.lister.children["root"] =
                listOf(dir("known", "Known"), dir("new", "New"), file("a", "a.md", 11, 100))
            f.lister.children["new"] = emptyList()
            f.dirs.failAfter = 1 // listByLibrary passes, insertAll throws inside the txn
            val result = f.scanner.scan(LibraryId(lib))

            assertEquals(ScanStatus.FAILED, result.status)
            assertEquals(docsBefore, f.docs.rows.values.map { it.copy() }.sortedBy { it.relativePath })
            assertEquals(listOf("Known"), f.dirs.rows.values.map { it.relativePath })
            assertEquals(
                scannedBefore,
                f.libs.rows.values.first { it.libraryId == lib }.lastScannedAt,
            )
        }

    @Test
    fun updatedDocumentKeepsItsPositionRow() =
        runTest(dispatcher) {
            // F05 core: re-indexing a changed file MUST NOT delete its
            // position (old REPLACE-via-upsert dropped the CASCADE child).
            val f = setup(ids = (1..50).map { "doc-$it" }.iterator())
            val lib = f.addLibrary()
            f.lister.children["root"] = listOf(file("a", "a.md", 10, 100))
            f.scanner.scan(LibraryId(lib))
            val docId = f.docs.rows.values.first { it.relativePath == "a.md" }.documentId
            f.repo.savePosition(
                io.leaflet.model.ReadingPosition(
                    documentId = io.leaflet.model.DocumentId(docId),
                    version = 1,
                    blockIndex = 5,
                    blockOffset = 2,
                    progress = 0.7f,
                    anchor = "H",
                ),
                lastModified = 100,
                size = 10,
            )
            f.lister.children["root"] = listOf(file("a", "a.md", 11, 100))
            val result = f.scanner.scan(LibraryId(lib))
            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.updated)
            val kept = f.repo.loadPosition(io.leaflet.model.DocumentId(docId))
            assertEquals(5, kept?.blockIndex)
            assertEquals(0.7f, kept?.progress)
        }

    private fun doc(
        libraryId: String,
        path: String,
        size: Long,
        mtime: Long,
    ): DocumentEntity =
        DocumentEntity(
            documentId = "$libraryId/$path",
            libraryId = libraryId,
            relativePath = path,
            uri = "content://doc/$path",
            displayName = path.substringAfterLast('/'),
            title = null,
            extension = "md",
            size = size,
            lastModified = mtime,
            contentHash = null,
        )
}
