package io.leaflet.reader.ui.library

import androidx.lifecycle.ViewModelStore
import io.leaflet.model.Document
import io.leaflet.model.DocumentId
import io.leaflet.model.Library
import io.leaflet.model.LibraryId
import io.leaflet.storage.ScanState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * CR-20261001-06 (round-2 review): an earlier Library load must not replace a
 * later selection.
 *
 * `openLibrary` started untracked jobs and published unconditionally, so a slow
 * load for A could overwrite the browse state (rows, path state, progress
 * subscription) after the user had already opened B. The review reproduced this
 * as `ConcurrencyReviewTest.aSlowEarlierLibraryOpenMustNotReplaceTheCurrentLibrary`.
 *
 * Runs the production [LibraryViewModel] with a delayed [LibraryListSource]; no
 * Compose, Room, or device run is claimed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowseSelectionTest {
    private val scheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(scheduler)
    private lateinit var owner: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        owner = ViewModelStore()
    }

    @After
    fun tearDown() {
        owner.clear()
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    /** Source whose document snapshot for [slowLibrary] waits for [gate]. */
    private class Source(
        private val slowLibrary: String,
        val gate: CompletableDeferred<Unit>,
    ) : LibraryListSource {
        var documentSnapshots = 0
            private set

        override suspend fun addLibrary(
            displayName: String,
            rootTreeUri: String,
        ): Library = Library(LibraryId("unused"), displayName, rootTreeUri)

        override fun observeLibraries(): Flow<List<Library>> = flowOf(emptyList())

        override fun observeScanState(libraryId: LibraryId): Flow<ScanState> = flowOf(ScanState.IDLE)

        override suspend fun observeDirectoriesSnapshot(libraryId: LibraryId): List<String> = emptyList()

        override suspend fun observeDocumentsSnapshot(libraryId: LibraryId): List<Document> {
            documentSnapshots++
            if (libraryId.value == slowLibrary) gate.await()
            return listOf(
                Document(
                    DocumentId("${libraryId.value}-doc"),
                    libraryId,
                    "read.txt",
                    "read.txt",
                    "content://doc/${libraryId.value}",
                    "txt",
                ),
            )
        }

        override suspend fun removeLibrary(libraryId: LibraryId) = Unit
    }

    @Test
    fun aSlowEarlierLibraryOpenMustNotReplaceTheCurrentLibrary() =
        runTest(main) {
            val source = Source(slowLibrary = "a", gate = CompletableDeferred())
            val vm = LibraryViewModel(source, null)
            owner.put("library-selection", vm)

            vm.openLibrary(LibraryId("a"))
            scheduler.runCurrent()
            vm.openLibrary(LibraryId("b"))
            scheduler.runCurrent()
            assertEquals("the later selection is current immediately", "b", vm.browse.value.libraryId)

            // Release the slow earlier load: it must not take the screen back.
            source.gate.complete(Unit)
            scheduler.runCurrent()
            assertEquals("the earlier Library must not replace the current one", "b", vm.browse.value.libraryId)
            assertEquals(
                "the current Library's rows must be the ones published",
                listOf("b-doc"),
                vm.browse.value.documents.map { it.id.value },
            )
        }

    /** Re-opening the same Library still publishes (cancellation is per selection). */
    @Test
    fun reOpeningTheSameLibraryStillPublishesItsRows() =
        runTest(main) {
            val source = Source(slowLibrary = "never", gate = CompletableDeferred())
            val vm = LibraryViewModel(source, null)
            owner.put("library-reopen", vm)

            vm.openLibrary(LibraryId("a"))
            scheduler.runCurrent()
            assertEquals("a", vm.browse.value.libraryId)
            vm.openLibrary(LibraryId("a"))
            scheduler.runCurrent()

            assertEquals("a", vm.browse.value.libraryId)
            assertEquals(listOf("a-doc"), vm.browse.value.documents.map { it.id.value })
        }
}
