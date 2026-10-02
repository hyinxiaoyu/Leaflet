package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.ByteArrayInputStream

/**
 * CR-20261001-04 (round-2 review): immutable save snapshots fixed *identity*, but
 * independent IO coroutines could still COMPLETE out of order. A slow write for
 * an older block could land after a newer jump and its exit flush, leaving the
 * older address stored and losing the newest position.
 *
 * Runs the production [ReaderViewModel] over a store that holds one write open,
 * so completion order is controlled explicitly. The review reproduced this as
 * `ConcurrencyReviewTest.anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSaveOrderTest {
    private class Store : ReaderStore {
        /** Held while the block-2 write is in flight. */
        val olderSaveGate = CompletableDeferred<Unit>()

        /** Flipped as soon as the older write reaches the store. */
        var olderSaveStarted = false

        /** Completed writes, in completion order. */
        val saved = mutableListOf<ReadingPosition>()

        private val body = (1..8).joinToString("\n\n") { "Paragraph $it" }.toByteArray()

        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)

        override suspend fun describe(documentId: DocumentId) =
            DocumentDescriptor(
                fileName = "a.txt",
                fingerprint = "42:${body.size}",
                libraryId = LibraryId("lib"),
                titleFallback = "a",
            )

        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null

        override suspend fun resolveRelative(
            fromId: DocumentId,
            ref: String,
        ): io.leaflet.storage.ResolvedResource? = null

        override suspend fun recordRecent(entry: RecentRead) = Unit

        override suspend fun savePosition(
            position: ReadingPosition,
            lastModified: Long,
            size: Long,
        ) {
            if (position.blockIndex == 2) {
                olderSaveStarted = true
                olderSaveGate.await()
            }
            saved.add(position)
        }

        fun storedBlocks(): List<Int> = saved.map { it.blockIndex }
    }

    private val mainScheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(mainScheduler)
    private val io = TestCoroutineScheduler()
    private lateinit var store: Store
    private lateinit var vm: ReaderViewModel
    private lateinit var owner: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        store = Store()
        vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), StandardTestDispatcher(io))
        owner = ViewModelStore().apply { put("save-order", vm) }
    }

    @After
    fun tearDown() {
        owner.clear()
        mainScheduler.runCurrent()
        io.runCurrent()
        Dispatchers.resetMain()
    }

    /**
     * The newest accepted address must survive an older write that completes
     * later: writes may not be assumed to finish in request order.
     */
    @Test
    fun anOlderSaveMustNotOverwriteTheLatestJumpAndExitFlush() =
        runTest(main) {
            vm.open(DocumentId("a"))
            repeat(20) {
                mainScheduler.runCurrent()
                io.runCurrent()
                mainScheduler.advanceTimeBy(16)
            }
            assertTrue("A must be loaded", vm.ui.value is ReaderUiState.Content)

            // Block 2's write enters the store and stays there.
            vm.jumpToBlock(2)
            io.runCurrent()
            assertTrue("the older write must be in flight", store.olderSaveStarted)

            // A newer jump plus the exit flush complete while it is still held.
            vm.jumpToBlock(5)
            io.runCurrent()
            val flush = vm.flushPosition()
            io.runCurrent()
            assertTrue("the latest exit flush must complete", flush.isCompleted)
            assertEquals("the newest write is stored first", 5, store.storedBlocks().last())

            // Release the older write: it must not become the stored address.
            store.olderSaveGate.complete(Unit)
            io.runCurrent()
            assertEquals(
                "the newest address must survive the older completion (writes=${store.storedBlocks()})",
                5,
                store.storedBlocks().last(),
            )
        }

    /**
     * The repair must target the SAME document. A document switch between two
     * overlapping writes of A (and a later write of B) must not redirect A's
     * repair at B's request: A's newest accepted address is the one that has to
     * survive, even though the ViewModel has moved on to B.
     */
    @Test
    fun aDocumentSwitchMustNotRedirectTheRepairToTheOtherDocument() =
        runTest(main) {
            fun settle() {
                repeat(20) {
                    mainScheduler.runCurrent()
                    io.runCurrent()
                    mainScheduler.advanceTimeBy(16)
                }
            }

            fun lastBlockFor(documentId: String): Int? = store.saved.lastOrNull { it.documentId == DocumentId(documentId) }?.blockIndex

            vm.open(DocumentId("a"))
            settle()
            assertTrue("A must be loaded", vm.ui.value is ReaderUiState.Content)

            // A's older write is held inside the store...
            vm.jumpToBlock(2)
            io.runCurrent()
            assertTrue("the older A write must be in flight", store.olderSaveStarted)

            // ...while a newer A address lands, the user opens B, and B saves.
            vm.jumpToBlock(5)
            io.runCurrent()
            vm.open(DocumentId("b"))
            settle()
            vm.jumpToBlock(3)
            io.runCurrent()
            assertEquals("B's own save is stored", 3, lastBlockFor("b"))

            // A's held write completes last: A keeps its newest address, and the
            // repair is not spent on B's request.
            store.olderSaveGate.complete(Unit)
            io.runCurrent()
            val aWrites = store.saved.filter { it.documentId == DocumentId("a") }.map { it.blockIndex }
            assertEquals("A's newest address must be repaired, not B's (A writes=$aWrites)", 5, lastBlockFor("a"))
            assertEquals("B's stored address is untouched", 3, lastBlockFor("b"))
        }
}
