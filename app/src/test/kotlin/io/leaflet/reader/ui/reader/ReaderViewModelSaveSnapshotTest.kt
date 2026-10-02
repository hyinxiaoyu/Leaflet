package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
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
 * R2 DEF-012, deferred-save subcase (reproduced by the 2026-10-01 review as a
 * failing JVM regression): a position write queued for document A must travel
 * with an IMMUTABLE snapshot of the address it was requested for.
 *
 * The ViewModel used to read the mutable `lastVisibleBlock` when the IO
 * coroutine finally ran, and switching documents resets that field to 0 — so
 * A's queued save wrote block 0 over A's real position. The completion
 * bookkeeping (`PositionThrottler.markSaved`) had the same problem: it marked
 * the NEW document's throttle state with a stale save's block.
 *
 * Runs the production [ReaderViewModel] with separate Main/IO test schedulers
 * and an in-memory [ReaderStore], so the queueing order is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSaveSnapshotTest {
    private class Store(
        private val blocks: Int = 8,
    ) : ReaderStore {
        val saved = mutableListOf<ReadingPosition>()
        private val body =
            (1..blocks).joinToString("\n\n") { "Paragraph $it alpha beta" }.toByteArray()

        override suspend fun openStream(documentId: DocumentId) = ByteArrayInputStream(body)

        override suspend fun describe(documentId: DocumentId) =
            DocumentDescriptor(
                fileName = "${documentId.value}.txt",
                fingerprint = "42:${body.size}",
                libraryId = LibraryId("save-snapshot"),
                titleFallback = documentId.value,
            )

        override suspend fun loadPosition(documentId: DocumentId): StoredPosition? = null

        override suspend fun resolveRelative(
            fromId: DocumentId,
            ref: String,
        ): io.leaflet.storage.ResolvedResource? = null

        override suspend fun savePosition(
            position: ReadingPosition,
            lastModified: Long,
            size: Long,
        ) {
            saved.add(position)
        }

        override suspend fun recordRecent(entry: RecentRead) = Unit

        /** Block of the LAST save for [documentId] (a document may save twice). */
        fun lastSavedBlockFor(documentId: String): Int? {
            return saved.lastOrNull { it.documentId == DocumentId(documentId) }?.blockIndex
        }
    }

    private val mainScheduler = TestCoroutineScheduler()
    private val main = StandardTestDispatcher(mainScheduler)
    private val ioScheduler = TestCoroutineScheduler()
    private val io = StandardTestDispatcher(ioScheduler)
    private lateinit var store: Store
    private lateinit var vm: ReaderViewModel
    private lateinit var owner: ViewModelStore

    /** Test time for the throttle clock; moved explicitly, never by real time. */
    private var now = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        store = Store()
        vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), io, clock = { now })
        owner = ViewModelStore().apply { put("save-snapshot", vm) }
    }

    @After
    fun tearDown() {
        owner.clear()
        mainScheduler.runCurrent()
        ioScheduler.runCurrent()
        Dispatchers.resetMain()
    }

    /** Drains Main then IO (A's load, then the queued save it dispatches). */
    private fun settle(rounds: Int = 20) {
        repeat(rounds) {
            mainScheduler.runCurrent()
            ioScheduler.runCurrent()
            mainScheduler.advanceTimeBy(16)
        }
    }

    private fun openAndAwait(documentId: String) {
        vm.open(DocumentId(documentId))
        settle()
        assertTrue(
            "$documentId must be fully loaded",
            vm.ui.value is ReaderUiState.Content,
        )
    }

    /**
     * The review's case: `jumpToBlock(5)` queued for A, then B opened before the
     * IO write starts. A must still be saved at block 5.
     */
    @Test
    fun queuedJumpSaveKeepsItsInvocationBlockAfterADocumentSwitch() {
        runTest(main) {
            openAndAwait("a")
            vm.jumpToBlock(5)
            // Deliberately do not run IO yet: A's save is queued.
            vm.open(DocumentId("b"))
            assertEquals("the switch must show Loading", ReaderUiState.Loading, vm.ui.value)
            ioScheduler.runCurrent()
            assertEquals(
                "A's queued jump save must keep the block captured when it was requested",
                5,
                store.lastSavedBlockFor("a"),
            )
        }
    }

    /**
     * The same race on the throttled scroll path (delayed scroll save), which the
     * review calls out as the subcase a flush-only capture would miss.
     */
    @Test
    fun delayedScrollSaveKeepsItsInvocationBlockAfterADocumentSwitch() {
        runTest(main) {
            openAndAwait("a")
            // Past the 2s throttle interval so the scroll save is queued.
            now += 3_000
            vm.onVisibleBlock(4)
            vm.open(DocumentId("b"))
            assertEquals("the switch must show Loading", ReaderUiState.Loading, vm.ui.value)
            ioScheduler.runCurrent()
            assertEquals(
                "A's queued scroll save must keep the block captured when it was requested",
                4,
                store.lastSavedBlockFor("a"),
            )
        }
    }

    /** Baseline: the same paths still write the right block without a switch. */
    @Test
    fun queuedSavesStillWriteTheRequestedBlockWithinOneDocument() {
        runTest(main) {
            openAndAwait("a")
            vm.jumpToBlock(3)
            settle()
            assertEquals("the jump save writes its own block", 3, store.lastSavedBlockFor("a"))

            now += 3_000
            vm.onVisibleBlock(6)
            settle()
            assertEquals("the scroll save writes its own block", 6, store.lastSavedBlockFor("a"))
        }
    }
}
