package io.leaflet.reader.ui.library

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModelStore
import io.leaflet.model.Document
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executes the real compiler-generated UI callback, without composing Android views. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompiledBreadcrumbCallbackProbe {
    private class RenderState(override var value: LibraryBrowseUiState) : State<LibraryBrowseUiState>

    @Test
    fun aQueuedRenderedRootTapKeepsItsOriginalLibrary() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val owner = ViewModelStore()
        try {
            val vm = LibraryViewModel(Source(), null)
            owner.put("callback", vm)
            vm.openLibrary(LibraryId("A"))
            vm.enterDirectory("Docs")
            vm.enterDirectory("Notes")
            val origin = vm.browse.value
            val rendered = RenderState(origin)
            val callback = Class.forName("io.leaflet.reader.ui.library.LibraryScreensKt").declaredMethods.single {
                it.name.startsWith("LibraryBrowseScreen") &&
                    it.parameterTypes.toList() in listOf(
                        listOf(LibraryViewModel::class.java, State::class.java, String::class.java),
                        listOf(LibraryViewModel::class.java, String::class.java, String::class.java, String::class.java),
                    )
            }
            callback.isAccessible = true

            vm.openLibrary(LibraryId("B"))
            vm.enterDirectory("Docs")
            vm.enterDirectory("Notes")
            val before = vm.browse.value
            rendered.value = before
            if (callback.parameterTypes[1] == State::class.java) {
                callback.invoke(null, vm, rendered, "")
            } else {
                callback.invoke(null, vm, origin.libraryId, origin.prefix, "")
            }

            assertEquals("the actual old UI callback must retain A's origin instead of reading live B state", before, vm.browse.value)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
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
