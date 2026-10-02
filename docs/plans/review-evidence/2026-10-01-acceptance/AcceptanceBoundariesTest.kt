package io.leaflet.reader.review

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.ui.reader.ReaderStore
import io.leaflet.reader.ui.reader.ReaderUiState
import io.leaflet.reader.ui.reader.ReaderViewModel
import io.leaflet.reader.ui.reader.StoredPosition
import io.leaflet.storage.*
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.scan.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptanceBoundariesTest {
    private class HeldDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var hold = false
        private val pending = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (hold) pending.add(block) else delegate.dispatch(context, block)
        }
        fun releaseNewestFirst() {
            while (pending.isNotEmpty()) pending.removeAt(pending.lastIndex).run()
        }
    }

    private class Store : ReaderStore {
        val writes = mutableListOf<ReadingPosition>()
        private val body = (1..8).joinToString("\n\n") { "Paragraph $it" }.toByteArray()
        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)
        override suspend fun describe(documentId: DocumentId) = DocumentDescriptor("a.txt", "42:${body.size}", LibraryId("lib"), "a")
        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null
        override suspend fun resolveRelative(fromId: DocumentId, ref: String): io.leaflet.storage.ResolvedResource? = null
        override suspend fun recordRecent(entry: RecentRead) = Unit
        override suspend fun savePosition(position: ReadingPosition, lastModified: Long, size: Long) { writes.add(position) }
    }

    @Test
    fun newestRequestedPositionMustWinWhenIOJobsStartInReverseOrder() {
        val main = TestCoroutineScheduler()
        val io = TestCoroutineScheduler()
        val dispatcher = HeldDispatcher(StandardTestDispatcher(io))
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(main))
        try {
            val store = Store()
            val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), dispatcher)
            owner.put("acceptance", vm)
            vm.open(DocumentId("a"))
            repeat(20) { main.runCurrent(); io.runCurrent(); main.advanceTimeBy(16) }
            assertTrue(vm.ui.value is ReaderUiState.Content)
            dispatcher.hold = true
            vm.jumpToBlock(2)
            vm.jumpToBlock(5)
            val flush = vm.flushPosition()
            assertTrue("IO writes are still held", store.writes.isEmpty())
            dispatcher.releaseNewestFirst()
            assertTrue("exit flush completed", flush.isCompleted)
            assertEquals("invocation order must win, not IO-start order; writes=${store.writes.map { it.blockIndex }}", 5, store.writes.last().blockIndex)
        } finally {
            owner.clear(); dispatcher.releaseNewestFirst(); main.runCurrent(); io.runCurrent(); Dispatchers.resetMain()
        }
    }

    @Test
    fun missingOnlyMimeMustNotDeleteThePreviouslyIndexedDirectory() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val docs = FakeDocumentDao(); val positions = FakeReadingPositionDao()
        val libs = FakeLibraryDao(docs, positions)
        val db = FakeDatabase(libs, docs, positions, FakeRecentReadDao())
        val repo = LibraryRepositoryImpl(db, FakeContentAccess(), dispatcher, idGenerator = { "lib" })
        val lib = repo.addLibrary("Docs", "content://tree/root")
        var missingMime = false
        val lister = object : TreeLister {
            override suspend fun treeDocumentId(treeUri: String) = "root"
            override suspend fun listChildren(treeUri: String, parentDocumentId: String): List<TreeEntry> =
                if (parentDocumentId == "root") {
                    if (missingMime) listOf(classifyTreeRow("dir-id", "folder", null, null, null))
                    else listOf(TreeEntry("dir-id", "folder", true, null, null, "content://dir"))
                } else listOf(TreeEntry("file-id", "keep.md", false, 1L, 1L, "content://file"))
        }
        val scanner = LibraryScanner(db, lister, repo, dispatcher, idGenerator = { "doc" })
        scanner.scan(lib.id)
        assertEquals(listOf("folder/keep.md"), docs.rows.values.map { it.relativePath })
        missingMime = true
        val result = scanner.scan(lib.id)
        assertEquals("a row with ID/name but unknown MIME cannot prove a subtree vanished", 0, result.deleted)
        assertEquals(1, docs.rows.size)
    }
}
