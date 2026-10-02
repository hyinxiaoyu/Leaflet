package io.leaflet.reader.review

import androidx.lifecycle.ViewModelStore
import io.leaflet.model.*
import io.leaflet.reader.ui.library.LibraryListSource
import io.leaflet.reader.ui.library.LibraryViewModel
import io.leaflet.storage.*
import io.leaflet.storage.db.DocumentEntity
import io.leaflet.storage.repo.LibraryRepositoryImpl
import io.leaflet.storage.scan.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRefreshAcceptanceTest {
    @Test
    fun completedEarlierRefreshCannotReplaceTheSelectedLibraryRowsOrProgress() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val io = UnconfinedTestDispatcher(testScheduler)
            val docs = FakeDocumentDao(); val positions = FakeReadingPositionDao()
            val libs = FakeLibraryDao(docs, positions)
            val db = FakeDatabase(libs, docs, positions, FakeRecentReadDao())
            var nextId = 0
            val repo = LibraryRepositoryImpl(db, FakeContentAccess(), io, idGenerator = { "lib-${++nextId}" })
            val a = repo.addLibrary("A", "content://tree/a")
            val b = repo.addLibrary("B", "content://tree/b")
            docs.upsert(DocumentEntity("b-doc", b.id.value, "b.txt", "content://doc/b", "b.txt", "B document", "txt", 1L, 1L, null))
            val lister = object : TreeLister {
                override suspend fun treeDocumentId(treeUri: String) = "root"
                override suspend fun listChildren(treeUri: String, parentDocumentId: String) = emptyList<TreeEntry>()
            }
            val scanner = LibraryScanner(db, lister, repo, io)
            var holdRefreshSnapshot = false
            var refreshSnapshotStarted = false
            val gate = CompletableDeferred<Unit>()
            val source = object : LibraryListSource {
                override suspend fun addLibrary(displayName: String, rootTreeUri: String) = repo.addLibrary(displayName, rootTreeUri)
                override fun observeLibraries() = repo.observeLibraries()
                override fun observeScanState(libraryId: LibraryId) = repo.observeScanState(libraryId)
                override suspend fun removeLibrary(libraryId: LibraryId) = repo.removeLibrary(libraryId)
                override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId) = repo.listDirectories(libraryId)
                override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> {
                    val snapshot = repo.listDocuments(libraryId)
                    if (libraryId == a.id && holdRefreshSnapshot) {
                        refreshSnapshotStarted = true
                        // Simulate provider/query work that returns after cancellation.
                        withContext(NonCancellable) { gate.await() }
                    }
                    return snapshot
                }
            }
            val vm = LibraryViewModel(source, scanner); owner.put("refresh-acceptance", vm)
            vm.openLibrary(a.id); testScheduler.runCurrent()
            assertEquals(a.id.value, vm.browse.value.libraryId)
            holdRefreshSnapshot = true
            vm.refresh(a.id); testScheduler.runCurrent()
            assertTrue("A refresh reached its metadata publication boundary", refreshSnapshotStarted)
            vm.openLibrary(b.id); testScheduler.runCurrent()
            assertEquals(b.id.value, vm.browse.value.libraryId)
            assertEquals(listOf("b-doc"), vm.browse.value.documents.map { it.id.value })
            gate.complete(Unit); testScheduler.runCurrent()
            assertEquals("B", vm.browse.value.displayName)
            assertEquals(b.id.value, vm.browse.value.libraryId)
            assertEquals(listOf("b-doc"), vm.browse.value.documents.map { it.id.value })
            assertEquals("old A progress must not leak into B", b.id.value, vm.browse.value.progress.libraryId)
        } finally {
            owner.clear(); testScheduler.runCurrent(); Dispatchers.resetMain()
        }
    }
}
