package io.leaflet.reader.ui.library

import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRemovalTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun confirmRemoveDeletesOnlyThePendingLibrary() {
        val source = FakeLibraryListSource()
        val notes = Library(LibraryId("notes"), "Notes", "content://notes")
        val work = Library(LibraryId("work"), "Work", "content://work")
        source.libraries.value = listOf(notes, work)
        val viewModel = LibraryViewModel(source, scanner = null)

        viewModel.requestRemove(notes.id)
        assertEquals("notes", viewModel.list.value.pendingRemoval?.id)
        viewModel.confirmRemove()

        assertEquals(listOf("notes"), source.removed)
        assertNull(viewModel.list.value.pendingRemoval)
    }

    @Test
    fun dismissRemoveLeavesTheLibraryInPlace() {
        val source = FakeLibraryListSource()
        val notes = Library(LibraryId("notes"), "Notes", "content://notes")
        source.libraries.value = listOf(notes)
        val viewModel = LibraryViewModel(source, scanner = null)

        viewModel.requestRemove(notes.id)
        viewModel.dismissRemove()

        assertTrue(source.removed.isEmpty())
        assertNull(viewModel.list.value.pendingRemoval)
        assertEquals(listOf(notes), viewModel.list.value.libraries)
    }

    @Test
    fun leaveDirectoryStopsAtTheLibraryRoot() {
        val viewModel = LibraryViewModel(FakeLibraryListSource(), scanner = null)

        viewModel.enterDirectory("notes")
        viewModel.enterDirectory("drafts")
        assertEquals("notes/drafts", viewModel.browse.value.prefix)
        assertTrue(viewModel.leaveDirectory())
        assertEquals("notes", viewModel.browse.value.prefix)
        assertTrue(viewModel.leaveDirectory())
        assertEquals("", viewModel.browse.value.prefix)
        assertFalse(viewModel.leaveDirectory())
    }

    @Test
    fun openLibraryLoadsIndexedDirectoriesIntoBrowseState() {
        val source = FakeLibraryListSource()
        source.directories = listOf("Empty", "notes/drafts")
        val viewModel = LibraryViewModel(source, scanner = null)

        viewModel.openLibrary(LibraryId("notes"))

        assertEquals("notes", viewModel.browse.value.libraryId)
        assertEquals(listOf("Empty", "notes/drafts"), viewModel.browse.value.directories)
        assertEquals("", viewModel.browse.value.prefix)
    }
}

private class FakeLibraryListSource : LibraryListSource {
    val libraries = MutableStateFlow<List<Library>>(emptyList())
    val removed = mutableListOf<String>()
    var directories: List<String> = emptyList()

    override suspend fun addLibrary(
        displayName: String,
        rootTreeUri: String,
    ): Library = error("unused")

    override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> = emptyList()

    override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = directories

    override fun observeLibraries(): Flow<List<Library>> = libraries

    override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = MutableStateFlow(ScanState.IDLE)

    override suspend fun removeLibrary(libraryId: LibraryId) {
        removed += libraryId.value
        libraries.value = libraries.value.filter { it.id != libraryId }
    }
}
