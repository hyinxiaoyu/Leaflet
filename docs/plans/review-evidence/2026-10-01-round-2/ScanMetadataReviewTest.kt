package io.leaflet.reader.review

import android.database.Cursor
import android.net.Uri
import io.leaflet.model.LibraryId
import io.leaflet.storage.*
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.scan.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class ScanMetadataReviewTest {
    // Exercise the production cursor parser without constructing Android's resolver.
    // This malformed-row branch does not use the tree Uri or resolver.
    private fun parseMalformedDirectory(): TreeEntry {
        val columns = listOf("document_id", "_display_name", "mime_type", "_size", "last_modified")
        val values = listOf("dir-id", "", "vnd.android.document/directory", "0", "1")
        var moved = false
        val cursor = Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, args ->
            when (method.name) {
                "getColumnIndex" -> columns.indexOf(args!![0] as String)
                "getString" -> values[args!![0] as Int]
                "moveToNext" -> (!moved).also { moved = true }
                else -> error("Unexpected cursor call: ${method.name}")
            }
        } as Cursor
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null) as Unsafe
        val reader = unsafe.allocateInstance(ContentResolverTreeLister::class.java)
        val method = ContentResolverTreeLister::class.java.getDeclaredMethod("readEntries", Uri::class.java, Cursor::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = method.invoke(reader, null, cursor) as List<TreeEntry>
        return entries.single()
    }

    @Test
    fun missingDirectoryNameMustNotAuthorizeDeletingItsPreviouslyIndexedSubtree() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val docs = FakeDocumentDao(); val positions = FakeReadingPositionDao()
        val libs = FakeLibraryDao(docs, positions)
        val db = FakeDatabase(libs, docs, positions, FakeRecentReadDao())
        var nextId = 0
        val repo = LibraryRepositoryImpl(db, FakeContentAccess(), dispatcher, idGenerator = { "lib" })
        val lib = repo.addLibrary("Docs", "content://tree/root")
        var damaged = false
        val malformed = parseMalformedDirectory()
        val lister = object : TreeLister {
            override suspend fun treeDocumentId(treeUri: String) = "root"
            override suspend fun listChildren(treeUri: String, parentDocumentId: String): List<TreeEntry> =
                if (parentDocumentId == "root") {
                    if (damaged) listOf(malformed) else listOf(TreeEntry("dir-id", "folder", true, null, null, "content://dir"))
                } else listOf(TreeEntry("file-id", "keep.md", false, 1L, 1L, "content://file"))
        }
        val scanner = LibraryScanner(db, lister, repo, dispatcher, idGenerator = { "doc-${++nextId}" })
        scanner.scan(LibraryId(lib.id.value))
        assertEquals(listOf("folder/keep.md"), docs.rows.values.map { it.relativePath })
        damaged = true
        val result = scanner.scan(lib.id)
        assertEquals("malformed directory classified=${malformed.isDirectory}; old subtree must survive", 0, result.deleted)
        assertEquals(1, docs.rows.size)
    }
}
