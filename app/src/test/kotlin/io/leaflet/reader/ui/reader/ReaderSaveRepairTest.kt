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
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Third independent-acceptance counterexample for CR-20261001-04:
 * `RepairAndProjectionAcceptanceTest.aCompensatingWriteMustNotOverwriteAnEvenNewerJumpAndFlush`.
 *
 * A compensating (repair) write is itself an ordinary store write: it can be held
 * in the store while an even newer jump and exit flush complete, so it must obey
 * the same latest-request rule as any other write. Under the rejected revision it
 * landed last and left the older address stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSaveRepairTest {
    private class Store : ReaderStore {
        /** Holds block 2's original write. */
        val olderGate = CompletableDeferred<Unit>()

        /** Holds the SECOND block-5 write (the repair of block 5). */
        val repairGate = CompletableDeferred<Unit>()

        var repairStarted = false
        private var block5Calls = 0
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
            if (position.blockIndex == 2) olderGate.await()
            if (position.blockIndex == 5 && ++block5Calls == 2) {
                repairStarted = true
                repairGate.await()
            }
            writes.add(position)
        }

        fun storedBlocks(): List<Int> = writes.map { it.blockIndex }
    }

    @Test
    fun aCompensatingWriteMustNotOverwriteAnEvenNewerJumpAndFlush() {
        val mainScheduler = TestCoroutineScheduler()
        val ioScheduler = TestCoroutineScheduler()
        val owner = ViewModelStore()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        try {
            val store = Store()
            val vm = ReaderViewModel(store, LeafletDocumentParser.createForJvmTests(), StandardTestDispatcher(ioScheduler))
            owner.put("save-repair", vm)
            vm.open(DocumentId("a"))
            repeat(20) {
                mainScheduler.runCurrent()
                ioScheduler.runCurrent()
                mainScheduler.advanceTimeBy(16)
            }
            assertTrue("A must be loaded", vm.ui.value is ReaderUiState.Content)

            vm.jumpToBlock(2)
            ioScheduler.runCurrent()
            vm.jumpToBlock(5)
            ioScheduler.runCurrent()

            // Block 2 lands and triggers the block-5 repair, which is held.
            store.olderGate.complete(Unit)
            ioScheduler.runCurrent()
            assertTrue("the repair of block 5 is held in the store", store.repairStarted)

            // An even newer jump and its exit flush complete meanwhile.
            vm.jumpToBlock(6)
            ioScheduler.runCurrent()
            val flush = vm.flushPosition()
            ioScheduler.runCurrent()
            assertTrue("the newest exit flush must complete", flush.isCompleted)
            assertEquals("the newest write is stored first", 6, store.storedBlocks().last())

            // Releasing the held repair must not leave the older address stored.
            store.repairGate.complete(Unit)
            ioScheduler.runCurrent()
            assertEquals(
                "a repair must obey the same latest-request rule (writes=${store.storedBlocks()})",
                6,
                store.storedBlocks().last(),
            )
        } finally {
            owner.clear()
            mainScheduler.runCurrent()
            ioScheduler.runCurrent()
            Dispatchers.resetMain()
        }
    }
}
