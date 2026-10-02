package io.leaflet.reader.ui.reader

import androidx.lifecycle.ViewModelStore
import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.model.DocumentId
import io.leaflet.model.LibraryId
import io.leaflet.model.ReadingPosition
import io.leaflet.model.RecentRead
import io.leaflet.reader.DocumentDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.coroutines.CoroutineContext

/**
 * Independent-acceptance counterexample for CR-20261001-04
 * (`AcceptanceBoundariesTest.newestRequestedPositionMustWinWhenIOJobsStartInReverseOrder`).
 *
 * The request sequence must be allocated at INVOCATION time, on the caller's
 * thread — not when the IO coroutine starts. This harness holds IO dispatch,
 * invokes block 2 → block 5 → the exit flush, then releases the queued work
 * newest-first, so IO start order is the reverse of invocation order. Assigning
 * the sequence inside the worker promoted the earliest invocation (block 2) to
 * "newest" and left block 2 stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSaveOrderDispatchTest {
    /** Holds dispatched blocks and can run them newest-first. */
    private class HeldDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var hold = false
        private val pending = mutableListOf<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            if (hold) pending.add(block) else delegate.dispatch(context, block)
        }

        /** Runs held work in reverse dispatch order (the counterexample). */
        fun releaseNewestFirst() {
            while (pending.isNotEmpty()) pending.removeAt(pending.lastIndex).run()
        }
    }

    private class Store : ReaderStore {
        val writes = mutableListOf<ReadingPosition>()
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
            writes.add(position)
        }

        fun storedBlocks(): List<Int> = writes.map { it.blockIndex }
    }

    @Test
    fun newestRequestedPositionMustWinWhenIOJobsStartInReverseOrder() {
        val mainScheduler = TestCoroutineScheduler()
        val ioScheduler = TestCoroutineScheduler()
        val dispatcher = HeldDispatcher(StandardTestDispatcher(ioScheduler))
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        try {
            val store = Store()
            val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), dispatcher)
            owner.put("save-order-dispatch", vm)
            vm.open(DocumentId("a"))
            repeat(20) {
                mainScheduler.runCurrent()
                ioScheduler.runCurrent()
                mainScheduler.advanceTimeBy(16)
            }
            assertTrue("A must be loaded", vm.ui.value is ReaderUiState.Content)

            dispatcher.hold = true
            vm.jumpToBlock(2)
            vm.jumpToBlock(5)
            val flush = vm.flushPosition()
            assertTrue("IO writes are still held", store.writes.isEmpty())

            dispatcher.releaseNewestFirst()
            assertTrue("the exit flush must complete", flush.isCompleted)
            assertEquals(
                "invocation order must win, not IO-start order (writes=${store.storedBlocks()})",
                5,
                store.storedBlocks().last(),
            )
        } finally {
            owner.clear()
            dispatcher.releaseNewestFirst()
            mainScheduler.runCurrent()
            ioScheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }
}
