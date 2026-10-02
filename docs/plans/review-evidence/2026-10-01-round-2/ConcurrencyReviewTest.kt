package io.leaflet.reader.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.*
import io.leaflet.reader.DocumentDescriptor
import io.leaflet.reader.ui.library.LibraryListSource
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.reader.ui.reader.*
import io.leaflet.storage.ScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class ConcurrencyReviewTest {
    private fun withMain(test: (TestCoroutineScheduler, ViewModelStore) -> Unit) {
        val scheduler = TestCoroutineScheduler()
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        try { test(scheduler, owner) } finally {
            owner.clear(); scheduler.runCurrent(); Dispatchers.resetMain()
        }
    }

    private class SaveStore : ReaderStore {
        val oldSaveGate = CompletableDeferred<Unit>()
        var oldStarted = false
        val saved = mutableListOf<ReadingPosition>()
        private val body = (1..8).joinToString("\n\n") { "Paragraph $it" }.toByteArray()
        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)
        override suspend fun describe(documentId: DocumentId) = DocumentDescriptor("a.txt", "42:${body.size}", LibraryId("lib"), "a")
        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null
        override suspend fun resolveRelative(fromId: DocumentId, ref: String): io.leaflet.storage.ResolvedResource? = null
        override suspend fun recordRecent(entry: RecentRead) = Unit
        override suspend fun savePosition(position: ReadingPosition, lastModified: Long, size: Long) {
            if (position.blockIndex == 2) { oldStarted = true; oldSaveGate.await() }
            saved.add(position)
        }
    }

    @Test
    fun anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush() = withMain { main, owner ->
        val io = TestCoroutineScheduler()
        val store = SaveStore()
        val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), StandardTestDispatcher(io))
        owner.put("reader", vm)
        vm.open(DocumentId("a"))
        repeat(20) { main.runCurrent(); io.runCurrent(); main.advanceTimeBy(16) }
        assertTrue(vm.ui.value is ReaderUiState.Content)
        vm.jumpToBlock(2); io.runCurrent()
        assertTrue("older write is in flight", store.oldStarted)
        vm.jumpToBlock(5); io.runCurrent()
        val flush = vm.flushPosition(); io.runCurrent()
        assertTrue("latest exit flush completed", flush.isCompleted)
        assertEquals(5, store.saved.last().blockIndex)
        store.oldSaveGate.complete(Unit); io.runCurrent()
        assertEquals("latest address must survive older completion; writes=${store.saved.map { it.blockIndex }}", 5, store.saved.last().blockIndex)
    }

    @Test
    fun aSlowEarlierLibraryOpenMustNotReplaceTheCurrentLibrary() = withMain { main, owner ->
        val gate = CompletableDeferred<Unit>()
        val source = object : LibraryListSource {
            override suspend fun addLibrary(displayName: String, rootTreeUri: String) = Library(LibraryId("unused"), displayName, rootTreeUri)
            override fun observeLibraries(): Flow<List<Library>> = flowOf(emptyList())
            override fun observeScanState(libraryId: LibraryId) = flowOf(ScanState.IDLE)
            override suspend fun removeLibrary(libraryId: LibraryId) = Unit
            override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId) = emptyList<String>()
            override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> {
                if (libraryId.value == "a") gate.await()
                return listOf(Document(DocumentId("${libraryId.value}-doc"), libraryId, "read.txt", "read.txt", "content://doc/${libraryId.value}", "txt"))
            }
        }
        val vm = LibraryViewModel(source, null); owner.put("library", vm)
        vm.openLibrary(LibraryId("a")); main.runCurrent()
        vm.openLibrary(LibraryId("b")); main.runCurrent()
        assertEquals("b", vm.browse.value.libraryId)
        gate.complete(Unit); main.runCurrent()
        assertEquals("B must stay selected after A completes", "b", vm.browse.value.libraryId)
    }

    @Test
    fun completedRecentMetadataMustPublishHealthAndLibraryName() = withMain { main, owner ->
        val gate = CompletableDeferred<Unit>()
        val entry = RecentRead(DocumentId("doc"), "Title", LibraryId("lib"), 1L)
        val entries = MutableStateFlow(listOf(entry))
        var healthChecked = false
        val store = object : RecentStore {
            override fun observeRecent(limit: Int): Flow<List<RecentRead>> = entries
            override suspend fun resolveLibraryName(libraryId: LibraryId): String { gate.await(); return "Actual library" }
            override suspend fun checkHealth(entry: RecentRead): RecentHealth { healthChecked = true; return RecentHealth.AUTH_LOST }
            override suspend fun removeRecent(documentId: DocumentId) = Unit
            override suspend fun clearRecent() = Unit
        }
        val vm = RecentViewModel(store); owner.put("recent", vm); main.runCurrent()
        assertEquals(RecentHealth.UNKNOWN, vm.rows.value.single().health)
        gate.complete(Unit); main.runCurrent()
        assertTrue("health lookup actually finished", healthChecked)
        assertEquals("metadata completion must update rows", RecentHealth.AUTH_LOST, vm.rows.value.single().health)
        assertEquals("Actual library", vm.rows.value.single().libraryName)
    }
}
