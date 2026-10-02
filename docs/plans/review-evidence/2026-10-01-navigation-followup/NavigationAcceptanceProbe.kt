package io.leaflet.reader.ui.library

import androidx.lifecycle.ViewModelStore
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import io.leaflet.storage.scan.ScanProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Independent acceptance probes; production ViewModel and mapping are compiled unchanged. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationAcceptanceProbe {
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

    private fun viewModel(): LibraryViewModel =
        LibraryViewModel(Source(), null).also { owner.put("acceptance", it) }

    @Test
    fun aRootBreadcrumbFromLibraryAMustNotNavigateLibraryB() {
        val vm = viewModel()
        vm.openLibrary(LibraryId("A"))
        vm.enterDirectory("Docs")
        vm.enterDirectory("Notes")
        val oldRoot = breadcrumbSegments(vm.browse.value.prefix, "A").first().target
        // The breadcrumb request now carries its provenance (REQ-0012 FR-2 after
        // NAV-ACC-01); the stale origin is retained so the scenario is unchanged.
        val staleRequest = AncestorRequest(target = oldRoot, libraryId = "A", prefix = "Docs/Notes")

        vm.openLibrary(LibraryId("B"))
        vm.enterDirectory("Docs")
        vm.enterDirectory("Notes")
        val before = vm.browse.value
        assertEquals("B", before.libraryId)
        assertEquals("Docs/Notes", before.prefix)

        // Simulates an already queued action from the replaced Library's UI.
        vm.openAncestor(staleRequest)

        assertEquals("A's stale root action must not move B out of Docs/Notes", before, vm.browse.value)
    }

    @Test
    fun cancelWithoutAnActiveScanMustNotPublishAnUnfinishableCancellingState() {
        val vm = viewModel()
        vm.openLibrary(LibraryId("A"))
        val before = vm.browse.value
        assertEquals(ScanProgressState.IDLE, before.progress.state)

        // The same entry point can receive a delayed Cancel after a scan has settled.
        vm.cancelRefresh()

        assertEquals("no active scan owns a subsequent terminal-state publication", before, vm.browse.value)
    }

    private class Source : LibraryListSource {
        override suspend fun addLibrary(displayName: String, rootTreeUri: String): Library = error("unused")
        override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = emptyList()
        override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = listOf("Docs", "Docs/Notes")
        override fun observeLibraries(): Flow<List<Library>> = flowOf(emptyList())
        override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = flowOf(ScanState.IDLE)
        override suspend fun removeLibrary(libraryId: LibraryId) = Unit
    }
}
