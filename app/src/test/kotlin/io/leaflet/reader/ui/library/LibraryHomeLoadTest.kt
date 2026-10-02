package io.leaflet.reader.ui.library

import androidx.lifecycle.ViewModelStore
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryHomeLoadTest {
    private lateinit var owner: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        owner = ViewModelStore()
    }

    @After
    fun tearDown() {
        owner.clear()
        Dispatchers.resetMain()
    }

    private class Source : LibraryListSource {
        var fail = false
        var failScan = false
        var gate = CompletableDeferred<Unit>()

        override fun observeLibraries(): Flow<List<Library>> =
            flow {
                gate.await()
                if (fail) throw java.io.IOException("index unavailable")
                emit(listOf(Library(LibraryId("one"), "Notes", "content://one")))
                awaitCancellation()
            }

        override fun observeScanState(libraryId: LibraryId): Flow<ScanState> =
            flow {
                if (failScan) throw java.io.IOException("status unavailable")
                emit(ScanState.IDLE)
                awaitCancellation()
            }

        override suspend fun addLibrary(
            displayName: String,
            rootTreeUri: String,
        ): Library = error("unused")

        override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = emptyList()

        override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = emptyList()

        override suspend fun removeLibrary(libraryId: LibraryId) = Unit
    }

    @Test
    fun pendingIndexDoesNotPretendThisIsFirstUse() {
        val source = Source()
        val vm = LibraryViewModel(source, null)
        owner.put("home", vm)
        assertTrue(vm.list.value.loading)
        source.gate.complete(Unit)
        assertFalse(vm.list.value.loading)
        assertEquals(1, vm.list.value.libraries.size)
    }

    @Test
    fun aFailedIndexCanBeRetriedWithoutDroppingToTheEmptyGuide() {
        val source =
            Source().apply {
                fail = true
                gate.complete(Unit)
            }
        val vm = LibraryViewModel(source, null)
        owner.put("home", vm)
        assertTrue(vm.list.value.loadFailed)
        assertFalse(vm.list.value.loading)
        source.fail = false
        vm.retryLibraries()
        assertFalse(vm.list.value.loadFailed)
        assertEquals("Notes", vm.list.value.libraries.single().displayName)
    }

    @Test
    fun aFailedFolderStatusDoesNotCancelTheLibraryList() {
        val source =
            Source().apply {
                failScan = true
                gate.complete(Unit)
            }
        val vm = LibraryViewModel(source, null)
        owner.put("home", vm)
        assertFalse(vm.list.value.loadFailed)
        assertEquals(1, vm.list.value.libraries.size)
        assertEquals(ScanState.FAILED, vm.list.value.scanStates["one"])
    }
}
