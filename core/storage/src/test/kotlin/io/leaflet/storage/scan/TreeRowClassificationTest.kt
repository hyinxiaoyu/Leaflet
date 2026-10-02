package io.leaflet.storage.scan

import io.leaflet.model.LibraryId
import io.leaflet.storage.FakeContentAccess
import io.leaflet.storage.FakeDatabase
import io.leaflet.storage.FakeDocumentDao
import io.leaflet.storage.FakeLibraryDao
import io.leaflet.storage.FakeReadingPositionDao
import io.leaflet.storage.FakeRecentReadDao
import io.leaflet.storage.ScanStatus
import io.leaflet.storage.repo.LibraryRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CR-20261001-05 (round-2 review): incomplete provider metadata must not be
 * turned into a classification the provider did not state.
 *
 * A directory row that lost its display name (or id) used to be rebuilt as a
 * "file" with an empty identity, which let the scanner treat the folder as
 * vanished and delete its previously indexed subtree. Two rules close it:
 * [classifyTreeRow] keeps the type/identity the row does provide, and
 * [LibraryScanner] treats any row it could not fully classify as insufficient
 * absence evidence for the whole walk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TreeRowClassificationTest {
    // -- classification (pure, no Android runtime) ---------------------------

    @Test
    fun aNamelessDirectoryRowStaysADirectory() {
        val row =
            classifyTreeRow(
                documentId = "dir-id",
                displayName = "",
                mimeType = android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                size = null,
                lastModified = null,
            )
        assertTrue("a folder without a name is still a folder", row.isDirectory)
        assertEquals("its known identity is kept", "dir-id", row.documentId)
    }

    @Test
    fun anIdentityLessDirectoryRowStaysADirectory() {
        val row =
            classifyTreeRow(
                documentId = "",
                displayName = "folder",
                mimeType = android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                size = null,
                lastModified = null,
            )
        assertTrue("a folder without an id is still a folder", row.isDirectory)
        assertEquals("folder", row.displayName)
    }

    @Test
    fun anUnclassifiableRowKeepsEverythingItDoesProvide() {
        // Missing name AND missing MIME: the type is unknown, so the row must not
        // claim to be a file that can prove another entry gone.
        val unknown = classifyTreeRow(documentId = "id-1", displayName = "", mimeType = null, size = null, lastModified = null)
        assertFalse("an unknown type is not asserted as a file", unknown.isDirectory)
        assertEquals("the known id is preserved", "id-1", unknown.documentId)

        // A row with a name but no MIME keeps the name and reports a file type
        // (the scanner still suppresses cleanup for it: see the scan tests).
        val named = classifyTreeRow(documentId = "", displayName = "readme.md", mimeType = null, size = 1L, lastModified = 2L)
        assertFalse(named.isDirectory)
        assertEquals("readme.md", named.displayName)
        assertEquals(1L, named.size)
        assertEquals(2L, named.lastModified)
    }

    // -- scanner behaviour with the previously indexed subtree ---------------

    private class Fixture(
        val docs: FakeDocumentDao,
        val db: FakeDatabase,
        val repo: LibraryRepositoryImpl,
        val lib: io.leaflet.model.Library,
    )

    private suspend fun fixture(): Fixture {
        val docs = FakeDocumentDao()
        val positions = FakeReadingPositionDao()
        val libs = FakeLibraryDao(docs, positions)
        val db = FakeDatabase(libs, docs, positions, FakeRecentReadDao())
        val repo = LibraryRepositoryImpl(db, FakeContentAccess(), UnconfinedTestDispatcher(), idGenerator = { "lib" })
        val lib = repo.addLibrary("Docs", "content://tree/root")
        return Fixture(docs, db, repo, lib)
    }

    private fun lister(damagedRow: () -> TreeEntry?): TreeLister =
        object : TreeLister {
            override suspend fun treeDocumentId(treeUri: String) = "root"

            override suspend fun listChildren(
                treeUri: String,
                parentDocumentId: String,
            ): List<TreeEntry> =
                if (parentDocumentId == "root") {
                    listOfNotNull(
                        damagedRow() ?: TreeEntry("dir-id", "folder", true, null, null, "content://dir"),
                    )
                } else {
                    listOf(TreeEntry("file-id", "keep.md", false, 1L, 1L, "content://file"))
                }
        }

    @Test
    fun aDamagedDirectoryRowMustNotDeleteItsIndexedSubtree() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val f = fixture()
            var damaged = false
            val scanner =
                LibraryScanner(
                    f.db,
                    lister { if (damaged) classifyTreeRow("dir-id", "", "vnd.android.document/directory", null, null) else null },
                    f.repo,
                    dispatcher,
                    idGenerator = { "doc" },
                )
            scanner.scan(LibraryId(f.lib.id.value))
            assertEquals(listOf("folder/keep.md"), f.docs.rows.values.map { it.relativePath })

            damaged = true
            val result = scanner.scan(f.lib.id)

            assertEquals("the malformed directory must not delete its subtree", 0, result.deleted)
            assertEquals(1, f.docs.rows.size)
            assertEquals(ScanStatus.SUCCEEDED, result.status)
            assertEquals(1, result.entryErrors.size)
        }

    @Test
    fun anUnclassifiableRowMustNotDeleteAnythingEither() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val f = fixture()
            var damaged = false
            // Name AND MIME missing: the row is not provably a directory, but it
            // is also not provably absent — the walk cannot prove the old index
            // wrong, so nothing may be deleted.
            val scanner =
                LibraryScanner(
                    f.db,
                    lister { if (damaged) classifyTreeRow("dir-id", "", null, null, null) else null },
                    f.repo,
                    dispatcher,
                    idGenerator = { "doc" },
                )
            scanner.scan(LibraryId(f.lib.id.value))
            assertEquals(listOf("folder/keep.md"), f.docs.rows.values.map { it.relativePath })

            damaged = true
            val result = scanner.scan(f.lib.id)

            assertEquals("partial enumeration must not become disappearance cleanup", 0, result.deleted)
            assertEquals(1, f.docs.rows.size)
            assertEquals(1, result.entryErrors.size)
        }

    /**
     * Independent-acceptance counterexample
     * (`AcceptanceBoundariesTest.missingOnlyMimeMustNotDeleteThePreviouslyIndexedDirectory`):
     * the row keeps its ID and display name and only its MIME type is missing. It
     * must still be reported as an UNKNOWN type, not as a file — otherwise the
     * scanner skips it as an unsupported document and deletes the folder's
     * previously indexed subtree as "absent".
     */
    @Test
    fun anUnknownMimeRowIsNotClassifiedAsAFile() {
        val unknown = classifyTreeRow("dir-id", "folder", null, null, null)
        assertTrue("the type must be reported as unknown", unknown.typeUnknown)
        assertFalse("an unknown type is not a directory", unknown.isDirectory)
        assertEquals("the known id is preserved", "dir-id", unknown.documentId)
        assertEquals("the known name is preserved", "folder", unknown.displayName)

        // A stated file type is still a file, and a stated directory is still a
        // directory: only the absent MIME is unknown.
        val file = classifyTreeRow("file-id", "keep.md", "text/markdown", 1L, 2L)
        assertFalse("a stated file MIME is not unknown", file.typeUnknown)
        assertFalse(file.isDirectory)
        val directory = classifyTreeRow("dir-id", "folder", "vnd.android.document/directory", null, null)
        assertFalse("a stated directory MIME is not unknown", directory.typeUnknown)
        assertTrue(directory.isDirectory)
    }

    @Test
    fun missingOnlyMimeMustNotDeleteThePreviouslyIndexedDirectory() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val f = fixture()
            var missingMime = false
            // ID and display name stay intact; only the MIME value disappears.
            val scanner =
                LibraryScanner(
                    f.db,
                    lister { if (missingMime) classifyTreeRow("dir-id", "folder", null, null, null) else null },
                    f.repo,
                    dispatcher,
                    idGenerator = { "doc" },
                )
            scanner.scan(LibraryId(f.lib.id.value))
            assertEquals(listOf("folder/keep.md"), f.docs.rows.values.map { it.relativePath })

            missingMime = true
            val result = scanner.scan(f.lib.id)

            assertEquals("a row with ID/name but unknown MIME cannot prove a subtree vanished", 0, result.deleted)
            assertEquals(1, f.docs.rows.size)
            assertEquals(1, result.entryErrors.size)
        }
}
