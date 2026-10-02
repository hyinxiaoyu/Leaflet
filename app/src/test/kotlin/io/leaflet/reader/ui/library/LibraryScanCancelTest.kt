package io.leaflet.reader.ui.library

import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import io.leaflet.storage.scan.ScanProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * NAV-ACC-02 regression (REQ-0012 FR-4, AC-4): cancellation may only be
 * published while a scan is actually running. The independent acceptance
 * reproduced `openLibrary()` followed by `cancelRefresh()` publishing
 * `CANCELLING` with no owner left to publish a terminal state, which would
 * leave the reserved status region stuck on "cancelling".
 *
 * The real-scanner counterpart (Cancel after a scan has settled keeps the
 * terminal state) lives in `LibraryBrowsePageTest`; this file pins the ViewModel
 * contract without a scanner seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScanCancelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cancelWithoutAnActiveScanKeepsTheCurrentState() {
        val viewModel = LibraryViewModel(FakeCancelSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        val before = viewModel.browse.value
        assertEquals(ScanProgressState.IDLE, before.progress.state)

        viewModel.cancelRefresh()

        assertEquals(before, viewModel.browse.value)
        assertEquals(ScanProgressState.IDLE, viewModel.browse.value.progress.state)
    }

    @Test
    fun repeatedCancelWithoutAnActiveScanIsIdempotent() {
        val viewModel = LibraryViewModel(FakeCancelSource(), scanner = null)
        viewModel.openLibrary(LibraryId("docs"))
        val before = viewModel.browse.value

        repeat(3) { viewModel.cancelRefresh() }

        assertEquals(before, viewModel.browse.value)
        assertEquals(ScanProgressState.IDLE, viewModel.browse.value.progress.state)
    }

    @Test
    fun cancelBeforeAnyLibraryIsOpenIsANoOp() {
        val viewModel = LibraryViewModel(FakeCancelSource(), scanner = null)

        viewModel.cancelRefresh()

        assertEquals(ScanProgressState.IDLE, viewModel.browse.value.progress.state)
        assertEquals("", viewModel.browse.value.libraryId)
    }
}

private class FakeCancelSource : LibraryListSource {
    private val libraries = MutableStateFlow<List<Library>>(emptyList())

    override suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library = error("unused")

    override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = emptyList()

    override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = emptyList()

    override fun observeLibraries(): Flow<List<Library>> = libraries

    override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = MutableStateFlow(ScanState.IDLE)

    override suspend fun removeLibrary(libraryId: LibraryId) = Unit
}
