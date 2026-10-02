package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModelStore
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.RecentRead
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CR-20261001-07 (round-2 review): a finished metadata lookup must be visible.
 *
 * `rows` derived only from the entry list while the library name and file health
 * lived in plain mutable maps, so a completed lookup published nothing: the row
 * kept an unknown library and omitted its authorization-lost/missing-file hint
 * until an unrelated entry change rebuilt the rows. The review reproduced this
 * as `ConcurrencyReviewTest.completedRecentMetadataMustPublishHealthAndLibraryName`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentMetadataPublicationTest {
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

    private class Store(
        val entries: MutableStateFlow<List<RecentRead>>,
        val nameGate: CompletableDeferred<Unit>,
        val healthGate: CompletableDeferred<Unit>,
    ) : RecentStore {
        var healthChecks = 0
            private set

        override fun observeRecent(limit: Int): Flow<List<RecentRead>> = entries

        override suspend fun resolveLibraryName(libraryId: LibraryId): String? {
            nameGate.await()
            return "Actual library"
        }

        override suspend fun checkHealth(entry: RecentRead): RecentHealth {
            healthChecks++
            healthGate.await()
            return RecentHealth.AUTH_LOST
        }

        override suspend fun removeRecent(documentId: DocumentId) = Unit

        override suspend fun clearRecent() = Unit
    }

    @Test
    fun completedMetadataMustPublishHealthAndLibraryName() =
        runTest(main) {
            val entry = RecentRead(DocumentId("doc"), "Title", LibraryId("lib"), 1L)
            val store =
                Store(
                    entries = MutableStateFlow(listOf(entry)),
                    nameGate = CompletableDeferred(),
                    healthGate = CompletableDeferred(),
                )
            val vm = RecentViewModel(store)
            owner.put("recent-metadata", vm)
            scheduler.runCurrent()
            assertEquals("before the lookup the row is unknown", RecentHealth.UNKNOWN, vm.rows.value.single().health)

            store.nameGate.complete(Unit)
            store.healthGate.complete(Unit)
            scheduler.runCurrent()

            assertTrue("the health lookup must have run", store.healthChecks == 1)
            assertEquals(
                "a finished health lookup must update the published rows",
                RecentHealth.AUTH_LOST,
                vm.rows.value.single().health,
            )
            assertEquals(
                "a finished name lookup must update the published rows",
                "Actual library",
                vm.rows.value.single().libraryName,
            )
        }

    /**
     * A lookup that finishes after its entry disappeared must not republish it:
     * the completion is bound to the entry list, so rows stay empty.
     */
    @Test
    fun aLookupCompletingAfterRemovalDoesNotRepublishTheRow() =
        runTest(main) {
            val entry = RecentRead(DocumentId("doc"), "Title", LibraryId("lib"), 1L)
            val store =
                Store(
                    entries = MutableStateFlow(listOf(entry)),
                    nameGate = CompletableDeferred(),
                    healthGate = CompletableDeferred(),
                )
            val vm = RecentViewModel(store)
            owner.put("recent-removal", vm)
            scheduler.runCurrent()
            assertEquals(1, vm.rows.value.size)

            store.entries.value = emptyList()
            scheduler.runCurrent()
            assertEquals("the removed entry leaves the rows", 0, vm.rows.value.size)

            store.nameGate.complete(Unit)
            store.healthGate.complete(Unit)
            scheduler.runCurrent()
            assertEquals("a late lookup must not resurrect the row", 0, vm.rows.value.size)
        }
}
